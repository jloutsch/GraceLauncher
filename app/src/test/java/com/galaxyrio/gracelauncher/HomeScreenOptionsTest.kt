package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.CalendarPreferences
import com.galaxyrio.gracelauncher.data.DateAction
import com.galaxyrio.gracelauncher.data.HomeLayout
import org.junit.Assert.*
import org.junit.Test

class HomeScreenOptionsTest {
    @Test fun homeCanScrollAndStretchByDefault() {
        val home = HomeLayout()
        assertTrue(home.enabled)
        assertFalse(home.disableScrolling)
        assertFalse(home.disableStretch)
    }

    @Test fun removingAWidgetPreservesIndependentScreenOptions() {
        val home = HomeLayout(widgetId = 9, enabled = false, disableScrolling = true, disableStretch = false)
        val cleared = home.withoutWidget()
        assertFalse(cleared.hasWidget)
        assertFalse(cleared.enabled)
        assertTrue(cleared.disableScrolling)
        assertFalse(cleared.disableStretch)
    }

    @Test fun dateActionsAreIndependent() {
        val defaults = CalendarPreferences()
        assertEquals(DateAction.Agenda, defaults.dateTapAction)
        assertEquals(DateAction.Calendar, defaults.dateLongPressAction)
        val changed = defaults.copy(dateTapAction = DateAction.NewTask)
        assertEquals(DateAction.Calendar, changed.dateLongPressAction)
    }
}
