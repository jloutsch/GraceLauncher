package com.galaxyrio.gracelauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockStylePersistenceTest {
    @Test fun colorSourcesVisibilityAndLayoutFacesRoundTrip() {
        val colors = mapOf(
            ClockColorPart.HourTens to IconColor(0xFF123456.toInt()),
            ClockColorPart.HourOnes to IconColor(0, materialRole = MaterialColorRole.OnPrimary),
            ClockColorPart.MinuteTens to IconColor(0, fontColorId = FontColors.ThemeId),
            ClockColorPart.MinuteOnes to IconColor(0, fontColorId = "custom"),
            ClockColorPart.Month to IconColor(0, materialRole = MaterialColorRole.Secondary, hueShift = 12),
        )
        var style = ClockStyle(colors = colors, visibility = ClockVisibility(showWeekday = false, showBattery = false))
        ClockLayout.entries.forEachIndexed { index, layout ->
            style = style.copy(layout = layout).withFace(style.copy(layout = layout).face.copy(showLeadingZero = index % 2 == 0))
        }
        val encoded = style.encode()
        assertEquals(style, ClockStyle.decode(encoded))
        val savedColors = JSONObject(encoded).getJSONObject("colors")
        assertFalse(savedColors.has(ClockColorPart.Weather.name))
        assertFalse(savedColors.has(ClockColorPart.Day.name))
    }

    @Test fun legacyBatteryOnlyFillsMissingVisibilityAndLegacyFacesKeepLeadingZero() {
        val legacy = """{"layout":"TwoLines","twoLines":{"size":95},"colors":{"unknown":{},"Weather":null,"Day":"invalid"}}"""
        val decoded = ClockStyle.decode(legacy, legacyShowBattery = false)
        assertFalse(decoded.visibility.showBattery)
        assertTrue(decoded.visibility.showClock)
        assertTrue(decoded.face.showLeadingZero)
        assertEquals(95, decoded.face.size)
        assertTrue(decoded.colors.isEmpty())
        assertEquals(ClockVisibility(showBattery = false), ClockStyle.decode(null, legacyShowBattery = false).visibility)
        assertEquals(ClockVisibility(showBattery = false), ClockStyle.decode("bad json", legacyShowBattery = false).visibility)
        val explicit = ClockStyle(visibility = ClockVisibility(showBattery = true))
        assertEquals(explicit, ClockStyle.decode(explicit.encode(), legacyShowBattery = false))
    }
}
