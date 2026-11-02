package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class ClockStyleTest {
    @Test fun baseLayoutsShareVisibilityWhilePresetsIgnoreIt() {
        val hidden = ClockVisibility(false, false, false, false, false)
        val style = ClockStyle(visibility = hidden)
        ClockLayout.entries.forEach { layout ->
            val selected = style.copy(layout = layout)
            assertEquals(if (layout.presetFont == null) hidden else ClockVisibility(), selected.effectiveVisibility)
            assertEquals(hidden, selected.visibility)
            assertTrue(selected.face.showLeadingZero)
        }
    }

    @Test fun leadingZeroRemainsIndependentInEveryLayoutSlot() {
        var style = ClockStyle()
        ClockLayout.entries.forEachIndexed { index, layout ->
            style = style.copy(layout = layout).withFace(style.copy(layout = layout).face.copy(showLeadingZero = index % 2 == 0))
        }
        ClockLayout.entries.forEachIndexed { index, layout ->
            assertEquals(index % 2 == 0, style.copy(layout = layout).face.showLeadingZero)
        }
    }

    @Test fun presetConversionCopiesTypographyFontAndVisiblePartsWithoutChangingSharedColorsOrPresets() {
        val presetFonts = mapOf(
            ClockLayout.Sacramento to AppFont.Sacramento.id,
            ClockLayout.SacramentoWeek to AppFont.Sacramento.id,
            ClockLayout.Bokor to AppFont.Bokor.id,
            ClockLayout.Plaster to AppFont.Plaster.id,
            ClockLayout.DoublePlaster to AppFont.Plaster.id,
            ClockLayout.Monoton to AppFont.Monoton.id,
            ClockLayout.Lucky to AppFont.LuckiestGuy.id,
        )
        val colors = mapOf(ClockColorPart.HourTens to IconColor(0, fontColorId = FontColors.ThemeId),
            ClockColorPart.Day to IconColor(0, materialRole = MaterialColorRole.Secondary))
        presetFonts.forEach { (layout, fontId) ->
            val original = ClockStyle(layout = layout, visibility = ClockVisibility(showClock = false, showDate = false), colors = colors)
                .withFace(ClockFaceStyle(weight = 850, size = 111, letterSpacing = -7, hourMinuteSpacing = 31,
                    fontShadow = 8, showColon = true, separateDigits = true, showLeadingZero = false))
            val applied = original.applyPresetToBase()
            val expectedLayout = when {
                layout.week -> ClockLayout.Week
                layout.stacked -> ClockLayout.TwoLines
                else -> ClockLayout.SingleLine
            }
            assertEquals(expectedLayout, applied.layout)
            assertEquals(original.face.copy(fontId = fontId), applied.face)
            assertEquals(ClockVisibility(), applied.visibility)
            assertEquals(colors, applied.colors)
            assertEquals(original.face, applied.copy(layout = layout).face)
            original.presets.forEach { (key, face) -> assertEquals(face, applied.presets[key]) }
            assertEquals(original.visibility, original.applyPresetToBase(applyVisibility = false).visibility)
        }
    }

    @Test fun applyingBaseLayoutIsANoOpAndDeletingFontsPreservesOtherClockPreferences() {
        val style = ClockStyle(layout = ClockLayout.Week, visibility = ClockVisibility(showBattery = false),
            colors = mapOf(ClockColorPart.Weather to IconColor(0xFF123456.toInt())))
            .withFace(ClockFaceStyle(fontId = "imported", showLeadingZero = false))
        assertEquals(style, style.applyPresetToBase())
        val cleaned = style.withoutFonts(setOf("imported"))
        assertNull(cleaned.face.fontId)
        assertFalse(cleaned.face.showLeadingZero)
        assertEquals(style.visibility, cleaned.visibility)
        assertEquals(style.colors, cleaned.colors)
    }
}
