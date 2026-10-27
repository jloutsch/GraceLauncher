package com.galaxyrio.gracelauncher.data.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object BackupArchive {
    const val MANIFEST = "manifest.json"
    const val DATABASE = "data/launcher.db"
    const val PREFERENCES = "data/preferences.json"
    const val LABELS = "data/labels.json"
    const val FONTS_DIR = "fonts/"
    const val ICONS_DIR = "icons/"
    const val MAX_TOTAL_BYTES = 268_435_456L

    fun safeEntryName(name: String): String? {
        val n = name.replace('\\', '/')
        if (n.isEmpty() || n.startsWith("/") || '\u0000' in n) return null
        if (n.split('/').any { it == ".." }) return null
        return n
    }

    fun extract(input: InputStream, into: File): BackupProblem? {
        val root = into.canonicalFile
        var total = 0L
        val buf = ByteArray(64 * 1024)
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = safeEntryName(entry.name) ?: return BackupProblem.UnsafeEntry(entry.name)
                    val target = File(root, name).canonicalFile
                    if (target != root && !target.path.startsWith(root.path + File.separator)) return BackupProblem.UnsafeEntry(entry.name)
                    if (entry.isDirectory) { target.mkdirs(); continue }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_TOTAL_BYTES) return BackupProblem.TooLarge
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
        } catch (_: ZipException) {
            return BackupProblem.NotABackup
        } catch (_: IOException) {
            return BackupProblem.NotABackup
        }
        return if (File(root, MANIFEST).isFile) null else BackupProblem.NotABackup
    }
}
