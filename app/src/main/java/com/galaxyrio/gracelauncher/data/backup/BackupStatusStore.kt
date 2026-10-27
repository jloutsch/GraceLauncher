package com.galaxyrio.gracelauncher.data.backup

import android.content.Context
import androidx.core.content.edit

/** Kept out of the backup itself: the exporter copies only the two named preference files. */
class BackupStatusStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("backup_status", Context.MODE_PRIVATE)

    var lastExportAtMillis: Long?
        get() = long("last_export_at")
        set(value) = put("last_export_at", value)
    var autoEnabled: Boolean
        get() = prefs.getBoolean("auto_enabled", false)
        set(value) = prefs.edit { putBoolean("auto_enabled", value) }
    var autoFolderUri: String?
        get() = prefs.getString("auto_folder_uri", null)
        set(value) = prefs.edit { if (value == null) remove("auto_folder_uri") else putString("auto_folder_uri", value) }
    var lastAutoHash: String?
        get() = prefs.getString("last_auto_hash", null)
        set(value) = prefs.edit { if (value == null) remove("last_auto_hash") else putString("last_auto_hash", value) }
    var lastFailureAtMillis: Long?
        get() = long("last_failure_at")
        set(value) = put("last_failure_at", value)
    var lastFailureReason: String?
        get() = prefs.getString("last_failure_reason", null)
        set(value) = prefs.edit { if (value == null) remove("last_failure_reason") else putString("last_failure_reason", value) }

    private fun long(key: String) = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    private fun put(key: String, value: Long?) = prefs.edit { if (value == null) remove(key) else putLong(key, value) }
}
