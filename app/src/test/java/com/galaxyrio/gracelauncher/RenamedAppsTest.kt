package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.HomeContentKey
import com.galaxyrio.gracelauncher.data.WidgetScreenKey
import com.galaxyrio.gracelauncher.data.isAppKey
import com.galaxyrio.gracelauncher.data.renamedAppKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenamedAppsTest {
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
}
