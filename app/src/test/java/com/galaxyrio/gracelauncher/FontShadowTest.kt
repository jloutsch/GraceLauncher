package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class FontShadowTest {
    @Test fun defaultsAndLegacyZeroFollowTheGlobalSetting() {
        assertEquals(FontShadowPreset.None, LauncherSettings().fontShadow.preset)
        assertFalse(ClockFaceStyle().shadow.enabled)
        assertFalse(ClockRowTypography().shadow.enabled)
        assertFalse(FontShadowOverride.fromLegacy(0).enabled)
    }

    @Test fun legacyShadowBecomesAnOverrideWithTheOriginalDimensions() {
        val migrated = FontShadowOverride.fromLegacy(8)
        assertTrue(migrated.enabled)
        assertEquals(FontShadowPreset.Custom, migrated.shadow.preset)
        assertEquals(25, migrated.shadow.opacity)
        assertEquals(8f, migrated.shadow.blur, .0001f)
        assertEquals(0f, migrated.shadow.offsetX, .0001f)
        assertEquals(1f, migrated.shadow.offsetY, .0001f)
        assertEquals(0f, migrated.shadow.spread, .0001f)
        assertEquals(migrated, ClockFaceStyle(fontShadow = 8).shadow)
        assertEquals(migrated, ClockRowTypography(fontShadow = 8).shadow)
    }

    @Test fun sharpRestoresItsParametersAndDisablingPreservesTheOverride() {
        val custom = FontShadow.Soft.copy(preset = FontShadowPreset.Custom, blur = 21f, spread = 4f, offsetX = -3f)
        val sharp = custom.select(FontShadowPreset.Sharp)
        assertEquals(95, sharp.opacity)
        assertEquals(2f, sharp.blur, .0001f)
        assertEquals(0f, sharp.offsetX, .0001f)
        assertEquals(1f, sharp.offsetY, .0001f)
        assertEquals(0f, sharp.spread, .0001f)
        assertEquals(custom, FontShadowOverride(true, custom).copy(enabled = false).shadow)
        val none = custom.select(FontShadowPreset.None)
        assertEquals(FontShadow(color = custom.color), none)
        assertEquals(none.copy(preset = FontShadowPreset.Custom), none.select(FontShadowPreset.Custom))
        assertEquals(none, custom.copy(preset = FontShadowPreset.None).normalized())
    }

    @Test fun clockGroupScalesAreBoundedAndEveryPresetStartsAtFullSize() {
        assertEquals(20, ClockFaceStyle(minuteScale = -1).normalized().minuteScale)
        assertEquals(150, ClockFaceStyle(secondScale = 500).normalized().secondScale)
        ClockLayout.entries.forEach { layout ->
            val face = ClockStyle().selectPreset(layout).resolvedRegions().face(ClockElement.Clock)
            assertEquals(100, face.minuteScale)
            assertEquals(100, face.secondScale)
            assertFalse(face.showSeconds)
        }
    }
}
