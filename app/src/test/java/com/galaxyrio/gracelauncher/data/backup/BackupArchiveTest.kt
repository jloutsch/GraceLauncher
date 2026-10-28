package com.galaxyrio.gracelauncher.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zipBytes(block: ZipOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { ZipOutputStream(it).use(block) }.toByteArray()

    private fun ZipOutputStream.file(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name)); write(data); closeEntry()
    }

    @Test fun safeEntryNameRejectsTraversalAndAbsolutePaths() {
        assertNull(BackupArchive.safeEntryName("../x"))
        assertNull(BackupArchive.safeEntryName("/etc/x"))
        assertNull(BackupArchive.safeEntryName("fonts/../../x"))
        assertNull(BackupArchive.safeEntryName("fonts\\..\\x"))
        assertEquals("fonts/Inter.ttf", BackupArchive.safeEntryName("fonts/Inter.ttf"))
    }

    @Test fun extractWritesEntriesUnderTarget() {
        val into = tmp.newFolder("out")
        val zip = zipBytes {
            file(BackupArchive.MANIFEST, "{}".toByteArray())
            file(BackupArchive.DATABASE, byteArrayOf(1, 2, 3))
            putNextEntry(ZipEntry("fonts/")); closeEntry()
            file("fonts/X.ttf", byteArrayOf(9))
        }
        assertNull(BackupArchive.extract(ByteArrayInputStream(zip), into))
        assertEquals(3, File(into, "data/launcher.db").length().toInt())
        assertEquals(1, File(into, "fonts/X.ttf").length().toInt())
    }

    @Test fun extractStopsPastSizeCap() {
        val big = tmp.newFile("big.zip")
        val chunk = ByteArray(1 shl 20)
        val total = BackupArchive.MAX_TOTAL_BYTES + 1
        fun forEachChunk(block: (Int) -> Unit) { var left = total; while (left > 0) { val n = minOf(left, chunk.size.toLong()).toInt(); block(n); left -= n } }
        val crc = CRC32().also { c -> forEachChunk { c.update(chunk, 0, it) } }
        ZipOutputStream(big.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry(BackupArchive.MANIFEST).apply { method = ZipEntry.STORED; size = total; compressedSize = total; this.crc = crc.value })
            forEachChunk { z.write(chunk, 0, it) }
            z.closeEntry()
        }
        val into = tmp.newFolder("out")
        big.inputStream().buffered().use { assertEquals(BackupProblem.TooLarge, BackupArchive.extract(it, into)) }
    }

    @Test fun extractRejectsUnsafeEntry() {
        val into = tmp.newFolder("out")
        val zip = zipBytes { file("../evil", byteArrayOf(1)) }
        assertEquals(BackupProblem.UnsafeEntry("../evil"), BackupArchive.extract(ByteArrayInputStream(zip), into))
        assertFalse(File(into.parentFile, "evil").exists())
    }

    @Test fun extractRejectsNonZip() {
        val junk = ByteArray(1024).also { java.util.Random(1).nextBytes(it) }
        assertEquals(BackupProblem.NotABackup, BackupArchive.extract(ByteArrayInputStream(junk), tmp.newFolder("out")))
    }

    @Test fun extractRejectsZipWithoutManifest() {
        val zip = zipBytes { file("data/launcher.db", byteArrayOf(1)) }
        assertEquals(BackupProblem.NotABackup, BackupArchive.extract(ByteArrayInputStream(zip), tmp.newFolder("out")))
    }

    @Test fun compatibilityRules() {
        fun m(format: Int, schema: Int) = BackupManifest(format, "1", 1, schema, 0, emptySet(), BackupCounts(0, 0, 0, 0), emptyList())
        assertEquals(BackupProblem.NewerFormat(2), checkCompatible(m(2, 20), supportedSchema = 27))
        assertEquals(BackupProblem.NewerSchema(28), checkCompatible(m(1, 28), supportedSchema = 27))
        assertNull(checkCompatible(m(1, 20), supportedSchema = 27))
    }
}
