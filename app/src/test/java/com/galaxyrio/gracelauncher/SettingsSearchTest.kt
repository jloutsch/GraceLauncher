package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.settings.SettingsPage
import com.galaxyrio.gracelauncher.ui.settings.SettingsSearchIndex
import com.galaxyrio.gracelauncher.ui.settings.searchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    // Unit tests have no resources, so a string's "label" is its resource name with spaces.
    private val names = R.string::class.java.fields.associate { it.getInt(null) to it.name.replace('_', ' ') }
    private fun search(query: String) = searchSettings(query) { names.getValue(it) }

    @Test fun aSettingRowIsFoundWithThePageThatHoldsIt() {
        val result = search("allow screen rotation").single()
        assertEquals(SettingsPage.Advanced, result.page)
        assertEquals(listOf("settings advanced"), result.path)
    }

    @Test fun aRowResultNamesTheRowToHighlightAndAPageResultNamesNone() {
        val row = search("allow screen rotation").single()
        assertEquals(row.title, row.row)
        assertEquals(null, search("tasks choose lists").single().row)
    }

    @Test fun aPageIsFoundWithThePathToItAndTheRouteThroughItsParents() {
        val result = search("tasks choose lists").single()
        assertEquals(SettingsPage.TaskLists, result.page)
        assertEquals(listOf("settings productivity", "settings calendar agenda", "tasks org"), result.path)
        assertEquals(listOf(SettingsPage.Productivity, SettingsPage.Calendar, SettingsPage.TasksOrg, SettingsPage.TaskLists), result.pages)
    }

    @Test fun aRowOnASubPageNamesTheSubPageInItsPath() {
        val result = search("weather hour show chart").single()
        assertEquals(SettingsPage.AgendaAppearance, result.page)
        assertEquals(listOf("settings themes", "settings agenda", "agenda hourly weather"), result.path)
    }

    @Test fun workProfileDoesNotOfferPrivateSpaceOnlyRows() {
        assertEquals(listOf(SettingsPage.PrivateSpace), search("private space password").map { it.page })
    }

    @Test fun exactMatchesComeBeforeLongerTitlesAndBlankFindsNothing() {
        assertEquals("settings weather", search("settings weather").first().title)
        assertTrue(search("  ").isEmpty())
        assertTrue(search("no such setting anywhere").isEmpty())
    }

    @Test fun everyIndexedPageReachesTheSettingsHome() {
        SettingsSearchIndex.pages.keys.forEach { page ->
            val seen = mutableSetOf<SettingsPage>()
            var current: SettingsPage? = page
            while (current != null && current != SettingsPage.Root) {
                assertTrue("$page loops through $current", seen.add(current))
                current = SettingsSearchIndex.pages[current]?.second
            }
            assertEquals("$page has no path to the settings home", SettingsPage.Root, current)
        }
    }
}
