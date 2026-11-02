package com.galaxyrio.gracelauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.AtomicFile
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Disposable rendered rows for first paint, followed by live catalog reconciliation. Read only
 * after settings/items load: this file never determines hidden apps, favorites or folder contents.
 * User serials are device-local identities, so the file is deliberately excluded from backup.
 */
internal class LauncherAppCache(context: Context) {
    private val context = context.applicationContext
    private val file = AtomicFile(File(this.context.noBackupFilesDir, "launcher-apps.json"))
    private val profiles = LauncherProfiles(this.context)

    /**
     * Decode home entries first, without querying installed activities. The callback runs on IO;
     * consumers publish each cumulative preview on their own dispatcher. A corrupt row does not
     * discard the remaining snapshot. Profile rows wait for fresh visibility/type validation.
     */
    suspend fun load(
        key: String,
        priorityKeys: Set<String> = emptySet(),
        entryKeys: Map<String, String> = emptyMap(),
        onPreview: suspend (List<LauncherApp>) -> Unit = {},
    ): List<LauncherApp>? = withContext(Dispatchers.IO) {
        val bytes = mutex.withLock {
            bestEffort<ByteArray?>(null) { if (file.baseFile.exists()) file.readFully() else null }
        } ?: return@withContext null
        val root = bestEffort<JSONObject?>(null) { JSONObject(bytes.toString(Charsets.UTF_8)) }
            ?: return@withContext null
        if (root.optInt("version") !in 3..VERSION || root.optString("key") != key) return@withContext null
        val encoded = root.optJSONArray("apps") ?: return@withContext null
        val coroutine = currentCoroutineContext()
        val entries = buildList {
            for (index in 0 until encoded.length()) {
                coroutine.ensureActive()
                val entry = bestEffort<CachedEntry?>(null) {
                    cachedEntry(encoded.getJSONObject(index))
                } ?: continue
                if (entry.json.optString("entryKey") == entryKeys[entry.key].orEmpty()) add(entry)
            }
        }.distinctBy(CachedEntry::key)
        val decoded = linkedMapOf<String, LauncherApp>()
        val attempted = mutableSetOf<String>()
        fun decodeRows(rows: List<CachedEntry>, allowed: Map<Long, LauncherProfile> = emptyMap()) {
            rows.forEach { entry ->
                coroutine.ensureActive()
                if (!attempted.add(entry.key)) return@forEach
                val profile = entry.serial?.let { serial ->
                    allowed[serial]?.takeIf { it.type == entry.type } ?: return@forEach
                }
                bestEffort<LauncherApp?>(null) { decode(entry.json, profile) }
                    ?.takeIf { it.key == entry.key }?.let { decoded[it.key] = it }
            }
        }
        suspend fun publish() {
            if (decoded.isNotEmpty()) onPreview(decoded.values.toList())
        }

        val personal = entries.filter { it.serial == null }
        val home = personal.filter { it.key in priorityKeys }
        // An existing snapshot without saved favorites can still paint a small initial batch.
        decodeRows(home.ifEmpty { personal.take(INITIAL_BATCH_SIZE) })
        publish()

        val foreign = entries.filter { it.serial != null }
        val allowed = if (foreign.isEmpty()) emptyMap() else visibleProfiles()
        val foreignHome = foreign.filter { it.key in priorityKeys }
        val previousSize = decoded.size
        decodeRows(foreignHome, allowed)
        if (decoded.size != previousSize) publish()

        decodeRows(personal)
        decodeRows(foreign, allowed)
        decoded.values.toList()
    }

    /** Apply the same profile boundary to a process-memory preview as to the on-disk snapshot. */
    suspend fun validatePublicApps(apps: List<LauncherApp>): List<LauncherApp> = withContext(Dispatchers.IO) {
        val candidates = apps.filter(::cacheable)
        val allowed = if (candidates.any { it.userSerial != null }) visibleProfiles() else emptyMap()
        candidates.mapNotNull { app ->
            val serial = app.userSerial ?: return@mapNotNull app
            val profile = allowed[serial]?.takeIf { it.type == profileType(app) } ?: return@mapNotNull null
            app.copy(user = profile.user, shortcut = app.shortcut?.copy(user = profile.user, userSerial = serial))
        }
    }

    suspend fun save(
        key: String,
        apps: List<LauncherApp>,
        entryKeys: Map<String, String> = emptyMap(),
    ) {
        val request = saveSequence.incrementAndGet()
        withContext(Dispatchers.IO) {
            bestEffort(Unit) {
                val coroutine = currentCoroutineContext()
                val entries = JSONArray()
                apps.filter(::cacheable).distinctBy(LauncherApp::key).forEach { app ->
                    coroutine.ensureActive()
                    bestEffort<JSONObject?>(null) { encode(app).put("entryKey", entryKeys[app.key].orEmpty()) }
                        ?.let(entries::put)
                }
                val root = JSONObject().put("version", VERSION).put("key", key).put("apps", entries)
                val bytes = root.toString().toByteArray(Charsets.UTF_8)
                // Encoding never holds the file lock; an older Activity cannot overwrite
                // a newer snapshot merely because its PNG encoding finished later.
                mutex.withLock {
                    coroutine.ensureActive()
                    if (request != saveSequence.get()) return@withLock
                    val stream = file.startWrite()
                    try {
                        stream.write(bytes)
                        coroutine.ensureActive()
                        file.finishWrite(stream)
                    } catch (error: Exception) {
                        file.failWrite(stream)
                        throw error
                    }
                }
            }
        }
    }

    private fun cacheable(app: LauncherApp): Boolean {
        if (app.isPrivateSpace || app.shortcut?.isPrivateSpace == true || app.folderId == PrivateSpaceFolderId) return false
        if (app.isWorkProfile && app.isCloneProfile) return false
        if (app.userSerial == null) return app.user == null && !app.isWorkProfile && !app.isCloneProfile &&
            app.shortcut?.user == null && app.shortcut?.userSerial == null
        return app.userSerial >= 0 && app.user != null && app.folderId == null &&
            (app.shortcut == null || (app.shortcut.userSerial == app.userSerial && app.shortcut.user == app.user))
    }

    private fun profileType(app: LauncherApp) = when {
        app.userSerial == null -> LauncherProfileType.Personal
        app.isWorkProfile -> LauncherProfileType.Work
        app.isCloneProfile -> LauncherProfileType.Clone
        else -> LauncherProfileType.Other
    }

    /** Metadata only: activities are checked later by the live model, after the preview is visible. */
    private fun visibleProfiles(): Map<Long, LauncherProfile> = bestEffort(emptyMap()) {
        val launcher = context.getSystemService(LauncherApps::class.java)
        profiles.snapshot().profiles.filter { profile ->
            if (profile.user == Process.myUserHandle() || profile.serial < 0 || !profile.isPublic ||
                profile.quietMode != false || profile.unlocked != true) return@filter false
            if (Build.VERSION.SDK_INT < 35) return@filter true
            // A remembered public type alone cannot prove that a profile is still accessible.
            // In particular, unknown Android 15 profiles must never expose Private Space rows.
            val info = runCatching { launcher?.getLauncherUserInfo(profile.user) }.getOrNull()
                ?: return@filter false
            val type = when (info.userType?.takeIf(String::isNotBlank) ?: return@filter false) {
                UserManager.USER_TYPE_PROFILE_MANAGED -> LauncherProfileType.Work
                UserManager.USER_TYPE_PROFILE_CLONE -> LauncherProfileType.Clone
                UserManager.USER_TYPE_PROFILE_PRIVATE -> LauncherProfileType.Private
                else -> LauncherProfileType.Other
            }
            type == profile.type && info.userSerialNumber.toLong() == profile.serial
        }.associateBy(LauncherProfile::serial)
    }

    private data class CachedEntry(
        val json: JSONObject,
        val key: String,
        val serial: Long?,
        val type: LauncherProfileType,
    )

    private fun cachedEntry(entry: JSONObject): CachedEntry {
        val serial = if (entry.has("userSerial")) entry.getLong("userSerial").also { require(it >= 0) } else null
        if (serial == null) require(entry.optString("profileType") in setOf("", LauncherProfileType.Personal.name))
        val type = if (serial == null) LauncherProfileType.Personal else
            LauncherProfileType.valueOf(entry.getString("profileType")).also {
                require(it in setOf(LauncherProfileType.Work, LauncherProfileType.Clone, LauncherProfileType.Other))
            }
        val component = requireNotNull(ComponentName.unflattenFromString(entry.getString("component")))
        val shortcut = entry.optJSONObject("shortcut")
        val folder = entry.optString("folder").takeIf(String::isNotEmpty)
        require(folder != PrivateSpaceFolderId && (serial == null || folder == null))
        val itemKey = folder?.let { "folder:$it" } ?: shortcut?.let {
            (serial?.let { serial -> "profile:$serial:" } ?: "") +
                "shortcut:${it.getString("package")}/${Uri.encode(it.getString("id"))}"
        } ?: serial?.let { "profile:$it:${component.flattenToString()}" } ?: component.flattenToString()
        return CachedEntry(entry, itemKey, serial, type)
    }

    private fun encode(app: LauncherApp) = JSONObject().apply {
        put("component", app.componentName.flattenToString())
        put("label", app.label); put("originalLabel", app.originalLabel)
        put("icon", encodeIcon(app.icon)); put("monochrome", encodeIcon(app.monochromeIcon))
        put("scale", app.monochromeScale); put("pack", app.iconPackPackage); put("themePack", app.themeIconPackPackage)
        put("system", app.isSystemApp); put("adaptive", app.isAdaptiveIcon)
        put("installedAt", app.firstInstallTime); put("settings", app.isLauncherSettings)
        put("originalColor", app.originalIconColor); put("englishLabel", app.englishLabel)
        put("folder", app.folderId); put("stack", app.isAppStack)
        put("userSerial", app.userSerial); put("profileType", profileType(app).name)
        put("showWorkIndicator", app.showWorkIndicator)
        app.iconDisplay?.let { put("display", it.json()) }
        app.shortcut?.let { shortcut -> put("shortcut", JSONObject().apply {
            put("id", shortcut.id); put("package", shortcut.packageName); put("label", shortcut.label)
            put("activity", shortcut.activity?.flattenToString()); put("icon", encodeIcon(shortcut.icon))
        }) }
    }

    private fun decode(entry: JSONObject, profile: LauncherProfile?): LauncherApp {
        fun optional(name: String) = entry.optString(name).takeIf(String::isNotEmpty)
        val component = requireNotNull(ComponentName.unflattenFromString(entry.getString("component")))
        val shortcut = entry.optJSONObject("shortcut")?.let {
            LauncherShortcut(it.getString("id"), it.getString("package"), it.getString("label"),
                decodeIcon(it.optString("icon")), it.optString("activity").takeIf(String::isNotEmpty)
                    ?.let(ComponentName::unflattenFromString), user = profile?.user, userSerial = profile?.serial)
        }
        return LauncherApp(component, entry.getString("label"), decodeIcon(entry.optString("icon")),
            monochromeIcon = decodeIcon(entry.optString("monochrome")), originalLabel = entry.getString("originalLabel"),
            monochromeScale = entry.getDouble("scale").toFloat(), iconPackPackage = optional("pack"),
            themeIconPackPackage = optional("themePack"), shortcut = shortcut,
            isSystemApp = entry.getBoolean("system"), isAdaptiveIcon = entry.getBoolean("adaptive"),
            firstInstallTime = entry.getLong("installedAt"), isLauncherSettings = entry.getBoolean("settings"),
            originalIconColor = if (entry.has("originalColor")) entry.getInt("originalColor") else null,
            englishLabel = optional("englishLabel"), folderId = optional("folder"), isAppStack = entry.getBoolean("stack"),
            iconDisplay = entry.optJSONObject("display")?.let { IconDisplaySettings.decode(it.toString()) },
            user = profile?.user, userSerial = profile?.serial,
            isWorkProfile = profile?.type == LauncherProfileType.Work,
            isCloneProfile = profile?.type == LauncherProfileType.Clone,
            showWorkIndicator = entry.optBoolean("showWorkIndicator", true))
    }

    private fun encodeIcon(icon: ImageBitmap?): String = icon?.let {
        ByteArrayOutputStream().use { output ->
            check(it.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }.orEmpty()

    private fun decodeIcon(encoded: String): ImageBitmap? = if (encoded.isEmpty()) null else {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).asImageBitmap()
    }

    private inline fun <T> bestEffort(fallback: T, action: () -> T): T = try { action() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) {
        Log.w("LauncherAppCache", "Unable to use startup preview; live apps will still load", error)
        fallback
    }

    companion object {
        private const val VERSION = 4
        private const val INITIAL_BATCH_SIZE = 12
        private val mutex = Mutex()
        private val saveSequence = AtomicLong()
        fun key(inputs: String): String = MessageDigest.getInstance("SHA-256")
            .digest(inputs.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
