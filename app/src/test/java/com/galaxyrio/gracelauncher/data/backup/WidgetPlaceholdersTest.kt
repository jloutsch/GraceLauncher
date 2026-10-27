package com.galaxyrio.gracelauncher.data.backup

import com.galaxyrio.gracelauncher.data.HomeContentKey
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.data.WidgetGridPlacement
import com.galaxyrio.gracelauncher.data.WidgetScreenKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPlaceholdersTest {
    private fun widget(id: Int, height: Int = 0, grid: WidgetGridPlacement? = null) =
        PopupItem("widget:$id", HomeLayout(widgetId = id, widgetProvider = "p/$id", widgetHeightDp = height), grid)

    private fun layout() = WidgetLayout(
        mapOf(
            HomeContentKey to listOf(PopupItem("app:one"), widget(41)),
            "folder:a" to listOf(widget(7)),
            WidgetScreenKey to listOf(widget(9, 300, WidgetGridPlacement(1, 2, 2, 3))),
        ),
        listOf("grace:calendar", "widget:41"),
    )

    @Test fun assignsPlaceholdersAcrossAllOwners() {
        val result = layout().toPlaceholders()
        val widgets = result.popups.values.flatten().mapNotNull { it.widget }
        assertTrue(widgets.none { it.widgetId >= 0 })
        assertEquals(setOf(-2, -3, -4), widgets.map { it.widgetId }.toSet())
        val homeId = result.popups.getValue(HomeContentKey).last().widget!!.widgetId
        assertEquals(listOf("grace:calendar", "widget:$homeId"), result.homeOrder)
        result.popups.values.flatten().filter { it.widget != null }
            .forEach { assertEquals("widget:${it.widget!!.widgetId}", it.key) }
        assertEquals(listOf(-2, -3, -4), result.pending().map { it.placeholderId })
        assertEquals(listOf(HomeContentKey, "folder:a", WidgetScreenKey).sorted(), result.pending().map { it.ownerKey })
        assertEquals("p/7", result.pending().first { it.ownerKey == "folder:a" }.provider)
    }

    @Test fun isIdempotentAndNumbersBelowExisting() {
        val once = layout().toPlaceholders()
        assertEquals(once, once.toPlaceholders())
        val mixed = once.copy(popups = once.popups + ("folder:b" to listOf(widget(12))))
        val result = mixed.toPlaceholders()
        assertEquals(-5, result.popups.getValue("folder:b").single().widget!!.widgetId)
        assertEquals(4, result.pending().size)
    }

    @Test fun replaceKeepsPlacementAndUpdatesOrder() {
        val base = layout().toPlaceholders()
        val screenId = base.popups.getValue(WidgetScreenKey).single().widget!!.widgetId
        val screen = base.replaceWidget(screenId, 105).popups.getValue(WidgetScreenKey).single()
        assertEquals(105, screen.widget!!.widgetId)
        assertEquals("widget:105", screen.key)
        assertEquals(300, screen.widget!!.widgetHeightDp)
        assertEquals(WidgetGridPlacement(1, 2, 2, 3), screen.grid)
        val homeId = base.popups.getValue(HomeContentKey).last().widget!!.widgetId
        val replaced = base.replaceWidget(homeId, 106)
        assertEquals(listOf("grace:calendar", "widget:106"), replaced.homeOrder)
        assertEquals("widget:106", replaced.popups.getValue(HomeContentKey).last().key)
        assertFalse(replaced.pending().any { it.placeholderId == homeId })
    }

    @Test fun dropRemovesItemAndOrderEntry() {
        val base = layout().toPlaceholders()
        val home = base.popups.getValue(HomeContentKey).last().widget!!.widgetId
        val result = base.dropWidget(home)
        assertEquals(listOf("app:one"), result.popups.getValue(HomeContentKey).map { it.key })
        assertEquals(listOf("grace:calendar"), result.homeOrder)
        assertFalse(result.pending().any { it.placeholderId == home })
    }

    @Test fun appEntriesUntouched() {
        val result = layout().toPlaceholders()
        assertEquals("app:one", result.popups.getValue(HomeContentKey).first().key)
        assertEquals(2, result.popups.getValue(HomeContentKey).size)
    }
}
