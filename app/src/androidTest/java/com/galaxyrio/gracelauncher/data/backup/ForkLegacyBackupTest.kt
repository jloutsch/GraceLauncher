package com.galaxyrio.gracelauncher.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolderEntity
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ForkLegacyBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "fork-legacy-${UUID.randomUUID()}.db")

    @After fun cleanUp() {
        listOf("", "-wal", "-shm", "-journal", ".converted").forEach { File(file.path + it).delete() }
    }

    /** Builds a database shaped like the fork's old version 30: fork columns present, version 30. */
    private fun legacyDatabase() = runBlocking {
        Room.databaseBuilder(context, LauncherDatabase::class.java, file.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build().apply {
                LauncherSettingsRepository(this).updateSettings(LauncherSettings())
                backupDao().insertFolders(listOf(
                    LauncherFolderEntity("ws", "Widgets", "Favorites", isAppStack = true),
                    LauncherFolderEntity("plain", "Plain", "Favorites")))
            }.close()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE folders ADD COLUMN isWidgetStack INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE launcher_settings ADD COLUMN headphoneAppsJson TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE launcher_settings ADD COLUMN weatherAppKey TEXT DEFAULT NULL")
            db.execSQL("UPDATE folders SET isWidgetStack = 1 WHERE id = 'ws'")
            db.execSQL("UPDATE launcher_settings SET clockStyleJson = '{\"marker\":7}', weatherAppKey = 'w/w.Weather', headphoneAppsJson = '{\"enabled\":true}'")
            db.version = ForkLegacyBackup.VERSION
        }
    }

    @Test fun oldForkDatabaseIsRebuiltInUpstreamSchemaWithItsForkValues() = runBlocking {
        legacyDatabase()
        assertTrue(ForkLegacyBackup.isLegacy(file))

        ForkLegacyBackup.convert(context, file)

        assertFalse(ForkLegacyBackup.isLegacy(file))
        val database = Room.databaseBuilder(context, LauncherDatabase::class.java, file.absolutePath)
            .addMigrations(*LauncherDatabase.Migrations).build()
        try {
            val snapshot = LauncherSettingsRepository(database).snapshots.first()
            assertEquals("w/w.Weather", snapshot.settings.weatherAppKey)
            val clockJson = database.openHelper.readableDatabase.query("SELECT clockStyleJson FROM launcher_settings").use { it.moveToFirst(); it.getString(0) }
            assertEquals(7, JSONObject(clockJson).getInt("marker"))
            assertEquals(setOf("ws"), snapshot.settings.widgetStackFolderIds)
            assertEquals(mapOf("ws" to true, "plain" to false), snapshot.folders.associate { it.id to it.isWidgetStack })
            assertEquals(LauncherDatabase.SCHEMA_VERSION, database.openHelper.readableDatabase.version)
        } finally {
            database.close()
        }
    }

    @Test fun currentDatabaseIsNotTreatedAsLegacy() {
        Room.databaseBuilder(context, LauncherDatabase::class.java, file.absolutePath).build()
            .also { it.openHelper.writableDatabase }.close()
        assertFalse(ForkLegacyBackup.isLegacy(file))
    }

    /** Upstream's own version 30 has no fork columns and must go through Room's migrations instead. */
    @Test fun upstreamVersion30IsNotTreatedAsLegacy() {
        Room.databaseBuilder(context, LauncherDatabase::class.java, file.absolutePath).build()
            .also { it.openHelper.writableDatabase }.close()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = ForkLegacyBackup.VERSION }
        assertFalse(ForkLegacyBackup.isLegacy(file))
    }
}
