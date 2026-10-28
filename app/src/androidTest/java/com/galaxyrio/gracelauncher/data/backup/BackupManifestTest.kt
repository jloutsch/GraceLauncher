package com.galaxyrio.gracelauncher.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManifestTest {
    private val full = BackupManifest(
        formatVersion = 1, appVersionName = "1.2.3", appVersionCode = 123L, schemaVersion = 27,
        exportedAtMillis = 1_700_000_000_000L, sections = BackupSection.entries.toSet(),
        counts = BackupCounts(folders = 3, apps = 40, favorites = 5, widgets = 2),
        profiles = listOf(BackupProfile(10L, "work"), BackupProfile(11L, "private")),
    )

    @Test fun manifestRoundTrips() {
        assertEquals(full, BackupManifest.decode(full.encode()))
        assertNull(BackupManifest.decode("{}"))
        assertNull(BackupManifest.decode("not json"))
    }

    @Test fun unknownSectionsAreIgnored() {
        val json = full.encode().replace("\"settings\"", "\"settings\",\"from_the_future\"")
        assertEquals(full, BackupManifest.decode(json))
    }
}
