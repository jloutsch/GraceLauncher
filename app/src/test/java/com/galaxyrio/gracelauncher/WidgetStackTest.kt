package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.components.widgetStackPageCount
import com.galaxyrio.gracelauncher.ui.components.widgetStackStartPage
import org.junit.Assert.*
import org.junit.Test

class WidgetStackTest {
    @Test fun wrappingStacksStartOnTheFirstWidgetFarFromEitherEnd() {
        for (count in 2..12) {
            val pages = widgetStackPageCount(count)
            val start = widgetStackStartPage(count)
            assertEquals(0, start % count)
            // Hundreds of swipes either way before an end could be reached.
            assertTrue(start >= count * 400)
            assertTrue(pages - start >= count * 400)
        }
    }

    @Test fun singleOrEmptyStacksDoNotWrap() {
        assertEquals(1, widgetStackPageCount(1))
        assertEquals(0, widgetStackStartPage(1))
        assertEquals(0, widgetStackPageCount(0))
    }

    @Test fun swipingPastTheLastWidgetReturnsToTheFirst() {
        val count = 4
        val start = widgetStackStartPage(count)
        assertEquals(count - 1, (start - 1) % count)
        assertEquals(0, (start + count) % count)
    }
}
