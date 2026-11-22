package com.galaxyrio.gracelauncher.data.backup

import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.util.Log
import com.galaxyrio.gracelauncher.data.FontFilesVersion
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BackupTask {
    data object Exporting : BackupTask
    data object Importing : BackupTask
    data object Exported : BackupTask
    data class ExportFailed(val error: Throwable) : BackupTask
    data class Imported(val result: ImportResult) : BackupTask
}

/** Process-wide, so leaving the settings page never cancels a running export or import. */
object BackupController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<BackupTask?>(null)
    val state: StateFlow<BackupTask?> = _state.asStateFlow()

    fun export(context: Context, target: Uri, labels: Map<String, String>) = start(BackupTask.Exporting) {
        val app = context.applicationContext
        try {
            val exporter = BackupExporter(app)
            val output = app.contentResolver.openOutputStream(target, "w") ?: throw FileNotFoundException("no output stream")
            val hash = output.use { exporter.exportWithHash(it, labels) }
            BackupStatusStore(app).recordExport(System.currentTimeMillis(), hash)
            BackupTask.Exported
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("GraceBackup", "export failed", error)
            // A half-written file under a valid backup name would fail later, at import time.
            runCatching { DocumentsContract.deleteDocument(app.contentResolver, target) }
            BackupTask.ExportFailed(error)
        }
    }

    fun import(context: Context, source: Uri, installedAppKeys: Set<String>) = start(BackupTask.Importing) {
        val app = context.applicationContext
        val result = try {
            val providers = AppWidgetManager.getInstance(app).getInstalledProvidersForProfile(Process.myUserHandle())
                .map { it.provider.flattenToString() }.toSet()
            val input = app.contentResolver.openInputStream(source) ?: throw FileNotFoundException("no input stream")
            input.use { BackupImporter(app).import(it, installedAppKeys, providers) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // The importer reports its own write failures; anything thrown here came before it changed data.
            ImportResult.Failed(error, restored = true)
        }
        if (result is ImportResult.Failed) Log.e("GraceBackup", "import failed, restored=${result.restored}", result.error)
        BackupTask.Imported(result)
    }

    /** Fork #7: at start, rolls back an import the process died during, and reports it as a failed import. */
    fun recoverInterruptedImport(context: Context) {
        val app = context.applicationContext
        if (!BackupImporter.wasInterrupted(app)) return
        start(BackupTask.Importing) {
            val restored = try { BackupImporter(app).recoverInterrupted() } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("GraceBackup", "interrupted import rollback failed", error); false
            }
            Log.w("GraceBackup", "rolled back an interrupted import, restored=$restored")
            BackupTask.Imported(ImportResult.Failed(IllegalStateException("import interrupted"), restored))
        }
    }

    /** Clears a finished result once the page has shown it; a running task stays. */
    fun dismiss() = _state.update { if (it == BackupTask.Exporting || it == BackupTask.Importing) it else null }

    private fun start(running: BackupTask, work: suspend () -> BackupTask): Boolean {
        while (true) {
            val current = _state.value
            if (current == BackupTask.Exporting || current == BackupTask.Importing) return false
            if (_state.compareAndSet(current, running)) break
        }
        scope.launch {
            var finished: BackupTask? = null
            try { finished = work() } finally {
                // Import and rollback both replace the imported font files.
                if (finished is BackupTask.Imported) FontFilesVersion.bump()
                _state.value = finished
            }
        }
        return true
    }
}
