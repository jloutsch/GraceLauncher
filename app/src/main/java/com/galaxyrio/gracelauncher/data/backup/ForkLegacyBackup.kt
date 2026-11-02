package com.galaxyrio.gracelauncher.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.WeatherAppKeyField
import com.galaxyrio.gracelauncher.data.WidgetStackFoldersField
import com.galaxyrio.gracelauncher.data.withField
import org.json.JSONArray
import java.io.File

/**
 * Fork builds before 2026-11 used their own database version 30 (widget stacks, headphone apps and
 * the weather app as columns). The fork now uses upstream's schema and keeps those values in JSON.
 * A backup from an old build is rebuilt in the current schema before it is read.
 */
internal object ForkLegacyBackup {
    const val VERSION = 30

    fun isLegacy(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.version == VERSION && "isWidgetStack" in columns(db, "main", "folders")
        }
    }.getOrDefault(false)

    /** Replaces [file] with a database in the current schema holding the same data. */
    fun convert(context: Context, file: File) {
        val converted = File(file.parentFile, "${file.name}.converted")
        converted.delete()
        Room.databaseBuilder(context, LauncherDatabase::class.java, converted.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
            .also { it.openHelper.writableDatabase }.close()
        SQLiteDatabase.openDatabase(converted.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ATTACH DATABASE ? AS legacy", arrayOf(file.path))
            db.beginTransaction()
            try {
                tables(db).forEach { table ->
                    val shared = columns(db, "legacy", table).let { old -> columns(db, "main", table).filter { it in old } }
                    if (shared.isEmpty()) return@forEach
                    val list = shared.joinToString { "\"$it\"" }
                    db.execSQL("INSERT INTO main.\"$table\" ($list) SELECT $list FROM legacy.\"$table\"")
                }
                val stacks = db.rawQuery("SELECT id FROM legacy.folders WHERE isWidgetStack = 1", null)
                    .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
                db.rawQuery("SELECT clockStyleJson, shortcutsFoldersJson, weatherAppKey FROM legacy.launcher_settings WHERE id = 0", null).use { c ->
                    if (c.moveToFirst()) {
                        val clock = (c.getString(0) ?: "{}").withField(WeatherAppKeyField, c.getString(2))
                        val folders = (c.getString(1) ?: "{}")
                            .withField(WidgetStackFoldersField, if (stacks.isEmpty()) null else JSONArray(stacks.sorted()))
                        db.execSQL("UPDATE main.launcher_settings SET clockStyleJson = ?, shortcutsFoldersJson = ? WHERE id = 0",
                            arrayOf(clock, folders))
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.execSQL("DETACH DATABASE legacy")
        }
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        check(converted.renameTo(file)) { "Unable to replace ${file.name}" }
    }

    private fun tables(db: SQLiteDatabase): List<String> =
        db.rawQuery("SELECT name FROM main.sqlite_master WHERE type = 'table'", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }.filterNot { it.startsWith("sqlite_") || it == "room_master_table" || it == "android_metadata" }

    private fun columns(db: SQLiteDatabase, schema: String, table: String): Set<String> =
        db.rawQuery("PRAGMA $schema.table_info(\"$table\")", null).use { c ->
            val name = c.getColumnIndexOrThrow("name")
            buildSet { while (c.moveToNext()) add(c.getString(name)) }
        }
}
