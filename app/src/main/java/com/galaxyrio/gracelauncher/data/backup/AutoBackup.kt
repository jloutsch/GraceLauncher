package com.galaxyrio.gracelauncher.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.FileNotFoundException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

private const val DayMillis = 86_400_000L
private const val FolderNotAccessible = "folder not accessible"
private val AutoPartName = Regex("""^gracelauncher-auto-\d{4}-\d{2}-\d{2}( \(\d+\))?\.zip\.part$""")
private val AutoName = Regex("""^gracelauncher-auto-(\d{4}-\d{2}-\d{2})( \((\d+)\))?\.zip$""")

fun shouldAutoExport(
    enabled: Boolean, folderSet: Boolean, nowMillis: Long, lastSuccessMillis: Long?, lastHash: String?, currentHash: String,
): Boolean = enabled && folderSet && (lastSuccessMillis == null || nowMillis - lastSuccessMillis >= DayMillis) && currentHash != lastHash

fun manualBackupName(date: LocalDate) = "gracelauncher-$date.zip"

fun autoBackupName(date: LocalDate) = "gracelauncher-auto-$date.zip"

/** Newest first by date, then by the " (n)" suffix a provider adds on a name clash; everything past [keep] is returned. */
fun autoFilesToDelete(names: List<String>, keep: Int = 7): List<String> =
    names.mapNotNull { name -> AutoName.matchEntire(name)?.let { Triple(name, it.groupValues[1], it.groupValues[3].toIntOrNull() ?: 0) } }
        .sortedWith(compareByDescending<Triple<String, String, Int>> { it.second }.thenByDescending { it.third })
        .drop(keep).map { it.first }

/** Unfinished writes left by an earlier run; only exact auto-backup `.part` names qualify. */
fun stalePartFiles(names: List<String>): List<String> = names.filter { AutoPartName.matches(it) }

private class FolderError(cause: Throwable) : Exception(cause)

class AutoBackupRunner(context: Context) {
    private val context = context.applicationContext
    private val store = BackupStatusStore(context)

    suspend fun runIfDue(labels: Map<String, String>) {
        val folder = store.autoFolderUri
        val now = System.currentTimeMillis()
        // Reading the hash is the costly part, so skip it while the time and settings rules already say no.
        val probe = (store.lastAutoHash ?: "") + "!"
        if (!shouldAutoExport(store.autoEnabled, folder != null, now, store.lastExportAtMillis, store.lastAutoHash, probe)) return
        val tree = Uri.parse(folder ?: return)
        try {
            val exporter = BackupExporter(context)
            val hash = exporter.contentHash()
            if (!shouldAutoExport(true, true, now, store.lastExportAtMillis, store.lastAutoHash, hash)) return
            val parent = saf { DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)) }
            val existing = saf { children(tree) }
            stalePartFiles(existing.map { it.second }).forEach { name ->
                existing.firstOrNull { it.second == name }?.let { deleteQuietly(DocumentsContract.buildDocumentUriUsingTree(tree, it.first)) }
            }
            val name = autoBackupName(LocalDate.now())
            val written = writeViaPart(exporter, labels, parent, name) ?: writeDirect(exporter, labels, parent, name)
            store.recordExport(System.currentTimeMillis(), hash)
            prune(tree, written)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("GraceAutoBackup", "automatic backup failed", error)
            store.lastFailureAtMillis = System.currentTimeMillis()
            store.lastFailureReason = if (error is FolderError) FolderNotAccessible else error.javaClass.simpleName
        }
    }

    /** Writes `<name>.part` then renames it, so a killed process never leaves a partial zip under a valid name. Null when renaming is unsupported. */
    private suspend fun writeViaPart(exporter: BackupExporter, labels: Map<String, String>, parent: Uri, name: String): Uri? {
        val part = saf { DocumentsContract.createDocument(context.contentResolver, parent, "application/octet-stream", "$name.part") } ?: throw FolderError(SecurityException("createDocument returned null"))
        try {
            write(exporter, labels, part)
        } catch (error: Exception) {
            deleteQuietly(part)
            throw error
        }
        val renamed = try { DocumentsContract.renameDocument(context.contentResolver, part, name) } catch (error: Exception) {
            if (error is CancellationException) { deleteQuietly(part); throw error }
            null
        }
        if (renamed == null) deleteQuietly(part)
        return renamed
    }

    private suspend fun writeDirect(exporter: BackupExporter, labels: Map<String, String>, parent: Uri, name: String): Uri {
        val created = saf { DocumentsContract.createDocument(context.contentResolver, parent, "application/zip", name) } ?: throw FolderError(SecurityException("createDocument returned null"))
        try {
            write(exporter, labels, created)
        } catch (error: Exception) {
            deleteQuietly(created)
            throw error
        }
        return created
    }

    private suspend fun write(exporter: BackupExporter, labels: Map<String, String>, target: Uri) {
        val output = saf { context.contentResolver.openOutputStream(target, "w") } ?: throw FolderError(FileNotFoundException("no output stream"))
        output.use { exporter.export(it, labels) }
    }

    private fun deleteQuietly(uri: Uri) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }

    private inline fun <T> saf(block: () -> T): T = try { block() } catch (error: Exception) {
        if (error is CancellationException) throw error
        throw FolderError(error)
    }

    // The backup is already written; a failed cleanup must not turn it into a failure.
    private fun prune(tree: Uri, newest: Uri) {
        try {
            val files = children(tree)
            val newestId = DocumentsContract.getDocumentId(newest)
            autoFilesToDelete(files.map { it.second }).forEach { name ->
                files.firstOrNull { it.second == name && it.first != newestId }
                    ?.let { DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.first)) }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("GraceAutoBackup", "old backup cleanup failed", error)
        }
    }

    private fun children(tree: Uri): List<Pair<String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = context.contentResolver.query(uri, columns, null, null, null) ?: throw FileNotFoundException("folder missing")
        return cursor.use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }
    }
}
