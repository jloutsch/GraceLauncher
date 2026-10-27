package com.galaxyrio.gracelauncher.data.backup

import org.json.JSONArray
import org.json.JSONObject

const val BACKUP_FORMAT_VERSION = 1

enum class BackupSection(val id: String) {
    Settings("settings"), HiddenApps("hidden_apps"), Folders("folders"), ItemIcons("item_icons"),
    SavedShortcuts("saved_shortcuts"), Popups("app_popups"), Favorites("favorites"),
    Appearance("appearance"), Fonts("fonts"), Icons("icons"),
}

data class BackupCounts(val folders: Int, val apps: Int, val favorites: Int, val widgets: Int)

data class BackupProfile(val serial: Long, val type: String)

data class BackupManifest(
    val formatVersion: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val sections: Set<BackupSection>,
    val counts: BackupCounts,
    val profiles: List<BackupProfile>,
) {
    fun encode(): String = JSONObject()
        .put("formatVersion", formatVersion)
        .put("appVersionName", appVersionName)
        .put("appVersionCode", appVersionCode)
        .put("schemaVersion", schemaVersion)
        .put("exportedAtMillis", exportedAtMillis)
        .put("sections", JSONArray(sections.map { it.id }))
        .put("counts", JSONObject().put("folders", counts.folders).put("apps", counts.apps)
            .put("favorites", counts.favorites).put("widgets", counts.widgets))
        .put("profiles", JSONArray(profiles.map { JSONObject().put("serial", it.serial).put("type", it.type) }))
        .toString()

    companion object {
        fun decode(json: String): BackupManifest? = runCatching {
            val o = JSONObject(json)
            val c = o.getJSONObject("counts")
            val sectionIds = o.getJSONArray("sections").let { a -> (0 until a.length()).map { a.getString(it) } }
            val profiles = o.getJSONArray("profiles").let { a ->
                (0 until a.length()).map { a.getJSONObject(it).let { p -> BackupProfile(p.getLong("serial"), p.getString("type")) } }
            }
            BackupManifest(
                formatVersion = o.getInt("formatVersion"),
                appVersionName = o.getString("appVersionName"),
                appVersionCode = o.getLong("appVersionCode"),
                schemaVersion = o.getInt("schemaVersion"),
                exportedAtMillis = o.getLong("exportedAtMillis"),
                sections = BackupSection.entries.filter { it.id in sectionIds }.toSet(),
                counts = BackupCounts(c.getInt("folders"), c.getInt("apps"), c.getInt("favorites"), c.getInt("widgets")),
                profiles = profiles,
            )
        }.getOrNull()
    }
}

sealed interface BackupProblem {
    data class NewerFormat(val found: Int) : BackupProblem
    data class NewerSchema(val found: Int) : BackupProblem
    data object NotABackup : BackupProblem
    data object TooLarge : BackupProblem
    data class UnsafeEntry(val name: String) : BackupProblem
}

fun checkCompatible(
    manifest: BackupManifest,
    supportedFormat: Int = BACKUP_FORMAT_VERSION,
    supportedSchema: Int,
): BackupProblem? = when {
    manifest.formatVersion > supportedFormat -> BackupProblem.NewerFormat(manifest.formatVersion)
    manifest.schemaVersion > supportedSchema -> BackupProblem.NewerSchema(manifest.schemaVersion)
    else -> null
}
