package com.galaxyrio.gracelauncher.data.backup

import com.galaxyrio.gracelauncher.data.AgendaWidgetDraftKey
import com.galaxyrio.gracelauncher.data.AgendaWidgetKey
import com.galaxyrio.gracelauncher.data.renamedAppKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupKeysTest {
    @Test fun missingKeepsOrderAndIgnoresNonApps() {
        val referenced = listOf("a/a.Main", "folder:x", "b/b.Main", "widget:-2", "a/a.Main")
        assertEquals(listOf("b/b.Main"), missingAppKeys(referenced, setOf("a/a.Main")))
    }

    @Test fun agendaWidgetIsNeverTreatedAsAMissingOrRenamedApp() {
        // Regression: the fork's own key check once moved the agenda widget onto Grace Settings.
        val settings = "com.galaxyrio.gracelauncher/com.galaxyrio.gracelauncher.SettingsActivity"
        val saved = listOf(AgendaWidgetKey, AgendaWidgetDraftKey)
        assertEquals(emptyList<String>(), missingAppKeys(saved, setOf(settings)))
        assertEquals(emptyMap<String, String>(), renamedAppKeys(saved, listOf(settings)))
    }

    @Test fun profileSerialRemapped() {
        val serials = mapOf(10L to 13L)
        assertEquals("profile:13:p/p.M", remapProfileKey("profile:10:p/p.M", serials))
        assertEquals("profile:13:shortcut:p/id", remapProfileKey("profile:10:shortcut:p/id", serials))
        assertEquals("profile:11:p/p.M", remapProfileKey("profile:11:p/p.M", serials))
        assertEquals("p/p.M", remapProfileKey("p/p.M", serials))
    }
}
