package com.galaxyrio.gracelauncher

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.ClockAlignment
import com.galaxyrio.gracelauncher.data.ClockElement
import com.galaxyrio.gracelauncher.data.ClockFaceStyle
import com.galaxyrio.gracelauncher.data.ClockRegion
import com.galaxyrio.gracelauncher.data.ClockRegionRow
import com.galaxyrio.gracelauncher.data.ClockRegions
import com.galaxyrio.gracelauncher.data.ClockRowTypography
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.weather.WeatherCondition
import com.galaxyrio.gracelauncher.data.weather.WeatherCurrent
import com.galaxyrio.gracelauncher.data.weather.WeatherTemperature
import com.galaxyrio.gracelauncher.ui.home.HomeClockHeader
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockRegionRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-11-03T08:00:00Z")
    private val weather = WeatherCurrent(WeatherTemperature(22.0, "c"), WeatherCondition.PartlyCloudy, true, null)

    @Test fun regionOrderAndAlignmentPreserveIndependentClickTargets() {
        var clockClicks = 0
        var dateClicks = 0
        var weatherClicks = 0
        val regions = ClockRegions(
            primary = ClockElement.Weekday,
            primaryAlignment = ClockAlignment.Center,
            secondary = ClockRegionRow(
                order = listOf(ClockElement.Clock, null, ClockElement.Date),
                selected = setOf(ClockElement.Clock, ClockElement.Date),
            ),
            tertiary = ClockRegionRow(order = listOf(null, ClockElement.Weather), selected = setOf(ClockElement.Weather)),
            regionOrder = listOf(ClockRegion.Tertiary, ClockRegion.Primary, ClockRegion.Secondary),
        )
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomeClockHeader(now, null, { dateClicks++ }, { clockClicks++ }, false,
                    ClockStyle(regions = regions), weather, Modifier.width(360.dp),
                    onWeatherClick = { weatherClicks++ })
            }
        }
        val primary = bounds("clock_region:Primary")
        val secondary = bounds("clock_region:Secondary")
        val tertiary = bounds("clock_region:Tertiary")
        assertTrue(tertiary.bottom <= primary.top)
        assertTrue(primary.bottom <= secondary.top)
        assertEquals(primary.center.x, bounds("home_date_weekday").center.x, 1f)
        assertEquals(secondary.left, bounds("home_clock_time").left, 1f)
        assertEquals(secondary.right, bounds("home_date").right, 1f)
        assertEquals(tertiary.right, bounds("home_weather").right, 1f)

        compose.onNodeWithTag("home_clock_time").performClick()
        compose.onNodeWithTag("home_date").performClick()
        compose.onNodeWithTag("home_date_weekday").performClick()
        compose.onNodeWithTag("home_weather").performClick()
        compose.runOnIdle {
            assertEquals(1, clockClicks)
            assertEquals(2, dateClicks)
            assertEquals(1, weatherClicks)
        }
    }

    @Test fun rowTypographyDoesNotInheritPrimaryClockSpacingOrShadow() {
        var regions by mutableStateOf(ClockRegions(
            primary = null,
            primaryFaces = mapOf(ClockElement.Clock to ClockFaceStyle(hourMinuteSpacing = 64, fontShadow = 24)),
            secondary = ClockRegionRow(selected = setOf(ClockElement.Clock, ClockElement.Date),
                typography = ClockRowTypography(size = 22, weight = 600, fontShadow = 0)),
        ))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomeClockHeader(now, null, {}, {}, false, ClockStyle(regions = regions), null, Modifier.width(360.dp))
            }
        }
        val width = bounds("home_clock_time").width
        val timeStyle = layout("home_clock_time").layoutInput.style
        val dateStyle = layout("home_date").layoutInput.style
        assertEquals(dateStyle.fontSize, timeStyle.fontSize)
        assertEquals(dateStyle.fontWeight, timeStyle.fontWeight)
        assertEquals(dateStyle.shadow, timeStyle.shadow)
        compose.runOnIdle {
            regions = regions.copy(primaryFaces = mapOf(ClockElement.Clock to ClockFaceStyle(
                hourMinuteSpacing = -16, letterSpacing = 16, fontShadow = 0)))
        }
        assertEquals(width, bounds("home_clock_time").width, 0.1f)
    }

    @Test fun featureGatesHideOnlyTheirOwnElementsAndCollapseEmptyRegions() {
        var showCalendar by mutableStateOf(true)
        val regions = ClockRegions(primary = ClockElement.Date,
            secondary = ClockRegionRow(selected = setOf(ClockElement.Clock)),
            tertiary = ClockRegionRow(selected = setOf(ClockElement.Weather)))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomeClockHeader(now, null, {}, {}, false, ClockStyle(regions = regions), weather,
                    Modifier.width(360.dp), showCalendar = showCalendar)
            }
        }
        compose.onNodeWithTag("home_date").assertIsDisplayed()
        compose.runOnIdle { showCalendar = false }
        compose.onNodeWithTag("clock_region:Primary").assertDoesNotExist()
        compose.onNodeWithTag("home_clock_time").assertIsDisplayed()
        compose.onNodeWithTag("home_weather").assertIsDisplayed()
    }

    @Test fun largeTextPreservesBothPhysicalEdgesInRtl() {
        val regions = ClockRegions(primary = ClockElement.Date, primaryAlignment = ClockAlignment.Right,
            secondary = ClockRegionRow(order = listOf(ClockElement.Clock, null, ClockElement.Weather),
                selected = setOf(ClockElement.Clock, ClockElement.Weather), typography = ClockRowTypography(size = 72)))
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                GraceLauncherTheme(dynamicColor = false) {
                    HomeClockHeader(now, null, {}, {}, false, ClockStyle(regions = regions), weather, Modifier.width(320.dp))
                }
            }
        }
        val row = bounds("clock_region:Secondary")
        val clock = bounds("home_clock_time")
        val weatherBounds = bounds("home_weather")
        assertEquals(bounds("clock_region:Primary").right, bounds("home_date").right, 1f)
        assertEquals(row.left, clock.left, 1f)
        assertEquals(row.right, weatherBounds.right, 1f)
        assertTrue(clock.width > 0f && weatherBounds.width > 0f)
        assertTrue(clock.right < weatherBounds.left)
    }

    @Test fun primaryRotationSwapsItsBoundsAndPreservesVerticalRegionsAndTouchTargets() {
        var rotatePrimary by mutableStateOf(false)
        var clockClicks = 0
        var dateClicks = 0
        var weatherClicks = 0
        val regions = ClockRegions(primaryAlignment = ClockAlignment.Right,
            secondary = ClockRegionRow(selected = setOf(ClockElement.Date)),
            tertiary = ClockRegionRow(selected = setOf(ClockElement.Weather)))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomeClockHeader(now, null, { dateClicks++ }, { clockClicks++ }, false,
                    ClockStyle(regions = regions.copy(rotatePrimary = rotatePrimary)), weather, Modifier.width(360.dp),
                    onWeatherClick = { weatherClicks++ })
            }
        }
        val originalClock = bounds("home_clock")
        val originalDate = bounds("home_date")
        compose.runOnIdle { rotatePrimary = true }
        val rotatedClock = bounds("home_clock")
        assertEquals(originalClock.height, rotatedClock.width, 2f)
        assertEquals(originalClock.width, rotatedClock.height, 2f)
        assertEquals(bounds("clock_region:Primary").right, rotatedClock.right, 2f)
        assertTrue(bounds("clock_region:Primary").bottom <= bounds("clock_region:Secondary").top)
        assertTrue(bounds("clock_region:Secondary").bottom <= bounds("clock_region:Tertiary").top)
        assertEquals(originalDate.width, bounds("home_date").width, 1f)
        assertEquals(originalDate.height, bounds("home_date").height, 1f)
        touch("home_clock")
        touch("home_date")
        touch("home_weather")
        compose.runOnIdle {
            assertEquals(1, clockClicks)
            assertEquals(1, dateClicks)
            assertEquals(1, weatherClicks)
        }
    }

    @Test fun rotatingAllRegionsSwapsTheBlockAndKeepsAgendaBelowAndUnrotated() {
        var rotateAll by mutableStateOf(false)
        var clockClicks = 0
        var dateClicks = 0
        var weatherClicks = 0
        val regions = ClockRegions(
            secondary = ClockRegionRow(selected = setOf(ClockElement.Date)),
            tertiary = ClockRegionRow(selected = setOf(ClockElement.Weather)))
        val event = ScheduleEvent(1, "Team meeting", now.plusSeconds(600), now.plusSeconds(3600), false, null, null)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomeClockHeader(now, event, { dateClicks++ }, { clockClicks++ }, false,
                    ClockStyle(regions = regions.copy(rotateAll = rotateAll)), weather, Modifier.width(360.dp),
                    onWeatherClick = { weatherClicks++ })
            }
        }
        val originalRegions = ClockRegion.entries.associateWith { bounds("clock_region:$it") }
        val originalAgenda = bounds("home_agenda")
        compose.runOnIdle { rotateAll = true }
        ClockRegion.entries.forEach { region ->
            val original = originalRegions.getValue(region)
            val rotated = bounds("clock_region:$region")
            assertEquals(original.height, rotated.width, 2f)
            assertEquals(original.width, rotated.height, 2f)
        }
        val primary = bounds("clock_region:Primary")
        val secondary = bounds("clock_region:Secondary")
        val tertiary = bounds("clock_region:Tertiary")
        val agenda = bounds("home_agenda")
        assertTrue(primary.right <= secondary.left + 1f)
        assertTrue(secondary.right <= tertiary.left + 1f)
        assertTrue(maxOf(primary.bottom, secondary.bottom, tertiary.bottom) <= agenda.top + 1f)
        assertEquals(originalAgenda.width, agenda.width, 1f)
        assertEquals(originalAgenda.height, agenda.height, 1f)
        touch("home_clock")
        touch("home_date")
        touch("home_weather")
        touch("home_agenda")
        compose.runOnIdle {
            assertEquals(1, clockClicks)
            assertEquals(2, dateClicks)
            assertEquals(1, weatherClicks)
        }
    }

    @Test fun rotatingTallRegionsFitsAvailableWidthWithoutDroppingRows() {
        var weatherClicks = 0
        val regions = ClockRegions(stackedClock = true, rotateAll = true,
            primaryFaces = mapOf(ClockElement.Clock to ClockFaceStyle(size = 144)),
            secondary = ClockRegionRow(selected = setOf(ClockElement.Date), typography = ClockRowTypography(size = 72)),
            tertiary = ClockRegionRow(selected = setOf(ClockElement.Weather), typography = ClockRowTypography(size = 72)))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomeClockHeader(now, null, {}, {}, false, ClockStyle(regions = regions), weather, Modifier.width(180.dp),
                    onWeatherClick = { weatherClicks++ })
            }
        }
        val container = bounds("clock_regions")
        ClockRegion.entries.forEach { region ->
            compose.onNodeWithTag("clock_region:$region").assertIsDisplayed()
            val regionBounds = bounds("clock_region:$region")
            assertTrue(regionBounds.width > 0f && regionBounds.height > 0f)
            assertTrue(regionBounds.left >= container.left - 1f && regionBounds.right <= container.right + 1f)
            assertTrue(regionBounds.top >= container.top - 1f && regionBounds.bottom <= container.bottom + 1f)
        }
        touch("home_weather")
        compose.runOnIdle { assertEquals(1, weatherClicks) }
    }

    private fun touch(tag: String) = compose.onNodeWithTag(tag).performTouchInput { click() }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
