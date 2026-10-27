package com.galaxyrio.gracelauncher.data.backup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupTest {
    private val hour = 3_600_000L

    @Test fun dueOnlyAfter24hAndChange() {
        val last = 1_000_000_000L
        assertFalse(shouldAutoExport(true, true, last + 23 * hour + 59 * 60_000L, last, "a", "b"))
        assertFalse(shouldAutoExport(true, true, last + 25 * hour, last, "a", "a"))
        assertTrue(shouldAutoExport(true, true, last + 25 * hour, last, "a", "b"))
        assertTrue(shouldAutoExport(true, true, last, null, null, "b"))
        assertFalse(shouldAutoExport(false, true, last + 25 * hour, last, "a", "b"))
        assertFalse(shouldAutoExport(true, false, last + 25 * hour, last, "a", "b"))
    }

    @Test fun retentionKeepsNewestSevenAutoFiles() {
        val auto = (1..9).map { "gracelauncher-auto-2026-10-%02d.zip".format(it) }
        val others = listOf("gracelauncher-2026-10-01.zip", "notes.txt", "gracelauncher-auto-old.zip")
        assertEquals(auto.take(2).toSet(), autoFilesToDelete((others + auto).shuffled()).toSet())
        assertEquals(2, autoFilesToDelete(others + auto).size)
    }

    @Test fun retentionOrdersSuffixWithinSameDate() {
        val names = listOf("gracelauncher-auto-2026-10-05.zip", "gracelauncher-auto-2026-10-05 (1).zip", "gracelauncher-auto-2026-10-05 (2).zip")
        assertEquals(listOf("gracelauncher-auto-2026-10-05.zip"), autoFilesToDelete(names, keep = 2))
    }

    @Test fun namesUseIsoDate() {
        val date = LocalDate.of(2026, 10, 27)
        assertEquals("gracelauncher-auto-2026-10-27.zip", autoBackupName(date))
        assertEquals("gracelauncher-2026-10-27.zip", manualBackupName(date))
    }

    @Test fun stalePartFilesMatchOnlyAutoParts() {
        val names = listOf(
            "gracelauncher-auto-2026-10-05.zip.part", "gracelauncher-auto-2026-10-05 (1).zip.part",
            "gracelauncher-auto-2026-10-05.zip", "gracelauncher-2026-10-05.zip.part", "notes.part", "gracelauncher-auto-x.zip.part",
        )
        assertEquals(names.take(2), stalePartFiles(names))
    }
}
