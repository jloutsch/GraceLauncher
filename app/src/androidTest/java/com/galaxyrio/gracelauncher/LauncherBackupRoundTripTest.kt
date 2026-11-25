package com.galaxyrio.gracelauncher

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.HiddenAppEntity
import com.galaxyrio.gracelauncher.data.LauncherBackup
import com.galaxyrio.gracelauncher.data.LauncherBackupException
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolderEntity
import com.galaxyrio.gracelauncher.data.LauncherSettingsEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class LauncherBackupRoundTripTest {
    @get:Rule val files = TemporaryFolder()
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LauncherDatabase
    private lateinit var prefsName: String
    private lateinit var backup: LauncherBackup

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java).allowMainThreadQueries().build()
        prefsName = "backup_test_${UUID.randomUUID()}"
        backup = LauncherBackup(context, database, files.root, listOf(prefsName))
    }

    @After fun tearDown() {
        database.close()
        context.deleteSharedPreferences(prefsName)
    }

    private fun settings(themeColor: Int, homeLayoutJson: String? = null) = LauncherSettingsEntity(
        calendarAgenda = true, showBatteryPercentage = true, allowHapticFeedback = true,
        useDynamicColors = true, themeColor = themeColor, darkMode = "System", homeLayoutJson = homeLayoutJson)

    private fun writeFile(dir: String, name: String, bytes: ByteArray) =
        File(files.root, "$dir/$name").apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    private fun exportBytes(): ByteArray = ByteArrayOutputStream().also { out ->
        kotlinx.coroutines.runBlocking { backup.export(out) }
    }.toByteArray()

    private fun restore(bytes: ByteArray) = kotlinx.coroutines.runBlocking { backup.restore(ByteArrayInputStream(bytes)) }

    private fun archiveEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }.toByteArray()

    @Test fun restoresLayoutPreferencesAndFilesAndClearsWidgetIds() {
        val dao = database.settingsDao()
        kotlinx.coroutines.runBlocking {
            dao.saveSettings(settings(themeColor = 1, homeLayoutJson =
                """{"topOffsetDp":12,"widgetId":5,"widgetProvider":"p/w","widgetLabel":"Clock"}"""))
            dao.replaceHiddenApps(listOf(HiddenAppEntity("fixture.hidden/Main")))
            dao.upsertFolder(LauncherFolderEntity(id = "folder-1", name = "Work", placement = "None"))
        }
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            .putString("text", "kept").putBoolean("flag", true).putStringSet("set", setOf("a", "b")).commit()
        writeFile("item_icons", "icon-1.png", byteArrayOf(1, 2, 3))
        writeFile("clock_fonts", "Font_1.ttf", byteArrayOf(9, 8))

        val archive = exportBytes()

        val exported = JSONObject(String(archiveEntries(archive).getValue("database.json")))
        val layout = JSONObject(exported.getJSONArray("launcher_settings").getJSONObject(0).getString("homeLayoutJson"))
        assertEquals(-1, layout.getInt("widgetId"))
        assertFalse(layout.has("widgetProvider"))

        // Change everything the backup covers, then restore.
        kotlinx.coroutines.runBlocking {
            dao.saveSettings(settings(themeColor = 2))
            dao.replaceHiddenApps(emptyList())
            dao.deleteFolder("folder-1")
        }
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().putString("text", "changed").commit()
        File(files.root, "item_icons/icon-1.png").delete()
        writeFile("item_icons", "extra.png", byteArrayOf(7))

        restore(archive)

        kotlinx.coroutines.runBlocking {
            val restored = dao.readSettings()!!
            assertEquals(1, restored.themeColor)
            assertEquals(listOf("fixture.hidden/Main"), dao.readHiddenApps())
            assertEquals(listOf("folder-1"), dao.readFolders().map { it.id })
            assertEquals(-1, JSONObject(restored.homeLayoutJson!!).getInt("widgetId"))
            assertEquals(12.0, JSONObject(restored.homeLayoutJson!!).getDouble("topOffsetDp"), 0.0)
        }
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertEquals("kept", prefs.getString("text", null))
        assertTrue(prefs.getBoolean("flag", false))
        assertEquals(setOf("a", "b"), prefs.getStringSet("set", null))
        assertTrue(File(files.root, "item_icons/icon-1.png").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertFalse(File(files.root, "item_icons/extra.png").exists())
        assertTrue(File(files.root, "clock_fonts/Font_1.ttf").readBytes().contentEquals(byteArrayOf(9, 8)))
    }

    @Test fun rejectsAnotherDatabaseVersionWithoutChangingAnything() {
        kotlinx.coroutines.runBlocking {
            database.settingsDao().replaceHiddenApps(listOf(HiddenAppEntity("keep.me/Main")))
        }
        val version = database.openHelper.readableDatabase.version
        val archive = zipOf(mapOf(
            "manifest.json" to JSONObject().put("format", "grace-launcher-backup").put("formatVersion", 1)
                .put("databaseVersion", version + 1).toString().toByteArray(),
            "database.json" to "{}".toByteArray(),
            "preferences.json" to "{}".toByteArray(),
        ))
        assertThrows(LauncherBackupException::class.java) { restore(archive) }
        kotlinx.coroutines.runBlocking { assertEquals(listOf("keep.me/Main"), database.settingsDao().readHiddenApps()) }
    }

    @Test fun unknownColumnRejectsTheWholeRestoreBeforeAnyWrite() {
        kotlinx.coroutines.runBlocking {
            database.settingsDao().replaceHiddenApps(listOf(HiddenAppEntity("keep.me/Main")))
        }
        val version = database.openHelper.readableDatabase.version
        val tables = JSONObject()
        for (table in LauncherBackup.TABLES) tables.put(table, org.json.JSONArray())
        tables.put("hidden_apps", org.json.JSONArray().put(JSONObject().put("appKey", "new.app/Main").put("bogus", 1)))
        val preferences = JSONObject().put(prefsName, JSONObject())
        val archive = zipOf(mapOf(
            "manifest.json" to JSONObject().put("format", "grace-launcher-backup").put("formatVersion", 1)
                .put("databaseVersion", version).toString().toByteArray(),
            "database.json" to tables.toString().toByteArray(),
            "preferences.json" to preferences.toString().toByteArray(),
        ))
        assertThrows(LauncherBackupException::class.java) { restore(archive) }
        kotlinx.coroutines.runBlocking { assertEquals(listOf("keep.me/Main"), database.settingsDao().readHiddenApps()) }
    }

    @Test fun failedRestoreRollsBackToTheSetupItStartedFrom() {
        val dao = database.settingsDao()
        // The backup describes setup B.
        kotlinx.coroutines.runBlocking { dao.replaceHiddenApps(listOf(HiddenAppEntity("new.app/Main"))) }
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().putString("text", "B").commit()
        writeFile("item_icons", "icon-1.png", byteArrayOf(2))
        val archive = exportBytes()

        // The current setup is A. Restoring B fails after the database and files have been written.
        kotlinx.coroutines.runBlocking { dao.replaceHiddenApps(listOf(HiddenAppEntity("keep.me/Main"))) }
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().putString("text", "A").commit()
        writeFile("item_icons", "icon-1.png", byteArrayOf(1))
        val failing = LauncherBackup(context, database, files.root, listOf(prefsName)) { step ->
            if (step == "preferences") error("simulated failure")
        }
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { failing.restore(ByteArrayInputStream(archive)) }
        }

        kotlinx.coroutines.runBlocking { assertEquals(listOf("keep.me/Main"), dao.readHiddenApps()) }
        assertEquals("A", context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString("text", null))
        assertTrue(File(files.root, "item_icons/icon-1.png").readBytes().contentEquals(byteArrayOf(1)))
        assertFalse(File(files.root, LauncherBackup.SNAPSHOT_FILE_NAME).exists())
    }
}
