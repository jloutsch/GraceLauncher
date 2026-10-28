package com.galaxyrio.gracelauncher.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.backup.BackupController
import com.galaxyrio.gracelauncher.data.backup.BackupProblem
import com.galaxyrio.gracelauncher.data.backup.BackupStatusStore
import com.galaxyrio.gracelauncher.data.backup.BackupTask
import com.galaxyrio.gracelauncher.data.backup.ImportResult
import com.galaxyrio.gracelauncher.data.backup.ImportSummary
import com.galaxyrio.gracelauncher.data.backup.manualBackupName
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DayMillis = 86_400_000L
private const val TreeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@Composable
fun BackupSettings(uiState: LauncherUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { BackupStatusStore(context) }
    val task by BackupController.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    // Re-read after each finished task and each change made here.
    val lastExport = remember(task, revision) { store.lastExportAtMillis }
    val autoEnabled = remember(task, revision) { store.autoEnabled }
    val folder = remember(task, revision) { store.autoFolderUri }
    val failureAt = remember(task, revision) { store.lastFailureAtMillis }
    val failureReason = remember(task, revision) { store.lastFailureReason }
    val folderName by produceState<String?>(null, folder) { value = folder?.let { withContext(Dispatchers.IO) { treeName(context, it) } } }
    // Without the app list an export has no labels and an import would report every app as missing.
    val ready = !uiState.isLoadingApps && !uiState.appLoadFailed
    val busy = task == BackupTask.Exporting || task == BackupTask.Importing
    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }
    var enableAfterPick by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) BackupController.export(context, uri, uiState.apps.associate { it.key to it.label })
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingImport = it }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val enable = enableAfterPick
        enableAfterPick = false
        if (uri == null || runCatching { context.contentResolver.takePersistableUriPermission(uri, TreeFlags) }.isFailure) return@rememberLauncherForActivityResult
        store.autoFolderUri?.takeIf { it != uri.toString() }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), TreeFlags) }
        }
        store.autoFolderUri = uri.toString()
        if (enable) store.autoEnabled = true
        revision++
    }

    SettingsScaffold(stringResource(R.string.settings_backup), "settings_backup", onBack) { padding ->
        SettingsList(padding) {
            item { Spacer(Modifier.height(24.dp)) }
            item {
                SettingsActionItem(stringResource(R.string.backup_export),
                    stringResource(if (task == BackupTask.Exporting) R.string.backup_exporting else R.string.backup_export_summary),
                    0, 2, "backup_export", enabled = ready && !busy, leading = { LauncherIcon(LauncherSymbol.Outbound) },
                ) { exportLauncher.launch(manualBackupName(LocalDate.now())) }
            }
            item {
                SettingsActionItem(stringResource(R.string.backup_import),
                    stringResource(if (task == BackupTask.Importing) R.string.backup_importing else R.string.backup_import_summary),
                    1, 2, "backup_import", enabled = ready && !busy, leading = { LauncherIcon(LauncherSymbol.History2) },
                ) { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
            }
            item {
                val days = lastExport?.let { ((System.currentTimeMillis() - it) / DayMillis).coerceAtLeast(0).toInt() }
                Text(
                    when (days) {
                        null -> stringResource(R.string.backup_last_exported_never)
                        0 -> stringResource(R.string.backup_last_exported_today)
                        else -> pluralStringResource(R.plurals.backup_last_exported_days, days, days)
                    },
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp).testTag("backup_last_exported"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
            item {
                SettingsToggleItem(stringResource(R.string.backup_auto), stringResource(R.string.backup_auto_summary),
                    autoEnabled, 0, 2, "backup_auto") { on ->
                    if (on && folder == null) { enableAfterPick = true; folderLauncher.launch(null) }
                    else { store.autoEnabled = on; revision++ }
                }
            }
            item {
                SettingsActionItem(stringResource(R.string.backup_folder),
                    if (folder == null) stringResource(R.string.backup_folder_none) else folderName.orEmpty(),
                    1, 2, "backup_folder", leading = { LauncherIcon(LauncherSymbol.Folder) },
                ) { enableAfterPick = false; folderLauncher.launch(null) }
            }
            if (failureAt != null && (lastExport == null || failureAt > lastExport)) item {
                val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(failureAt).atZone(ZoneId.systemDefault()))
                Text(stringResource(R.string.backup_auto_failed, date, failureReason.orEmpty()),
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp).testTag("backup_failure"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    BackupController.import(context, uri, uiState.apps.map { it.key }.toSet())
                }, Modifier.testTag("backup_import_confirm")) { Text(stringResource(R.string.backup_import_action)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.settings_cancel)) } })
    }
    when (val current = task) {
        is BackupTask.Imported -> ImportResultDialog(current.result, BackupController::dismiss)
        is BackupTask.ExportFailed -> AlertDialog(onDismissRequest = BackupController::dismiss,
            text = { Text(stringResource(R.string.backup_export_failed)) },
            confirmButton = { TextButton(onClick = BackupController::dismiss) { Text(stringResource(R.string.backup_ok)) } })
        else -> Unit
    }
}

@Composable
internal fun ImportResultDialog(result: ImportResult, onDismiss: () -> Unit) {
    val title = when (result) {
        is ImportResult.Success -> R.string.backup_summary_title
        is ImportResult.Rejected -> R.string.backup_rejected_title
        is ImportResult.Failed -> R.string.backup_import_failed_title
    }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("backup_result"),
        title = { Text(stringResource(title)) },
        text = {
            when (result) {
                is ImportResult.Success -> ImportSummaryText(result.summary)
                is ImportResult.Rejected -> Text(stringResource(when (result.problem) {
                    is BackupProblem.NewerFormat, is BackupProblem.NewerSchema -> R.string.backup_rejected_newer
                    BackupProblem.NotABackup -> R.string.backup_rejected_not_backup
                    BackupProblem.TooLarge -> R.string.backup_rejected_too_large
                    is BackupProblem.UnsafeEntry -> R.string.backup_rejected_unsafe
                }))
                is ImportResult.Failed -> Text(stringResource(
                    if (result.restored) R.string.backup_import_failed_restored else R.string.backup_import_failed_unrestored))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_ok)) } })
}

@Composable
private fun ImportSummaryText(summary: ImportSummary) {
    val restored = listOfNotNull(
        pluralStringResource(R.plurals.backup_summary_folders, summary.folders, summary.folders),
        pluralStringResource(R.plurals.backup_summary_apps, summary.apps, summary.apps),
        pluralStringResource(R.plurals.backup_summary_favorites, summary.favorites, summary.favorites),
        pluralStringResource(R.plurals.backup_summary_widgets, summary.widgets, summary.widgets),
        if (summary.settingsRestored) stringResource(R.string.backup_summary_settings) else null,
    ).joinToString(", ")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.backup_summary_restored, restored))
        if (summary.notInstalled.isNotEmpty())
            Text(stringResource(R.string.backup_summary_not_installed, summary.notInstalled.joinToString(", ")))
        if (summary.widgets > 0) Column {
            Text(stringResource(R.string.backup_summary_widgets_setup, summary.widgets))
            Text(stringResource(R.string.backup_summary_widgets_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (summary.widgetsMissingProvider.isNotEmpty())
            Text(stringResource(R.string.backup_summary_widgets_missing, summary.widgetsMissingProvider.joinToString(", ")))
    }
}

private fun treeName(context: Context, tree: String): String? = runCatching {
    val uri = Uri.parse(tree)
    val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
    context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull() ?: runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)).substringAfter(':') }.getOrNull()
