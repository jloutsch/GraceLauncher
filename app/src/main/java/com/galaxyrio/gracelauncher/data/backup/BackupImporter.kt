package com.galaxyrio.gracelauncher.data.backup

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import com.galaxyrio.gracelauncher.data.AppPopupEntity
import com.galaxyrio.gracelauncher.data.FavoritesStore
import com.galaxyrio.gracelauncher.data.HiddenAppEntity
import com.galaxyrio.gracelauncher.data.HomeContentKey
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.ItemIconEntity
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolderEntity
import com.galaxyrio.gracelauncher.data.LauncherSettingsEntity
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.data.SavedShortcutEntity
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

sealed interface ImportResult {
    data class Success(val summary: ImportSummary) : ImportResult
    data class Rejected(val problem: BackupProblem) : ImportResult
    data class Failed(val error: Throwable, val restored: Boolean) : ImportResult
}

data class ImportSummary(
    val folders: Int,
    val apps: Int,
    val favorites: Int,
    val widgets: Int,
    val settingsRestored: Boolean,
    val notInstalled: List<String>,
    val widgetsMissingProvider: List<String>,
    val leftOut: List<String> = emptyList(),
)

private const val SafetyFile = "backup-safety.zip"
// No user has a negative serial, so keys moved here match no profile on this phone instead of a wrong one.
private const val NoProfile = -1L
private val DatabaseSections = setOf(BackupSection.Settings, BackupSection.HiddenApps, BackupSection.Folders,
    BackupSection.ItemIcons, BackupSection.SavedShortcuts, BackupSection.Popups)

private class Rejection(val problem: BackupProblem) : Exception()

private data class BackupContents(
    val manifest: BackupManifest,
    val settings: List<LauncherSettingsEntity> = emptyList(),
    val hiddenApps: List<HiddenAppEntity> = emptyList(),
    val folders: List<LauncherFolderEntity> = emptyList(),
    val itemIcons: List<ItemIconEntity> = emptyList(),
    val savedShortcuts: List<SavedShortcutEntity> = emptyList(),
    val popups: List<AppPopupEntity> = emptyList(),
    val preferences: Map<String, Map<String, Any>> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
)

class BackupImporter(
    context: Context,
    private val database: LauncherDatabase = LauncherDatabase.getInstance(context),
    private val databaseFile: File = context.getDatabasePath("grace_launcher.db"),
    private val afterSection: suspend (BackupSection) -> Unit = {},
) {
    private val context = context.applicationContext

    suspend fun import(input: InputStream, installedAppKeys: Set<String>, installedProviders: Set<String>): ImportResult =
        BackupLock.withLock { withContext(Dispatchers.IO) { run(input, installedAppKeys, installedProviders, restoring = false) } }

    /** Restoring applies this phone's own safety export: no new safety export, no hook, and widget IDs stay real. */
    private suspend fun run(input: InputStream, installedAppKeys: Set<String>, installedProviders: Set<String>, restoring: Boolean): ImportResult {
        val dir = File(context.cacheDir, "backup-import-${UUID.randomUUID()}")
        try {
            BackupArchive.extract(input, dir)?.let { return ImportResult.Rejected(it) }
            val contents = try { read(dir) } catch (e: Rejection) { return ImportResult.Rejected(e.problem) }
            val backup = if (restoring) contents else contents.remapped(profileSerials(contents.manifest.profiles))
            if (!restoring) {
                try { writeSafetyExport() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    return ImportResult.Failed(e, restored = true)
                }
            }
            // A cancel here could leave the DB committed but preferences and files old; finish or restore instead.
            return withContext(NonCancellable) {
                try {
                    apply(backup, dir, restoring)
                    ImportResult.Success(summary(backup, installedAppKeys, installedProviders))
                } catch (e: Exception) {
                    ImportResult.Failed(e, restored = !restoring && restoreSafetyExport())
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun read(dir: File): BackupContents {
        val manifest = runCatching { BackupManifest.decode(File(dir, BackupArchive.MANIFEST).readText()) }.getOrNull()
            ?: throw Rejection(BackupProblem.NotABackup)
        checkCompatible(manifest, supportedSchema = LauncherDatabase.SCHEMA_VERSION)?.let { throw Rejection(it) }
        val sections = manifest.sections
        val preferences = if (PreferenceSections.keys.none { it in sections }) emptyMap() else parse {
            val json = JSONObject(File(dir, BackupArchive.PREFERENCES).readText())
            PreferenceSections.filterKeys { it in sections }.values.associateWith { decodePreferences(json.getJSONObject(it)) }
        }
        preferences.forEach { (name, values) ->
            PreferenceTypes[name].orEmpty().forEach { (key, type) ->
                if (values[key]?.let(type::isInstance) == false) throw Rejection(BackupProblem.NotABackup)
            }
        }
        val labels = File(dir, BackupArchive.LABELS).takeIf { it.isFile }?.let { file ->
            parse { JSONObject(file.readText()).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }
        }.orEmpty()
        val contents = BackupContents(manifest, preferences = preferences, labels = labels)
        if (DatabaseSections.none { it in sections }) return contents
        val file = File(dir, BackupArchive.DATABASE)
        val version = databaseVersion(file) ?: throw Rejection(BackupProblem.NotABackup)
        if (version > LauncherDatabase.SCHEMA_VERSION) throw Rejection(BackupProblem.NewerSchema(version))
        val copy = Room.databaseBuilder(context, LauncherDatabase::class.java, file.absolutePath)
            .addMigrations(*LauncherDatabase.Migrations).build()
        try {
            val dao = copy.backupDao()
            return contents.copy(settings = dao.allSettings(), hiddenApps = dao.allHiddenApps(), folders = dao.allFolders(),
                itemIcons = dao.allItemIcons(), savedShortcuts = dao.allSavedShortcuts(), popups = dao.allPopups())
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            throw Rejection(BackupProblem.NotABackup)
        } finally {
            copy.close()
        }
    }

    private suspend fun apply(backup: BackupContents, dir: File, restoring: Boolean) {
        val sections = backup.manifest.sections
        suspend fun section(section: BackupSection, block: suspend () -> Unit) {
            if (section !in sections) return
            block()
            if (!restoring) afterSection(section)
        }
        val dao = database.backupDao()
        database.withTransaction {
            section(BackupSection.Settings) { dao.clearSettings(); dao.insertSettings(backup.settings) }
            section(BackupSection.HiddenApps) { dao.clearHiddenApps(); dao.insertHiddenApps(backup.hiddenApps) }
            section(BackupSection.Folders) { dao.clearFolders(); dao.insertFolders(backup.folders) }
            section(BackupSection.ItemIcons) { dao.clearItemIcons(); dao.insertItemIcons(backup.itemIcons) }
            section(BackupSection.SavedShortcuts) { dao.clearSavedShortcuts(); dao.insertSavedShortcuts(backup.savedShortcuts) }
            section(BackupSection.Popups) { dao.clearPopups(); dao.insertPopups(backup.popups) }
            val legacyKey = if (BackupSection.Settings !in sections) null else backup.settings.firstOrNull { it.id == 0 }
                ?.let { HomeLayout.decode(it.homeLayoutJson) }?.takeIf { it.hasWidget }?.let { "widget:${it.widgetId}" }
            if (BackupSection.Settings in sections) LauncherSettingsRepository(database).migrateLegacyHomeWidget()
            if (!restoring && (BackupSection.Popups in sections || legacyKey != null))
                assignPlaceholders(BackupSection.Popups in sections, legacyKey, BackupSection.Settings in sections)
        }
        PreferenceSections.forEach { (section, name) ->
            section(section) {
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                backup.preferences[name].orEmpty().forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                check(editor.commit()) { "Unable to save $name" }
            }
        }
        FileSections.forEach { (section, paths) ->
            section(section) { replaceDirectory(File(dir, paths.second.trimEnd('/')), File(context.filesDir, paths.first)) }
        }
    }

    /**
     * Widget IDs from another phone must never stay real. Converts every imported widget (all pop-ups when
     * they were imported, else only the migrated legacy home widget), numbering below existing placeholders.
     */
    private suspend fun assignPlaceholders(allImported: Boolean, legacyKey: String?, withHomeOrder: Boolean) {
        val dao = database.backupDao()
        val popups = dao.allPopups().associate { it.ownerKey to PopupItem.decode(it.itemsJson) }
        val foreign = popups.mapValues { (owner, items) ->
            items.filter { item ->
                val widget = item.widget ?: return@filter false
                allImported || isWidgetPlaceholder(widget.widgetId) || (owner == HomeContentKey && item.key == legacyKey)
            }
        }.filterValues { it.isNotEmpty() }
        val settings = if (withHomeOrder) database.settingsDao().readSettings() else null
        val layout = settings?.let { HomeLayout.decode(it.homeLayoutJson) }
        val converted = WidgetLayout(foreign, layout?.homeOrder).toPlaceholders()
        foreign.forEach { (owner, items) ->
            val renamed = items.zip(converted.popups.getValue(owner)).associate { (old, new) -> old.key to new }
            val next = popups.getValue(owner).map { renamed[it.key] ?: it }
            if (next != popups[owner]) dao.insertPopups(listOf(AppPopupEntity(owner, PopupItem.encode(next))))
        }
        if (settings != null && layout != null && converted.homeOrder != layout.homeOrder)
            dao.insertSettings(listOf(settings.copy(homeLayoutJson = layout.copy(homeOrder = converted.homeOrder).encode())))
    }

    private suspend fun summary(backup: BackupContents, installedAppKeys: Set<String>, installedProviders: Set<String>): ImportSummary {
        val sections = backup.manifest.sections
        val popups = if (BackupSection.Popups in sections) backup.popups.associate { it.ownerKey to PopupItem.decode(it.itemsJson) } else emptyMap()
        val favorites = backup.preferences[FavoritesStore.FILE_NAME]?.let(::favoritesOf).orEmpty()
        val counts = countsOf(if (BackupSection.Folders in sections) backup.folders.size else 0, popups, favorites)
        val referenced = favorites + popups.toSortedMap().values.flatten().filter { it.widget == null }.map { it.key }
        val needSetup = database.backupDao().allPopups().flatMap { PopupItem.decode(it.itemsJson) }.mapNotNull { it.widget }
            .filter { isWidgetPlaceholder(it.widgetId) && it.widgetProvider !in installedProviders }
        // A saved shortcut reappears once its app is installed in the same profile; package match survives activity renames.
        val missingShortcuts = if (BackupSection.SavedShortcuts !in sections) emptyList() else backup.savedShortcuts.filter { s ->
            val profile = s.itemKey.substringBefore("shortcut:")
            installedAppKeys.none { it.startsWith(profile) && it.startsWith("profile:") == profile.isNotEmpty() && packageOf(it) == s.packageName }
        }
        val shortcutLabels = missingShortcuts.associate { it.itemKey to it.label }
        val (leftOut, notInstalled) = (missingAppKeys(referenced, installedAppKeys) + shortcutLabels.keys)
            .partition { it.startsWith("profile:$NoProfile:") }
        fun name(key: String) = backup.labels[key] ?: shortcutLabels[key] ?: packageOf(key)
        return ImportSummary(counts.folders, counts.apps, counts.favorites, counts.widgets, BackupSection.Settings in sections,
            notInstalled.map(::name),
            needSetup.map { w -> w.widgetProvider?.let(backup.labels::get) ?: w.widgetLabel ?: w.widgetProvider?.substringBefore('/').orEmpty() },
            leftOut.map(::name))
    }

    private fun profileSerials(profiles: List<BackupProfile>): Map<Long, Long> {
        val local = deviceProfiles(context)
        return profiles.mapNotNull { saved ->
            (local.firstOrNull { it.type == saved.type }?.serial ?: NoProfile).takeIf { it != saved.serial }?.let { saved.serial to it }
        }.toMap()
    }

    private suspend fun writeSafetyExport() {
        val target = File(context.noBackupFilesDir, SafetyFile)
        val temp = File(target.path + ".tmp")
        temp.outputStream().use { BackupExporter(context, database, databaseFile).write(it, emptyMap()) }
        check(temp.renameTo(target)) { "Unable to save the safety export" }
    }

    private suspend fun restoreSafetyExport(): Boolean = try {
        File(context.noBackupFilesDir, SafetyFile).inputStream().use { run(it, emptySet(), emptySet(), restoring = true) } is ImportResult.Success
    } catch (_: Exception) { false }
}

private inline fun <T> parse(block: () -> T): T = try { block() } catch (_: Exception) { throw Rejection(BackupProblem.NotABackup) }

/** Validates the file before Room opens it: Room's default corruption handling would delete it and read empty tables. */
private fun databaseVersion(file: File): Int? = runCatching {
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE, DatabaseErrorHandler { }).use { db ->
        val ok = db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
        db.version.takeIf { ok && it >= 1 }
    }
}.getOrNull()

private fun replaceDirectory(source: File, target: File) {
    check(!target.exists() || target.deleteRecursively()) { "Unable to clear ${target.name}" }
    if (source.isDirectory) check(source.renameTo(target) || source.copyRecursively(target)) { "Unable to restore ${target.name}" }
}

private fun packageOf(key: String) =
    (if (key.startsWith("profile:")) key.substringAfter(':').substringAfter(':') else key).substringBefore('/')

private fun BackupContents.remapped(serials: Map<Long, Long>): BackupContents {
    if (serials.isEmpty()) return this
    fun key(value: String) = remapValue(value, serials)
    fun json(value: String?) = value?.let(::key)
    return copy(
        settings = settings.map {
            it.copy(iconPackPackage = json(it.iconPackPackage), iconPackPackagesJson = json(it.iconPackPackagesJson),
                iconDesignJson = json(it.iconDesignJson), mediaAppKey = json(it.mediaAppKey), graceButtonJson = json(it.graceButtonJson),
                weatherLocationId = json(it.weatherLocationId), clockAppKey = json(it.clockAppKey), clockStyleJson = json(it.clockStyleJson),
                homeLayoutJson = json(it.homeLayoutJson), appFontId = json(it.appFontId), fontLibraryJson = json(it.fontLibraryJson),
                listAppearanceJson = json(it.listAppearanceJson), privateSpaceJson = json(it.privateSpaceJson),
                workProfileJson = json(it.workProfileJson), homeGesturesJson = json(it.homeGesturesJson), searchJson = json(it.searchJson),
                shortcutsFoldersJson = json(it.shortcutsFoldersJson), digitalWellbeingJson = json(it.digitalWellbeingJson),
                widgetScreenJson = json(it.widgetScreenJson), headphoneAppsJson = json(it.headphoneAppsJson))
        },
        hiddenApps = hiddenApps.map { HiddenAppEntity(key(it.appKey)) },
        itemIcons = itemIcons.map { it.copy(itemKey = key(it.itemKey)) },
        savedShortcuts = savedShortcuts.map { it.copy(itemKey = key(it.itemKey)) },
        popups = popups.map { AppPopupEntity(key(it.ownerKey), key(it.itemsJson)) },
        preferences = preferences.mapValues { (_, values) ->
            values.entries.associate { (name, value) ->
                key(name) to when (value) {
                    is String -> key(value)
                    is Set<*> -> value.filterIsInstance<String>().map(::key).toSet()
                    else -> value
                }
            }
        },
        labels = labels.mapKeys { (name, _) -> key(name) },
    )
}

/**
 * Rewrites `profile:<serial>:` keys found anywhere in a stored value: a bare key, a key after a prefix such as
 * `label:`, or any string inside JSON. Returns the original text when nothing changes, so JSON is not reformatted.
 */
internal fun remapValue(value: String, serials: Map<Long, Long>): String {
    if (serials.isEmpty() || "profile:" !in value) return value
    val start = value.trimStart().firstOrNull()
    if (start == '{' || start == '[') runCatching { JSONTokener(value).nextValue() }.getOrNull()?.let { parsed ->
        var changed = false
        fun walk(node: Any?): Any? = when (node) {
            is JSONObject -> JSONObject().also { out -> node.keys().forEach { k -> out.put(walk(k) as String, walk(node.get(k))) } }
            is JSONArray -> JSONArray().also { out -> for (i in 0 until node.length()) out.put(walk(node.get(i))) }
            is String -> remapValue(node, serials).also { if (it != node) changed = true }
            else -> node
        }
        val result = walk(parsed)
        return if (changed) result.toString() else value
    }
    val at = value.indexOf("profile:")
    return value.substring(0, at) + remapProfileKey(value.substring(at), serials)
}
