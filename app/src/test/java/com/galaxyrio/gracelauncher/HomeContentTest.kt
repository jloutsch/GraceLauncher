package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class HomeContentTest {
    private val clockCalendar = GraceWidget.ClockCalendar.key
    private val media = GraceWidget.Media.key
    private val baseline = GraceWidget.Baseline.key

    @Test fun legacyLayoutKeepsItsWidgetAndFavoriteOrder() {
        val settings = LauncherSettings(homeLayout = HomeLayout(widgetId = 41, widgetHeightDp = 240))
        val items = orderedHomeItems(settings, listOf("app:a", "folder:b"), emptyList())
        assertEquals(listOf(clockCalendar, baseline, "widget:41", media, "app:a", "folder:b"), items.map { it.key })
        assertEquals(240, items.first { it.key == "widget:41" }.widget?.widgetHeightDp)
        assertEquals(legacyHomeOrder(settings), legacyHomeOrder(settings.copy(calendarAboveClock = true)))
    }

    @Test fun mixedOrderCanInterleaveAllKindsAndMultipleWidgets() {
        val keys = listOf("widget:2", "app:a", clockCalendar, "folder:b", baseline, "widget:1", media)
        val settings = LauncherSettings(homeLayout = HomeLayout(homeOrder = keys))
        val widgets = listOf(PopupItem("widget:1", HomeLayout(widgetId = 1)), PopupItem("widget:2", HomeLayout(widgetId = 2)))
        assertEquals(keys, orderedHomeItems(settings, listOf("app:a", "folder:b"), widgets).map { it.key })
    }

    @Test fun graceMembershipAndBannersUseTheSameFlagsAndReenableAtTop() {
        var settings = LauncherSettings(homeLayout = HomeLayout(homeOrder = listOf("app:a", baseline, clockCalendar, media)))
        for (widget in GraceWidget.entries) {
            val disabled = synchronizeGraceWidgetOrder(settings, widget.setEnabled(settings, false))
            assertFalse(widget.enabled(disabled))
            assertFalse(orderedHomeItems(disabled, listOf("app:a"), emptyList()).any { it.key == widget.key })
            settings = synchronizeGraceWidgetOrder(disabled, widget.setEnabled(disabled, true))
            assertTrue(widget.enabled(settings))
            assertEquals(widget.key, orderedHomeItems(settings, listOf("app:a"), emptyList()).first().key)
        }
    }

    @Test fun oldClockAndCalendarSlotsCollapseAtTheFirstOccurrence() {
        val settings = LauncherSettings(homeLayout = HomeLayout(homeOrder = listOf(
            "app:a", "grace:calendar", "widget:1", baseline, "grace:clock", "folder:b", clockCalendar, media,
        )))
        assertEquals(listOf("app:a", clockCalendar, "widget:1", baseline, "folder:b", media),
            orderedHomeItems(settings, listOf("app:a", "folder:b"), listOf(PopupItem("widget:1", HomeLayout(widgetId = 1)))).map { it.key })
        assertEquals(GraceWidget.ClockCalendar, GraceWidget.fromKey("grace:clock"))
        assertEquals(GraceWidget.ClockCalendar, GraceWidget.fromKey("grace:calendar"))
        assertEquals(GraceWidget.ClockCalendar, GraceWidget.fromKey(clockCalendar))
    }

    @Test fun combinedMasterPreservesPartsAndRestoresDefaultsOnlyWhenAllAreOff() {
        val widget = GraceWidget.ClockCalendar
        val weatherOnly = LauncherSettings(clockEnabled = false, calendarAgenda = false, weatherEnabled = true)
        assertTrue(widget.enabled(weatherOnly))
        val hidden = widget.setEnabled(weatherOnly, false)
        assertFalse(widget.enabled(hidden))
        assertFalse(hidden.clockEnabled)
        assertFalse(hidden.calendarAgenda)
        assertTrue(hidden.weatherEnabled)
        assertEquals(weatherOnly, widget.setEnabled(hidden, true))

        val empty = weatherOnly.copy(weatherEnabled = false)
        assertFalse(widget.enabled(empty))
        val restored = widget.setEnabled(empty, true)
        assertTrue(restored.clockEnabled)
        assertTrue(restored.calendarAgenda)
        assertFalse(restored.weatherEnabled)
        assertTrue(widget.enabled(restored))
    }

    @Test fun enablingAnyPartRestoresHiddenMasterAndAddsOnlyOneCombinedSlot() {
        val hidden = LauncherSettings(clockEnabled = false, calendarAgenda = false, weatherEnabled = false,
            homeLayout = HomeLayout(clockCalendarEnabled = false,
                homeOrder = listOf("app:a", "grace:calendar", baseline, "grace:clock", media)))
        listOf(hidden.copy(clockEnabled = true), hidden.copy(calendarAgenda = true), hidden.copy(weatherEnabled = true)).forEach { next ->
            val activated = synchronizeGraceWidgetOrder(hidden, next)
            assertTrue(activated.homeLayout.clockCalendarEnabled)
            assertEquals(listOf(clockCalendar, "app:a", baseline, media), activated.homeLayout.homeOrder)
            assertEquals(next.clockEnabled, activated.clockEnabled)
            assertEquals(next.calendarAgenda, activated.calendarAgenda)
            assertEquals(next.weatherEnabled, activated.weatherEnabled)
        }
        val clockOnly = hidden.copy(clockEnabled = true)
        val activatedCalendar = synchronizeGraceWidgetOrder(clockOnly, clockOnly.copy(calendarAgenda = true))
        assertTrue(activatedCalendar.homeLayout.clockCalendarEnabled)
        val unaffected = synchronizeGraceWidgetOrder(hidden, hidden.copy(showBatteryPercentage = false))
        assertFalse(unaffected.homeLayout.clockCalendarEnabled)
        assertEquals(listOf("app:a", clockCalendar, baseline, media), unaffected.homeLayout.homeOrder)
    }

    @Test fun reorderingRetainsUnavailableSlotsWithoutDuplicatingNewItems() {
        assertEquals(listOf("new", "unavailable", "b", "a"),
            mergeHomeOrder(listOf("new", "b", "a", "b"), listOf("a", "unavailable", "b")))
        assertEquals(listOf("b", "unavailable", clockCalendar, "a"),
            mergeHomeOrder(listOf("b", clockCalendar, "a", "grace:calendar"),
                listOf("a", "unavailable", "grace:clock", "grace:calendar", "b")))
    }

    @Test fun savedWidgetWinsDuringMigrationAndRemovingLegacyPreservesAnchor() {
        val layout = HomeLayout(widgetId = 41, topOffsetDp = 20f, baselineOffsetDp = 85f,
            baselineEnabled = false, clockCalendarEnabled = false, homeOrder = listOf("widget:41", clockCalendar))
        val stored = PopupItem("widget:41", HomeLayout(widgetId = 41, widgetHeightDp = 300))
        val items = orderedHomeItems(LauncherSettings(homeLayout = layout), emptyList(), listOf(stored))
        assertEquals(listOf(stored), items.filter { it.widget != null })
        val cleared = layout.withoutWidget()
        assertFalse(cleared.hasWidget)
        assertEquals(layout.homeOrder, cleared.homeOrder)
        assertEquals(layout.baselineOffsetDp, cleared.baselineOffsetDp)
        assertEquals(layout.topOffsetDp, cleared.topOffsetDp)
        assertFalse(cleared.baselineEnabled)
        assertFalse(cleared.clockCalendarEnabled)
    }
}
