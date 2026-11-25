package com.galaxyrio.gracelauncher.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val BackupNameStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
private val BackupMimeTypes = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

@Composable
internal fun BackupSettings(actions: LauncherActions, onBack: () -> Unit, inProgress: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    BackHandler(enabled = inProgress) { }
    if (inProgress) AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        icon = { CircularProgressIndicator() },
        title = { Text(stringResource(R.string.backup_in_progress)) },
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            message = actions.exportBackup(uri).fold(
                onSuccess = { context.getString(R.string.backup_exported) },
                onFailure = { context.getString(R.string.backup_export_failed) + detail(it) },
            )
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    pendingRestore?.let { uri ->
        AlertDialog(onDismissRequest = { pendingRestore = null }, shape = RoundedCornerShape(40.dp),
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        actions.restoreBackup(uri).fold(
                            onSuccess = { },
                            onFailure = { message = context.getString(R.string.backup_restore_failed) + detail(it) },
                        )
                    }
                }, modifier = Modifier.testTag("backup_restore_confirm")) {
                    Text(stringResource(R.string.backup_restore_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } })
    }

    SettingsScaffold(stringResource(R.string.settings_backup), "settings_backup", { if (!inProgress) onBack() }) { padding ->
        SettingsList(padding) {
            item { Spacer(Modifier.height(24.dp)) }
            item {
                SettingsActionItem(stringResource(R.string.backup_export), stringResource(R.string.backup_export_summary),
                    0, 2, "backup_export", enabled = !inProgress) {
                    exportPicker.launch("grace-launcher-backup-${LocalDateTime.now().format(BackupNameStamp)}.zip")
                }
            }
            item {
                SettingsActionItem(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_summary),
                    1, 2, "backup_restore", enabled = !inProgress) { restorePicker.launch(BackupMimeTypes) }
            }
            message?.let { text ->
                item { Text(text, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp).testTag("backup_message")) }
            }
        }
    }
}

private fun detail(error: Throwable): String = error.message?.let { " $it" }.orEmpty()
