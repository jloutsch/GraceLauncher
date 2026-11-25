package com.galaxyrio.gracelauncher.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

class LauncherBackupException(message: String) : IOException(message)

/**
 * One zip holding the launcher's layout, settings and appearance preferences, saved shortcut
 * references, plus the icon and font files they reference. Widget IDs are cleared on export,
 * so restored widget slots start empty. The database version must match exactly, since rows
 * are restored without migration. A restore that fails part-way is rolled back to the setup
 * it started from.
 */
class LauncherBackup(
    private val context: Context,
    private val database: LauncherDatabase,
    private val filesRoot: File = context.filesDir,
    private val preferenceNames: List<String> = listOf(LauncherPreferences.FILE_NAME, FavoritesStore.FILE_NAME),
    /** Test seam: called after each restore step; a throw simulates a failure at that step. */
    private val afterStep: (String) -> Unit = {},
) {
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) { writeArchive(output, scrubWidgets = true) }

    private fun writeArchive(output: OutputStream, scrubWidgets: Boolean) {
        ZipOutputStream(output).use { zip ->
            zip.writeEntry(MANIFEST, JSONObject()
                .put("format", FORMAT)
                .put("formatVersion", FORMAT_VERSION)
                .put("databaseVersion", database.openHelper.readableDatabase.version)
                .put("createdAt", Instant.now().toString())
                .toString().toByteArray())
            zip.writeEntry(DATABASE, databaseJson(scrubWidgets).toString().toByteArray())
            zip.writeEntry(PREFERENCES, preferencesJson().toString().toByteArray())
            for (dir in FILE_DIRS) {
                File(filesRoot, dir).listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
                    .forEach { zip.writeFile("files/$dir/${it.name}", it) }
            }
        }
    }

    /**
     * Everything is validated before the first write. A snapshot of the current setup is taken
     * next; if any write step fails, the snapshot is replayed so the launcher keeps its old setup.
     */
    suspend fun restore(input: InputStream) = withContext(Dispatchers.IO) {
        val staging = newStagingDir()
        val snapshot = File(filesRoot, SNAPSHOT_FILE_NAME).let {
            // Never overwrite a recovery snapshot retained after an earlier rollback failure.
            if (it.exists()) File.createTempFile("pre_restore_snapshot_", ".zip", filesRoot) else it
        }
        var restoreStarted = false
        try {
            val plan = plan(input, staging)
            currentCoroutineContext().ensureActive()
            FileOutputStream(snapshot).use { writeArchive(it, scrubWidgets = false) }
            currentCoroutineContext().ensureActive()
            restoreStarted = true
            applyBackupWithRollback(
                apply = { applyPlan(plan) },
                rollback = { rollBack(snapshot) },
                discardSnapshot = { snapshot.delete() },
            )
        } finally {
            if (!restoreStarted) snapshot.delete()
            staging.deleteRecursively()
        }
    }

    private suspend fun rollBack(snapshot: File) {
        val staging = newStagingDir()
        try {
            FileInputStream(snapshot).use { applyPlan(plan(it, staging), reportSteps = false) }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun newStagingDir(): File = File(filesRoot, "$STAGING_PREFIX${System.nanoTime()}").apply { mkdirs() }

    private fun plan(input: InputStream, staging: File): Plan = try {
        val archive = readArchive(input, staging)
        Plan(planRows(archive.database), planPreferences(archive.preferences), staging)
    } catch (e: JSONException) {
        throw LauncherBackupException("The backup file is damaged.")
    }

    private suspend fun applyPlan(plan: Plan, reportSteps: Boolean = true) {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            for (table in TABLES) db.delete(table, null, null)
            for ((table, values) in plan.rows) values.forEach { db.insert(table, SQLiteDatabase.CONFLICT_ABORT, it) }
        }
        if (reportSteps) afterStep("database")
        for (dir in FILE_DIRS) {
            val target = File(filesRoot, dir)
            target.deleteRecursively()
            if (!File(plan.staging, dir).renameTo(target)) throw LauncherBackupException("Could not place $dir files.")
        }
        if (reportSteps) afterStep("files")
        for ((name, values) in plan.preferences) {
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            if (!editor.commit()) throw LauncherBackupException("Could not write preferences.")
        }
        if (reportSteps) afterStep("preferences")
    }

    private fun databaseJson(scrubWidgets: Boolean): JSONObject {
        val db = database.openHelper.readableDatabase
        return JSONObject().also { root ->
            for (table in TABLES) {
                root.put(table, JSONArray().also { rows ->
                    db.query("SELECT * FROM $table").use { cursor ->
                        while (cursor.moveToNext()) {
                            rows.put(JSONObject().also { row ->
                                for (i in 0 until cursor.columnCount) row.put(cursor.getColumnName(i), cellValue(cursor, i, scrubWidgets))
                            })
                        }
                    }
                })
            }
        }
    }

    private fun cellValue(cursor: Cursor, index: Int, scrubWidgets: Boolean): Any {
        val column = cursor.getColumnName(index)
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(index).let { if (scrubWidgets && column.endsWith("Json")) scrubBackupJson(it) else it }
            else -> throw LauncherBackupException("Unsupported value in $column.")
        }
    }

    private fun preferencesJson(): JSONObject = JSONObject().also { root ->
        for (name in preferenceNames) {
            root.put(name, JSONObject().also { node ->
                for ((key, value) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                    node.put(key, encodePreference(value))
                }
            })
        }
    }

    private fun encodePreference(value: Any?): JSONObject = when (value) {
        is Boolean -> typed("boolean", value)
        is Int -> typed("int", value)
        is Long -> typed("long", value)
        is Float -> typed("float", value.toDouble())
        is String -> typed("string", value)
        is Set<*> -> typed("stringSet", JSONArray(value.filterIsInstance<String>()))
        else -> throw LauncherBackupException("Unsupported preference value.")
    }

    private fun typed(type: String, value: Any): JSONObject = JSONObject().put("type", type).put("value", value)

    private class Archive(val database: JSONObject, val preferences: JSONObject)

    private class Plan(val rows: List<Pair<String, List<ContentValues>>>,
        val preferences: Map<String, Map<String, Any>>, val staging: File)

    private fun readArchive(input: InputStream, staging: File): Archive {
        FILE_DIRS.forEach { File(staging, it).mkdirs() }
        val json = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name in JSON_ENTRIES -> {
                        if (json.containsKey(name)) throw LauncherBackupException("Duplicate entry $name.")
                        json[name] = ByteArrayOutputStream().also { zip.copyBounded(it, MAX_JSON_BYTES) }.toByteArray()
                    }
                    isBackupFileEntry(name) -> {
                        val parts = name.split('/')
                        val target = File(staging, "${parts[1]}/${parts[2]}")
                        if (target.exists()) throw LauncherBackupException("Duplicate file ${parts[2]}.")
                        target.outputStream().use { total += zip.copyBounded(it, MAX_FILE_BYTES) }
                    }
                    else -> throw LauncherBackupException("Unexpected entry in backup.")
                }
                if (total > MAX_TOTAL_BYTES) throw LauncherBackupException("The backup is too large.")
            }
        }
        val manifest = JSONObject(String(json[MANIFEST] ?: throw LauncherBackupException("Not a launcher backup.")))
        if (manifest.optString("format") != FORMAT) throw LauncherBackupException("Not a launcher backup.")
        if (manifest.optInt("formatVersion", -1) != FORMAT_VERSION) {
            throw LauncherBackupException("This backup uses an unsupported format version.")
        }
        val expected = database.openHelper.readableDatabase.version
        if (manifest.optInt("databaseVersion", -1) != expected) {
            throw LauncherBackupException("This backup was made by a different launcher version and cannot be restored.")
        }
        return Archive(
            JSONObject(String(json[DATABASE] ?: throw LauncherBackupException("Backup is incomplete."))),
            JSONObject(String(json[PREFERENCES] ?: throw LauncherBackupException("Backup is incomplete."))),
        )
    }

    private fun planRows(root: JSONObject): List<Pair<String, List<ContentValues>>> {
        val db = database.openHelper.readableDatabase
        return TABLES.map { table ->
            val columns = db.query("PRAGMA table_info($table)").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
            val rows = root.optJSONArray(table) ?: throw LauncherBackupException("Backup is missing $table.")
            table to List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                ContentValues().also { values ->
                    for (column in row.keys()) {
                        if (column !in columns) throw LauncherBackupException("Unknown column $table.$column.")
                        when {
                            row.isNull(column) -> values.putNull(column)
                            else -> when (val value = row.get(column)) {
                                is Long -> values.put(column, value)
                                is Int -> values.put(column, value.toLong())
                                is Double -> values.put(column, value)
                                is String -> values.put(column, value)
                                else -> throw LauncherBackupException("Unsupported value in $table.$column.")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun planPreferences(root: JSONObject): Map<String, Map<String, Any>> {
        if (root.keys().asSequence().toSet() != preferenceNames.toSet()) {
            throw LauncherBackupException("Backup preferences do not match this launcher.")
        }
        return preferenceNames.associateWith { name ->
            val node = root.getJSONObject(name)
            node.keys().asSequence().associateWith { key -> decodePreference(node.getJSONObject(key)) }
        }
    }

    private fun decodePreference(node: JSONObject): Any = when (node.getString("type")) {
        "boolean" -> node.getBoolean("value")
        "int" -> node.getInt("value")
        "long" -> node.getLong("value")
        "float" -> node.getDouble("value").toFloat()
        "string" -> node.getString("value")
        "stringSet" -> node.getJSONArray("value").let { array -> List(array.length()) { array.getString(it) }.toSet() }
        else -> throw LauncherBackupException("Unknown preference type.")
    }

    companion object {
        const val FORMAT = "grace-launcher-backup"
        const val FORMAT_VERSION = 1
        const val MANIFEST = "manifest.json"
        const val DATABASE = "database.json"
        const val PREFERENCES = "preferences.json"
        val TABLES = listOf("launcher_settings", "hidden_apps", "folders", "item_icons", "app_popups", "saved_shortcuts")
        val FILE_DIRS = listOf("item_icons", "clock_fonts")
        private val JSON_ENTRIES = setOf(MANIFEST, DATABASE, PREFERENCES)
        private const val STAGING_PREFIX = "backup_staging_"
        const val SNAPSHOT_FILE_NAME = "pre_restore_snapshot.zip"
        private const val MAX_JSON_BYTES = 16L * 1024 * 1024
        private const val MAX_FILE_BYTES = 64L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    }
}

/** Only `files/<item_icons|clock_fonts>/<plain name>` entries are accepted; no nested or parent paths. */
internal fun isBackupFileEntry(name: String): Boolean {
    val parts = name.split('/')
    return parts.size == 3 && parts[0] == "files" && parts[1] in LauncherBackup.FILE_DIRS &&
        parts[2].isNotEmpty() && parts[2] != "." && parts[2] != ".." && parts[2].length <= 255 &&
        '\\' !in parts[2]
}

/** Clears widget placements in any JSON column: IDs are per device, so slots restore empty. */
internal fun clearWidgetIds(node: Any?) {
    when (node) {
        is JSONObject -> {
            if (node.has("widgetId")) {
                node.put("widgetId", -1)
                node.remove("widgetProvider")
                node.remove("widgetLabel")
            }
            for (key in node.keys()) clearWidgetIds(node.opt(key))
        }
        is JSONArray -> for (index in 0 until node.length()) clearWidgetIds(node.opt(index))
    }
}

internal fun scrubBackupJson(text: String): String = runCatching {
    when (val node = JSONTokener(text).nextValue()) {
        is JSONObject -> node.also(::clearWidgetIds).toString()
        is JSONArray -> node.also(::clearWidgetIds).toString()
        else -> text
    }
}.getOrDefault(text)

private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(bytes)
    closeEntry()
}

private fun ZipOutputStream.writeFile(name: String, file: File) {
    putNextEntry(ZipEntry(name))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}

private fun InputStream.copyBounded(output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(8192)
    var copied = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        copied += count
        if (copied > limit) throw LauncherBackupException("A backup entry is too large.")
        output.write(buffer, 0, count)
    }
    return copied
}
