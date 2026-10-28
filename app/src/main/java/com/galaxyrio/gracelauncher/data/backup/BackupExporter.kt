package com.galaxyrio.gracelauncher.data.backup

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserManager
import com.galaxyrio.gracelauncher.data.FavoritesStore
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.PopupItem
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal const val AppearanceFile = "launcher_appearance"
internal val PreferenceSections = mapOf(BackupSection.Favorites to FavoritesStore.FILE_NAME, BackupSection.Appearance to AppearanceFile)
/** Keys the app reads with a typed getter; a stored value of another type would throw on every start. */
internal val PreferenceTypes: Map<String, Map<String, Class<*>>> = mapOf(
    FavoritesStore.FILE_NAME to mapOf(FavoritesStore.KEY_ORDER to String::class.java, FavoritesStore.KEY_FAVORITES to Set::class.java),
    AppearanceFile to mapOf("text_mode" to String::class.java, "themed_icons" to Boolean::class.javaObjectType),
)
internal val FileSections =mapOf(BackupSection.Fonts to ("clock_fonts" to BackupArchive.FONTS_DIR),
    BackupSection.Icons to ("item_icons" to BackupArchive.ICONS_DIR))

class BackupExporter(
    context: Context,
    private val database: LauncherDatabase = LauncherDatabase.getInstance(context),
    private val databaseFile: File = context.getDatabasePath("grace_launcher.db"),
) {
    private val context = context.applicationContext

    suspend fun export(output: OutputStream, labels: Map<String, String>): BackupManifest =
        BackupLock.withLock { write(output, labels) }

    suspend fun contentHash(): String = BackupLock.withLock {
        withContext(Dispatchers.IO) {
            checkpoint()
            val digest = MessageDigest.getInstance("SHA-256")
            databaseFile.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            digest.update(preferencesJson().toString().toByteArray())
            FileSections.values.forEach { (dir, entry) ->
                files(File(context.filesDir, dir)).forEach { (name, file) -> digest.update("$entry$name:${file.length()}\n".toByteArray()) }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /** Caller holds [BackupLock]; the importer uses this for its safety export. */
    internal suspend fun write(output: OutputStream, labels: Map<String, String>): BackupManifest = withContext(Dispatchers.IO) {
        checkpoint()
        val dao = database.backupDao()
        val preferences = preferencesJson()
        val popups = dao.allPopups().associate { it.ownerKey to PopupItem.decode(it.itemsJson) }
        val favorites = favoritesOf(decodePreferences(preferences.getJSONObject(FavoritesStore.FILE_NAME)))
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val manifest = BackupManifest(BACKUP_FORMAT_VERSION, info.versionName.orEmpty(), info.longVersionCode,
            LauncherDatabase.SCHEMA_VERSION, System.currentTimeMillis(), BackupSection.entries.toSet(),
            countsOf(dao.allFolders().size, popups, favorites), deviceProfiles(context))
        val zip = ZipOutputStream(output)
        fun entry(name: String, write: (OutputStream) -> Unit) { zip.putNextEntry(ZipEntry(name)); write(zip); zip.closeEntry() }
        entry(BackupArchive.DATABASE) { out -> databaseFile.inputStream().use { it.copyTo(out) } }
        entry(BackupArchive.PREFERENCES) { it.write(preferences.toString().toByteArray()) }
        entry(BackupArchive.LABELS) { it.write(JSONObject(labels.toSortedMap()).toString().toByteArray()) }
        FileSections.values.forEach { (dir, prefix) ->
            files(File(context.filesDir, dir)).forEach { (name, file) -> entry(prefix + name) { out -> file.inputStream().use { it.copyTo(out) } } }
        }
        // Last, so a file cut off at any entry boundary has no manifest and is refused instead of wiping later sections.
        entry(BackupArchive.MANIFEST) { it.write(manifest.encode().toByteArray()) }
        zip.finish()
        output.flush()
        manifest
    }

    /** Moves committed WAL pages into the main file so a plain copy of it is complete. */
    private suspend fun checkpoint() {
        repeat(5) {
            val busy = database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() && it.getInt(0) != 0 }
            if (!busy) return
            delay(100)
        }
        error("The launcher database stayed busy")
    }

    private fun preferencesJson() = JSONObject().apply {
        PreferenceSections.values.forEach { name -> put(name, encodePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE).all)) }
    }
}

private fun files(dir: File): List<Pair<String, File>> = dir.walkTopDown().filter { it.isFile }
    .map { it.relativeTo(dir).invariantSeparatorsPath to it }.sortedBy { it.first }.toList()

internal fun encodePreferences(values: Map<String, *>): JSONObject = JSONObject().apply {
    values.toSortedMap().forEach { (key, value) ->
        val (type, encoded) = when (value) {
            is String -> "string" to value
            is Boolean -> "boolean" to value
            is Int -> "int" to value
            is Long -> "long" to value
            is Float -> "float" to value.toDouble()
            is Set<*> -> "stringSet" to JSONArray(value.filterIsInstance<String>().sorted())
            else -> return@forEach
        }
        put(key, JSONObject().put("type", type).put("value", encoded))
    }
}

/** Throws on any entry it cannot restore with its original type. */
internal fun decodePreferences(json: JSONObject): Map<String, Any> = json.keys().asSequence().associateWith { key ->
    val entry = json.getJSONObject(key)
    when (val type = entry.getString("type")) {
        "string" -> entry.getString("value")
        "boolean" -> entry.getBoolean("value")
        "int" -> entry.getInt("value")
        "long" -> entry.getLong("value")
        "float" -> entry.getDouble("value").toFloat()
        "stringSet" -> entry.getJSONArray("value").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        else -> throw IllegalArgumentException("Unknown preference type $type")
    }
}

internal fun favoritesOf(preferences: Map<String, Any>): List<String> =
    (preferences[FavoritesStore.KEY_ORDER] as? String)?.let { encoded ->
        runCatching { JSONArray(encoded).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
    } ?: (preferences[FavoritesStore.KEY_FAVORITES] as? Set<*>)?.filterIsInstance<String>()?.sorted().orEmpty()

internal fun countsOf(folders: Int, popups: Map<String, List<PopupItem>>, favorites: List<String>) = BackupCounts(
    folders = folders,
    apps = popups.filterKeys { it.startsWith("folder:") }.values.sumOf { items -> items.count { it.widget == null && isAppKey(it.key) } },
    favorites = favorites.size,
    widgets = popups.values.sumOf { items -> items.count { it.widget != null } },
)

internal fun deviceProfiles(context: Context): List<BackupProfile> = runCatching {
    val launcher = context.getSystemService(LauncherApps::class.java)
    val users = context.getSystemService(UserManager::class.java)
    launcher.profiles.filter { it != Process.myUserHandle() }.mapNotNull { user ->
        val type = if (Build.VERSION.SDK_INT < 35) "work" else when (launcher.getLauncherUserInfo(user)?.userType) {
            UserManager.USER_TYPE_PROFILE_MANAGED -> "work"
            UserManager.USER_TYPE_PROFILE_PRIVATE -> "private"
            else -> null
        }
        val serial = users.getSerialNumberForUser(user)
        if (type == null || serial < 0) null else BackupProfile(serial, type)
    }
}.getOrDefault(emptyList())
