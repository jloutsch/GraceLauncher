package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.settings.sliderStep
import com.galaxyrio.gracelauncher.ui.settings.stepStackHeight
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStackHeightTest {
    @Test fun buttonsMoveOneDpSoEveryHeightCanBeReached() {
        assertEquals(151, stepStackHeight(150, +1))
        assertEquals(149, stepStackHeight(150, -1))
    }

    @Test fun automaticSitsJustBelowTheMinimumHeight() {
        assertEquals(48, stepStackHeight(0, +1))
        assertEquals(48, stepStackHeight(30, +1))
        assertEquals(0, stepStackHeight(48, -1))
        assertEquals(0, stepStackHeight(0, -1))
    }

    @Test fun heightStopsAtTheMaximum() {
        assertEquals(640, stepStackHeight(640, +1))
        assertEquals(639, stepStackHeight(640, -1))
    }

    @Test fun sharedSliderStepsOneWholeUnitWithinItsRange() {
        assertEquals(151f, sliderStep(150f, +1, 0f..640f))
        assertEquals(150f, sliderStep(150.4f, 0, 0f..640f))
        assertEquals(-100f, sliderStep(-100f, -1, -100f..100f))
        assertEquals(8f, sliderStep(8f, +1, 2f..8f))
        assertEquals(37.5f, sliderStep(37f, +1, 0f..37.5f))
    }
}
