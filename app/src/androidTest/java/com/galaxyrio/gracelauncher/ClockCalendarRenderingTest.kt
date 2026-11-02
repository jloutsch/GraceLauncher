package com.galaxyrio.gracelauncher

import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.ClockVisibility
import com.galaxyrio.gracelauncher.ui.home.HomeClockHeader
import com.galaxyrio.gracelauncher.ui.home.clockCalendarDate
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockCalendarRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-10-30T12:00:00Z")

    @Test fun coloredDateKeepsLocalizedOrderPunctuationAndIndependentFields() {
        listOf(Locale.US, Locale.SIMPLIFIED_CHINESE, Locale.JAPANESE, Locale.forLanguageTag("ar")).forEach { locale ->
            val date = clockCalendarDate(now, locale, true, true, Color.Red, Color.Green, Color.Blue)
            val expected = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMEd"), locale).format(Date.from(now))
            assertEquals(expected, date.text)
            assertEquals(setOf(Color.Red, Color.Green, Color.Blue), date.spanStyles.map { it.item.color }.toSet())
            assertTrue(date.spanStyles.all { it.start < it.end })

            val dateOnly = clockCalendarDate(now, locale, false, true, Color.Red, Color.Green, Color.Blue)
            assertEquals(setOf(Color.Green, Color.Blue), dateOnly.spanStyles.map { it.item.color }.toSet())
            val weekdayOnly = clockCalendarDate(now, locale, true, false, Color.Red, Color.Green, Color.Blue)
            assertEquals(setOf(Color.Red), weekdayOnly.spanStyles.map { it.item.color }.toSet())
            assertEquals("", clockCalendarDate(now, locale, false, false, Color.Red, Color.Green, Color.Blue).text)
        }
    }

    @Test fun weekTimeAndWeekdayHaveIndependentVisibilityAndClickTargets() {
        var style by mutableStateOf(ClockStyle(layout = ClockLayout.Week, visibility = ClockVisibility(
            showWeekday = false, showDate = false, showBattery = false, showWeather = false,
        )))
        var calendarEnabled by mutableStateOf(true)
        var clockClicks = 0
        var calendarClicks = 0
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomeClockHeader(now, null, { calendarClicks++ }, { clockClicks++ }, true, style, null,
                    showCalendar = calendarEnabled)
            }
        }
        compose.onNodeWithTag("home_clock").assertDoesNotExist()
        compose.onNodeWithTag("home_clock_time").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, clockClicks)
            assertEquals(0, calendarClicks)
            style = style.copy(visibility = style.visibility.copy(showClock = false, showWeekday = true))
        }
        compose.onNodeWithTag("home_clock_time").assertDoesNotExist()
        compose.onNodeWithTag("home_clock").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, clockClicks)
            assertEquals(1, calendarClicks)
            style = style.copy(visibility = style.visibility.copy(showClock = true))
            calendarEnabled = false
        }
        compose.onNodeWithTag("home_clock").assertDoesNotExist()
        compose.onNodeWithTag("home_clock_time").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(2, clockClicks)
            assertEquals(1, calendarClicks)
        }
    }
}
