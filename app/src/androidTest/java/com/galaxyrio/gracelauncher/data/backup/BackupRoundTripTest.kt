package com.galaxyrio.gracelauncher.data.backup

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.AppPopupEntity
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.GraceWidget
import com.galaxyrio.gracelauncher.data.HiddenAppEntity
import com.galaxyrio.gracelauncher.data.HomeContentKey
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.ItemIconEntity
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.LauncherFolderEntity
import com.galaxyrio.gracelauncher.data.LauncherItemsRepository
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.LauncherSettingsEntity
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.data.SavedShortcutEntity
import com.galaxyrio.gracelauncher.ui.overlays.reorderPopupItems
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val tag = "backup-test-${UUID.randomUUID()}"
    private val root = File(base.cacheDir, tag)
    private val context = Sandbox(base, tag, root)
    private val databaseName = "$tag.db"
    private val databaseFile = base.getDatabasePath(databaseName)
    private val database = Room.databaseBuilder(base, LauncherDatabase::class.java, databaseName)
        .addMigrations(*LauncherDatabase.Migrations).build()
    private val allApps = setOf("a/a.Main", "b/b.Main", "c/c.Main", "d/d.Main")
    private val labels = mapOf("a/a.Main" to "A label", "b/b.Main" to "B label")

    @get:Rule val helper = MigrationTestHelper(instrumentation, LauncherDatabase::class.java)

    @After
    fun cleanUp() {
        database.close()
        base.deleteDatabase(databaseName)
        base.deleteDatabase("$tag-v26.db")
        listOf(FavoritesFile, AppearanceFile).forEach { base.deleteSharedPreferences("$tag-$it") }
        root.deleteRecursively()
    }

    @Test
    fun roundTripRestoresEveryTable() = runBlocking {
        seed()
        val seeded = state()
        val backup = export()
        wipe()

        val result = importer().import(backup.inputStream(), allApps, setOf("w/w.Provider"))

        val summary = (result as ImportResult.Success).summary
        assertEquals(ImportSummary(folders = 2, apps = 3, favorites = 2, widgets = 1, settingsRestored = true,
            notInstalled = emptyList(), widgetsMissingProvider = emptyList()), summary)
        val after = state()
        assertEquals(seeded.copy(settings = emptyList(), popups = emptyList()), after.copy(settings = emptyList(), popups = emptyList()))
        val seededLayout = HomeLayout.decode(seeded.settings.single().homeLayoutJson)
        val layout = HomeLayout.decode(after.settings.single().homeLayoutJson)
        assertEquals(seeded.settings.single().copy(homeLayoutJson = null), after.settings.single().copy(homeLayoutJson = null))
        assertEquals(ClockLayout.TwoLines, ClockStyle.decode(after.settings.single().clockStyleJson).layout)
        assertEquals(listOf(GraceWidget.Clock.key, "widget:-2"), layout.homeOrder)
        assertEquals(seededLayout.copy(homeOrder = layout.homeOrder), layout)
        assertEquals(seeded.popups.filterNot { it.ownerKey == HomeContentKey }, after.popups.filterNot { it.ownerKey == HomeContentKey })
        val home = PopupItem.decode(after.popups.single { it.ownerKey == HomeContentKey }.itemsJson)
        assertEquals(listOf(PopupItem("widget:-2", HomeLayout(widgetId = -2, widgetProvider = "w/w.Provider", widgetHeightDp = 180))), home)
    }

    @Test
    fun legacyHomeWidgetBecomesPlaceholder() = runBlocking {
        seed()
        LauncherSettingsRepository(database).mutateSettings {
            it.copy(homeLayout = HomeLayout(widgetId = 7, widgetProvider = "w/w.Legacy", widgetHeightDp = 90,
                homeOrder = listOf(GraceWidget.Clock.key, "widget:7")))
        }
        database.itemsDao().deletePopup(HomeContentKey)
        val backup = export()
        wipe()

        val summary = (importer().import(backup.inputStream(), allApps, emptySet()) as ImportResult.Success).summary

        val layout = HomeLayout.decode(database.backupDao().allSettings().single().homeLayoutJson)
        assertFalse(layout.hasWidget)
        assertEquals(listOf(GraceWidget.Clock.key, "widget:-2"), layout.homeOrder)
        val home = PopupItem.decode(database.backupDao().allPopups().single { it.ownerKey == HomeContentKey }.itemsJson)
        assertEquals(listOf("widget:-2"), home.map { it.key })
        assertEquals(-2, home.single().widget?.widgetId)
        assertEquals("w/w.Legacy", home.single().widget?.widgetProvider)
        assertEquals(listOf("w"), summary.widgetsMissingProvider)
        val ids = database.backupDao().allPopups().flatMap { PopupItem.decode(it.itemsJson) }.mapNotNull { it.widget?.widgetId }
        assertTrue(ids.all(::isWidgetPlaceholder))
    }

    @Test
    fun missingSectionLeavesStateUnchanged() = runBlocking {
        seed()
        val backup = rezip(export()) { it.copy(sections = setOf(BackupSection.Folders, BackupSection.Popups)) }
        LauncherSettingsRepository(database).mutateSettings { it.copy(clockStyle = ClockStyle(layout = ClockLayout.Week)) }
        LauncherSettingsRepository(database).saveFolder(LauncherFolder("f3", "Later", listOf("d/d.Main"), FolderPlacement.AppList))
        prefs(FavoritesFile).edit().putString("favorite_component_order", JSONArray(listOf("c/c.Main")).toString()).commit()
        File(context.filesDir, "clock_fonts/other_Font.ttf").writeText("other")
        val changed = state()

        assertTrue(importer().import(backup.inputStream(), allApps, emptySet()) is ImportResult.Success)

        val after = state()
        assertEquals(changed.settings, after.settings)
        assertEquals(changed.favorites, after.favorites)
        assertEquals(changed.fonts, after.fonts)
        assertEquals(changed.hidden, after.hidden)
        assertEquals(listOf("f1", "f2"), after.folders.map { it.id })
        assertFalse(after.popups.any { it.ownerKey == "folder:f3" })
    }

    @Test
    fun missingAppsKeepKeysAndAreListed() = runBlocking {
        seed()
        val backup = export()
        wipe()

        val result = importer().import(backup.inputStream(), allApps - "b/b.Main", setOf("w/w.Provider"))

        assertEquals(listOf("B label"), (result as ImportResult.Success).summary.notInstalled)
        val folder = database.backupDao().allPopups().single { it.ownerKey == "folder:f1" }
        assertEquals(listOf("a/a.Main", "b/b.Main"), PopupItem.decode(folder.itemsJson).map { it.key })
    }

    @Test
    fun failureRestoresSafetyExport() = runBlocking {
        for (failing in listOf(BackupSection.Popups, BackupSection.Fonts)) {
            seed()
            val backup = export()
            LauncherSettingsRepository(database).mutateSettings { it.copy(clockStyle = ClockStyle(layout = ClockLayout.Week)) }
            LauncherSettingsRepository(database).deleteFolder("f2")
            prefs(FavoritesFile).edit().putString("favorite_component_order", JSONArray(listOf("c/c.Main")).toString()).commit()
            File(context.filesDir, "clock_fonts/other_Font.ttf").writeText("other")
            val before = state()
            val importer = BackupImporter(context, database, databaseFile, afterSection = {
                if (it == failing) throw IllegalStateException("fault after $it")
            })

            val result = importer.import(backup.inputStream(), allApps, emptySet())

            assertTrue("$failing: $result", result is ImportResult.Failed && result.restored)
            assertEquals("$failing", before, state())
            assertTrue(File(context.noBackupFilesDir, "backup-safety.zip").isFile)
            wipe()
        }
    }

    @Test
    fun cancelledImportFinishesOrRestores() = runBlocking {
        // Popups: cancel inside the DB transaction. Favorites: cancel after the DB commit, before the other files.
        for (gated in listOf(BackupSection.Popups, BackupSection.Favorites)) {
            seed()
            val seeded = state()
            val backup = export()
            LauncherSettingsRepository(database).deleteFolder("f2")
            prefs(FavoritesFile).edit().putString("favorite_component_order", JSONArray(listOf("c/c.Main")).toString()).commit()
            File(context.filesDir, "clock_fonts/other_Font.ttf").writeText("other")
            val before = state()
            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            // The gated wait ignores the cancel, like blocking DB work; every later section is a cancellable suspension.
            val importer = BackupImporter(context, database, databaseFile, afterSection = {
                if (it == gated) { entered.complete(Unit); withContext(NonCancellable) { gate.await() } } else yield()
            })

            val job = launch(Dispatchers.IO) { importer.import(backup.inputStream(), allApps, emptySet()) }
            withTimeout(10_000) { entered.await() }
            job.cancel()
            gate.complete(Unit)
            withTimeout(10_000) { job.join() }

            val after = state()
            val applied = after.folders.map { it.id } == listOf("f1", "f2") && after.favorites == seeded.favorites &&
                after.appearance == seeded.appearance && after.fonts == seeded.fonts && after.iconFiles == seeded.iconFiles
            assertTrue("$gated half-applied: $after", applied || after == before)
            wipe()
        }
    }

    @Test
    fun newerFormatRejectedWithoutChanges() = runBlocking {
        seed()
        val backup = rezip(export()) { it.copy(formatVersion = 2) }
        LauncherSettingsRepository(database).deleteFolder("f2")
        val before = state()

        val result = importer().import(backup.inputStream(), allApps, emptySet())

        assertEquals(ImportResult.Rejected(BackupProblem.NewerFormat(2)), result)
        assertEquals(before, state())
    }

    @Test
    fun olderSchemaBackupImports() = runBlocking {
        val name = "$tag-v26.db"
        helper.createDatabase(name, 26).apply {
            execSQL("INSERT INTO folders (id, name, placement, appListAtBottom, autoHideApps, isAppStack) VALUES ('old', 'Old', 'AppList', 1, 0, 0)")
            execSQL("INSERT INTO app_popups (ownerKey, itemsJson) VALUES ('folder:old', '[{\"key\":\"a/a.Main\"}]')")
            close()
        }
        val manifest = BackupManifest(BACKUP_FORMAT_VERSION, "1.0", 1, 26, 0, setOf(BackupSection.Folders, BackupSection.Popups),
            BackupCounts(1, 1, 0, 0), emptyList())
        val backup = zip(BackupArchive.MANIFEST to manifest.encode().toByteArray(),
            BackupArchive.DATABASE to base.getDatabasePath(name).readBytes())

        val result = importer().import(backup.inputStream(), allApps, emptySet())

        assertTrue("$result", result is ImportResult.Success)
        assertEquals(listOf("old"), database.backupDao().allFolders().map { it.id })
        val popup = database.backupDao().allPopups().single()
        assertEquals("folder:old", popup.ownerKey)
        assertEquals(listOf("a/a.Main"), PopupItem.decode(popup.itemsJson).map { it.key })
    }

    @Test
    fun exportDuringImportWaits() = runBlocking {
        seed()
        val backup = export()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val importer = BackupImporter(context, database, databaseFile, afterSection = {
            if (it == BackupSection.Settings) { entered.complete(Unit); gate.await() }
        })

        val importing = async(Dispatchers.IO) { importer.import(backup.inputStream(), allApps, emptySet()) }
        withTimeout(10_000) { entered.await() }
        val exporting = async(Dispatchers.IO) { exporter().export(ByteArrayOutputStream(), labels) }
        delay(500)
        assertFalse(exporting.isCompleted)
        gate.complete(Unit)

        assertTrue(withTimeout(10_000) { importing.await() } is ImportResult.Success)
        withTimeout(10_000) { exporting.await() }
        Unit
    }

    @Test
    fun nonBackupFileRejected() = runBlocking {
        seed()
        val before = state()
        val inputs = listOf(
            Random(1).nextBytes(4096),
            zip("data/notes.txt" to "hello".toByteArray()),
            "nlb".toByteArray() + Random(2).nextBytes(512),
        )
        inputs.forEach { bytes ->
            assertEquals(ImportResult.Rejected(BackupProblem.NotABackup), importer().import(bytes.inputStream(), allApps, emptySet()))
        }
        assertEquals(before, state())
    }

    @Test
    fun folderEditKeepsUninstalledKeys() = runBlocking {
        val current = listOf("a", "missing", "b").map(::PopupItem)
        assertEquals(listOf("b", "missing", "a"), reorderPopupItems(listOf("b", "a"), current).map { it.key })

        LauncherSettingsRepository(database).saveFolder(LauncherFolder("f1", "Work", listOf("a", "missing", "b"), FolderPlacement.AppList))
        LauncherItemsRepository(database).updatePopup("folder:f1", emptyList()) { reorderPopupItems(listOf("b", "a"), it) }
        val stored = database.backupDao().allPopups().single { it.ownerKey == "folder:f1" }
        assertEquals(listOf("b", "missing", "a"), PopupItem.decode(stored.itemsJson).map { it.key })
    }

    @Test
    fun profileKeysRemapInsideJson() {
        val serials = mapOf(10L to 11L)
        assertEquals("profile:11:a/a.Main", remapValue("profile:10:a/a.Main", serials))
        assertEquals("label:profile:11:a/a.Main", remapValue("label:profile:10:a/a.Main", serials))
        assertEquals("profile:12:a/a.Main", remapValue("profile:12:a/a.Main", serials))
        val items = PopupItem.decode(remapValue(PopupItem.encode(listOf(PopupItem("profile:10:a/a.Main"), PopupItem("b/b.Main"))), serials))
        assertEquals(listOf("profile:11:a/a.Main", "b/b.Main"), items.map { it.key })
        val unchanged = """{"x":1.0,"k":"profile:12:a/a.Main"}"""
        assertEquals(unchanged, remapValue(unchanged, serials))
    }

    @Test
    fun hostileEntryNamesRejectedWithoutWritingOutside() = runBlocking {
        seed()
        val valid = entries(export())
        val before = state()
        listOf("../escape.txt", "data/../../escape.txt", "/escape.txt", "..\\escape.txt", "fonts/../../escape.txt").forEach { name ->
            val hostile = zip(*(valid + (name to "x".toByteArray())).toTypedArray())
            assertTrue(name, importer().import(hostile.inputStream(), allApps, emptySet()) is ImportResult.Rejected)
        }
        assertEquals(before, state())
        assertFalse(root.walkTopDown().any { it.name == "escape.txt" })
        assertFalse(File(root.parentFile, "escape.txt").exists())
        assertNoImportLeftovers()
    }

    @Test
    fun compressionBombRejected() = runBlocking {
        seed()
        val valid = entries(export())
        val before = state()
        val bomb = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                valid.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
                zip.putNextEntry(ZipEntry("fonts/big.ttf"))
                val chunk = ByteArray(1 shl 20)
                repeat(300) { zip.write(chunk) }
                zip.closeEntry()
            }
        }.toByteArray()

        assertEquals(ImportResult.Rejected(BackupProblem.TooLarge), importer().import(bomb.inputStream(), allApps, emptySet()))
        assertEquals(before, state())
        assertNoImportLeftovers()
    }

    @Test
    fun brokenOrForeignDatabaseRejected() = runBlocking {
        seed()
        val valid = export()
        val before = state()
        val header = "SQLite format 3\u0000".toByteArray()
        val foreign = sqliteFile { db -> db.execSQL("CREATE TABLE notes (body TEXT)"); db.version = LauncherDatabase.SCHEMA_VERSION }
        listOf(Random(3).nextBytes(8192), header + Random(4).nextBytes(8192), ByteArray(0), foreign).forEach { db ->
            val hostile = replace(valid, BackupArchive.DATABASE, db)
            assertEquals(ImportResult.Rejected(BackupProblem.NotABackup), importer().import(hostile.inputStream(), allApps, emptySet()))
        }
        assertEquals(before, state())
        assertNoImportLeftovers()
    }

    @Test
    fun databaseNewerThanManifestRejected() = runBlocking {
        seed()
        val valid = export()
        val before = state()
        val newer = mutateDatabase(valid) { it.version = LauncherDatabase.SCHEMA_VERSION + 5 }

        val result = importer().import(newer.inputStream(), allApps, emptySet())

        assertEquals(ImportResult.Rejected(BackupProblem.NewerSchema(LauncherDatabase.SCHEMA_VERSION + 5)), result)
        assertEquals(before, state())
    }

    @Test
    fun missingOrMalformedPreferencesRejected() = runBlocking {
        seed()
        val valid = export()
        val before = state()
        val inputs = listOf(
            zip(*entries(valid).filterNot { it.first == BackupArchive.PREFERENCES }.toTypedArray()),
            replace(valid, BackupArchive.PREFERENCES, "[]".toByteArray()),
            replace(valid, BackupArchive.PREFERENCES, """{"grace_launcher_preferences":{"k":{"type":"blob","value":1}},"launcher_appearance":{}}""".toByteArray()),
            replace(valid, BackupArchive.MANIFEST, "{".toByteArray()),
        )
        inputs.forEach { bytes ->
            assertEquals(ImportResult.Rejected(BackupProblem.NotABackup), importer().import(bytes.inputStream(), allApps, emptySet()))
        }
        assertEquals(before, state())
    }

    @Test
    fun garbageStoredJsonDoesNotBreakTheLauncher() = runBlocking {
        seed()
        val hostile = mutateDatabase(export()) { db ->
            db.execSQL("UPDATE launcher_settings SET homeLayoutJson = 'garbage', clockStyleJson = '{{', listAppearanceJson = '[1,'")
            db.execSQL("UPDATE app_popups SET itemsJson = '{{'")
            db.execSQL("UPDATE folders SET placement = 'Bogus'")
            db.execSQL("UPDATE item_icons SET iconJson = 'nope'")
        }
        val before = state()

        val result = importer().import(hostile.inputStream(), allApps, emptySet())

        if (result is ImportResult.Success) {
            withTimeout(10_000) { LauncherSettingsRepository(database).snapshots.first() }
            withTimeout(10_000) { LauncherItemsRepository(database).snapshots.first() }
            WidgetRestoreRepository(database).pending()
        } else {
            assertTrue(result.toString(), result is ImportResult.Failed && result.restored)
            assertEquals(before, state())
        }
        Unit
    }

    @Test
    fun reimportingABackupTakenMidRestoreKeepsOnePlaceholderPerWidget() = runBlocking {
        seed()
        assertTrue(importer().import(export().inputStream(), allApps, setOf("w/w.Provider")) is ImportResult.Success)
        val midRestore = export()
        assertTrue(importer().import(midRestore.inputStream(), allApps, setOf("w/w.Provider")) is ImportResult.Success)

        val pending = WidgetRestoreRepository(database).pending()
        assertEquals(1, pending.size)
        assertEquals(pending.map { it.placeholderId }.distinct(), pending.map { it.placeholderId })
        val ids = database.backupDao().allPopups().flatMap { PopupItem.decode(it.itemsJson) }.mapNotNull { it.widget?.widgetId }
        assertTrue(ids.all(::isWidgetPlaceholder))
        val layout = HomeLayout.decode(database.backupDao().allSettings().single().homeLayoutJson)
        assertEquals(listOf(GraceWidget.Clock.key, "widget:${pending.single().placeholderId}"), layout.homeOrder)
    }

    @Test
    fun manyTinyEntriesFinishAndCleanUp() = runBlocking {
        seed()
        val valid = entries(export())
        val many = zip(*(valid + (0 until 20_000).map { "icons/i$it.png" to ByteArray(0) }).toTypedArray())

        val result = withTimeout(120_000) { importer().import(many.inputStream(), allApps, emptySet()) }

        assertTrue(result.toString(), result is ImportResult.Success)
        assertNoImportLeftovers()
    }

    @Test
    fun wrongPreferenceTypeIsRejectedBeforeItCanCrashTheLauncher() = runBlocking {
        seed()
        val before = state()
        val hostile = replace(export(), BackupArchive.PREFERENCES,
            """{"grace_launcher_preferences":{},"launcher_appearance":{"text_mode":{"type":"boolean","value":true}}}""".toByteArray())

        // A Success here would leave LauncherPreferences.textMode throwing ClassCastException on every start.
        assertEquals(ImportResult.Rejected(BackupProblem.NotABackup), importer().import(hostile.inputStream(), allApps, emptySet()))
        assertEquals(before, state())
    }

    @Test
    fun backupCutOffBeforeFontsIsRejected() = runBlocking {
        seed()
        val valid = entries(export())
        assertTrue(valid.any { it.first.startsWith(BackupArchive.FONTS_DIR) })
        val before = state()
        // Every complete entry before fonts/, with no central directory: what a cut at that entry boundary leaves.
        val cut = ByteArrayOutputStream().also { out ->
            val zip = ZipOutputStream(out)
            valid.takeWhile { !it.first.startsWith(BackupArchive.FONTS_DIR) && !it.first.startsWith(BackupArchive.ICONS_DIR) }
                .forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            zip.flush()
        }.toByteArray()

        val result = importer().import(cut.inputStream(), allApps, emptySet())

        assertTrue("truncated backup gave $result; fonts now ${files("clock_fonts")}", result is ImportResult.Rejected)
        assertEquals(before, state())
    }

    @Test
    fun appsFromAProfileThisPhoneLacksAreLeftOutNotMovedToAnotherProfile() = runBlocking {
        seed()
        database.itemsDao().savePopup(AppPopupEntity("folder:f1", PopupItem.encode(listOf(PopupItem("profile:10:x/x.Main")))))
        // Before Android 15 every local profile reports "work", so a "private" profile never matches.
        val backup = rezip(export()) { it.copy(profiles = listOf(BackupProfile(10, "private"))) }

        val summary = (importer().import(backup.inputStream(), allApps + "profile:10:x/x.Main", emptySet()) as ImportResult.Success).summary

        assertEquals(listOf("x"), summary.leftOut)
        assertFalse(summary.notInstalled.contains("x"))
        val folder = PopupItem.decode(database.backupDao().allPopups().single { it.ownerKey == "folder:f1" }.itemsJson)
        assertEquals(listOf("profile:-1:x/x.Main"), folder.map { it.key })
    }

    @Test
    fun savedShortcutOfAMissingAppIsListed() = runBlocking {
        seed()
        val summary = (importer().import(export().inputStream(), allApps - "a/a.Main", emptySet()) as ImportResult.Success).summary
        assertTrue(summary.notInstalled.toString(), "Shortcut" in summary.notInstalled)
    }

    private fun assertNoImportLeftovers() =
        assertEquals(emptyList<String>(), context.cacheDir.listFiles().orEmpty().map { it.name }.filter { it.startsWith("backup-import-") })

    private fun entries(bytes: ByteArray): List<Pair<String, ByteArray>> = buildList {
        ZipInputStream(bytes.inputStream()).use { input ->
            while (true) { val entry = input.nextEntry ?: break; add(entry.name to input.readBytes()) }
        }
    }

    private fun replace(bytes: ByteArray, name: String, data: ByteArray) =
        zip(*entries(bytes).map { if (it.first == name) name to data else it }.toTypedArray())

    private fun sqliteFile(block: (SQLiteDatabase) -> Unit): ByteArray {
        val file = File(root, "hostile-${UUID.randomUUID()}.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use(block)
        return file.readBytes().also { file.delete() }
    }

    private fun mutateDatabase(bytes: ByteArray, block: (SQLiteDatabase) -> Unit): ByteArray {
        val file = File(root, "hostile-${UUID.randomUUID()}.db").apply { parentFile?.mkdirs() }
        file.writeBytes(entries(bytes).single { it.first == BackupArchive.DATABASE }.second)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            block(db)
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        }
        return replace(bytes, BackupArchive.DATABASE, file.readBytes()).also {
            listOf("", "-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        }
    }

    private fun importer() = BackupImporter(context, database, databaseFile)
    private fun exporter() = BackupExporter(context, database, databaseFile)
    private suspend fun export() = ByteArrayOutputStream().also { exporter().export(it, labels) }.toByteArray()
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private suspend fun seed() {
        val settings = LauncherSettingsRepository(database)
        settings.updateSettings(LauncherSettings(clockStyle = ClockStyle(layout = ClockLayout.TwoLines),
            homeLayout = HomeLayout(homeOrder = listOf(GraceWidget.Clock.key, "widget:41"))))
        settings.saveFolder(LauncherFolder("f1", "Work", listOf("a/a.Main", "b/b.Main"), FolderPlacement.AppList))
        settings.saveFolder(LauncherFolder("f2", "Play", listOf("c/c.Main"), FolderPlacement.Both))
        settings.hideApp("d/d.Main")
        val items = database.itemsDao()
        items.savePopup(AppPopupEntity(HomeContentKey, PopupItem.encode(listOf(
            PopupItem("widget:41", HomeLayout(widgetId = 41, widgetProvider = "w/w.Provider", widgetHeightDp = 180))))))
        items.saveIcon(ItemIconEntity("a/a.Main", ItemIcon.Theme.encode()))
        items.saveShortcut(SavedShortcutEntity("shortcut:a/s1", "a", "s1", "a/a.Main", "Shortcut"))
        prefs(FavoritesFile).edit().putString("favorite_component_order", JSONArray(listOf("a/a.Main", "folder:f1")).toString())
            .putStringSet("favorite_components", setOf("a/a.Main", "folder:f1")).commit()
        prefs(AppearanceFile).edit().putString("text_mode", "Dark").putBoolean("themed_icons", true).putInt("columns", 3)
            .putLong("since", 5L).putFloat("scale", 0.75f).putString("label:a/a.Main", "Alpha").commit()
        File(context.filesDir, "clock_fonts").apply { mkdirs() }.resolve("id_Font.ttf").writeText("font")
        File(context.filesDir, "item_icons").apply { mkdirs() }.resolve("icon.png").writeText("icon")
    }

    private suspend fun wipe() {
        database.backupDao().run {
            clearSettings(); clearHiddenApps(); clearFolders(); clearItemIcons(); clearSavedShortcuts(); clearPopups()
        }
        listOf(FavoritesFile, AppearanceFile).forEach { prefs(it).edit().clear().commit() }
        listOf("clock_fonts", "item_icons").forEach { File(context.filesDir, it).deleteRecursively() }
    }

    private data class State(
        val settings: List<LauncherSettingsEntity>, val hidden: List<HiddenAppEntity>, val folders: List<LauncherFolderEntity>,
        val icons: List<ItemIconEntity>, val shortcuts: List<SavedShortcutEntity>, val popups: List<AppPopupEntity>,
        val favorites: Map<String, *>, val appearance: Map<String, *>, val fonts: Map<String, String>, val iconFiles: Map<String, String>,
    )

    private suspend fun state() = database.backupDao().run {
        State(allSettings(), allHiddenApps(), allFolders(), allItemIcons(), allSavedShortcuts(), allPopups(),
            prefs(FavoritesFile).all, prefs(AppearanceFile).all, files("clock_fonts"), files("item_icons"))
    }

    private fun files(dir: String) = File(context.filesDir, dir).listFiles().orEmpty().associate { it.name to it.readText() }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
        }
    }.toByteArray()

    private fun rezip(bytes: ByteArray, manifest: (BackupManifest) -> BackupManifest): ByteArray {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(bytes.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val data = input.readBytes()
                entries += entry.name to if (entry.name == BackupArchive.MANIFEST)
                    manifest(requireNotNull(BackupManifest.decode(String(data)))).encode().toByteArray() else data
            }
        }
        return zip(*entries.toTypedArray())
    }

    /** Keeps the test's preferences and files apart from the installed app's own. */
    private class Sandbox(base: Context, private val tag: String, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("$tag-$name", mode)
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "nobackup").apply { mkdirs() }
    }

    private companion object {
        const val FavoritesFile = "grace_launcher_preferences"
        const val AppearanceFile = "launcher_appearance"
    }
}
