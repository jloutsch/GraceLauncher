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
        var created: Uri? = null
        try {
            val exporter = BackupExporter(context)
            val hash = exporter.contentHash()
            if (!shouldAutoExport(true, true, now, store.lastExportAtMillis, store.lastAutoHash, hash)) return
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            created = DocumentsContract.createDocument(resolver, parent, "application/zip", autoBackupName(LocalDate.now()))
                ?: throw SecurityException("createDocument returned null")
            val output = resolver.openOutputStream(created, "w") ?: throw FileNotFoundException("no output stream")
            output.use { exporter.export(it, labels) }
            val done = created
            created = null
            store.lastExportAtMillis = System.currentTimeMillis()
            store.lastAutoHash = hash
            prune(tree, done)
        } catch (error: Exception) {
            created?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            if (error is CancellationException) throw error
            Log.e("GraceAutoBackup", "automatic backup failed", error)
            store.lastFailureAtMillis = System.currentTimeMillis()
            store.lastFailureReason = if (error is SecurityException || error is FileNotFoundException || error is IllegalArgumentException)
                FolderNotAccessible else error.javaClass.simpleName
        }
    }

    // The backup is already written; a failed cleanup must not turn it into a failure.
    private fun prune(tree: Uri, newest: Uri) {
        try {
            val files = children(tree)
            autoFilesToDelete(files.map { it.second }).forEach { name ->
                files.firstOrNull { it.second == name && it.first != DocumentsContract.getDocumentId(newest) }
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
