package com.galaxyrio.gracelauncher.data.backup

import com.galaxyrio.gracelauncher.data.HomeContentKey
import com.galaxyrio.gracelauncher.data.WidgetScreenKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupKeysTest {
    @Test fun missingKeepsOrderAndIgnoresNonApps() {
        val referenced = listOf("a/a.Main", "folder:x", "b/b.Main", "widget:-2", "a/a.Main")
        assertEquals(listOf("b/b.Main"), missingAppKeys(referenced, setOf("a/a.Main")))
    }

    @Test fun appKeysExcludeEverythingElse() {
        assertTrue(isAppKey("a/a.Main"))
        assertTrue(isAppKey("profile:10:a/a.Main"))
        listOf(
            "folder:x", "widget:-2", "grace:clock", HomeContentKey, WidgetScreenKey,
            "shortcut:a/id%201", "profile:10:shortcut:a/id%201", "plain", "",
        ).forEach { assertFalse(it, isAppKey(it)) }
    }

    @Test fun renamedAppsFollowTheOnlyActivityLeftInTheirPackageAndProfile() {
        val installed = listOf("d/d.New", "two/two.A", "two/two.B", "profile:10:d/d.Work", "kept/kept.Main")
        val saved = listOf("d/d.Old", "two/two.Old", "profile:10:d/d.Old", "profile:11:d/d.Old", "gone/gone.Main",
            "kept/kept.Main", "folder:x", "d/d.Old")
        // Several activities, another profile, or an uninstalled package stay as they were rather than guessing.
        assertEquals(mapOf("d/d.Old" to "d/d.New", "profile:10:d/d.Old" to "profile:10:d/d.Work"), renamedAppKeys(saved, installed))
    }

    @Test fun profileSerialRemapped() {
        val serials = mapOf(10L to 13L)
        assertEquals("profile:13:p/p.M", remapProfileKey("profile:10:p/p.M", serials))
        assertEquals("profile:13:shortcut:p/id", remapProfileKey("profile:10:shortcut:p/id", serials))
        assertEquals("profile:11:p/p.M", remapProfileKey("profile:11:p/p.M", serials))
        assertEquals("p/p.M", remapProfileKey("p/p.M", serials))
    }
}
