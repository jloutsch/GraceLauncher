package com.galaxyrio.gracelauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FontShadowPersistenceTest {
    @Test fun shadowKeepsSpreadAndLiveColorReferencesAcrossSerialization() {
        listOf(IconColor(0, materialRole = MaterialColorRole.OnSurface), IconColor(0, fontColorId = FontColors.ThemeId)).forEach { color ->
            val value = FontShadow.Sharp.copy(preset = FontShadowPreset.Custom, spread = 3.5f, offsetX = -2f, color = color)
            assertEquals(value, FontShadow.decode(value.encode()))
        }
    }

    @Test fun legacyZeroInheritsWhileNonzeroBecomesAnOverride() {
        val old = ClockStyle.decode("""{
            "singleLine":{"fontShadow":8},
            "regions":{
                "primaryFaces":{"Clock":{"fontShadow":13}},
                "secondary":{"typography":{"fontShadow":0}},
                "tertiary":{"typography":{"fontShadow":5}},
                "agenda":{"typography":{"fontShadow":9}},
                "tasks":{"typography":{"fontShadow":7}}
            }
        }""")
        assertEquals(FontShadowOverride.fromLegacy(8), old.singleLine.shadow)
        val regions = old.resolvedRegions()
        assertEquals(FontShadowOverride.fromLegacy(13), regions.face(ClockElement.Clock).shadow)
        assertFalse(regions.secondary.typography.shadow.enabled)
        assertEquals(FontShadowOverride.fromLegacy(5), regions.tertiary.typography.shadow)
        assertEquals(FontShadowOverride.fromLegacy(9), regions.agenda.typography.shadow)
        assertEquals(FontShadowOverride.fromLegacy(7), regions.tasks.typography.shadow)
    }

    @Test fun clockScalesAndDisabledOverrideSurviveSavingAndReopening() {
        val shadow = FontShadowOverride(false, FontShadow.Sharp.copy(preset = FontShadowPreset.Custom, spread = 2f))
        val face = ClockFaceStyle(showSeconds = true, horizontalEqualWidthDigits = true, horizontalCenterDigits = true,
            minuteScale = 20, secondScale = 150, shadow = shadow)
        val row = ClockRowTypography(shadow = shadow)
        val value = ClockStyle(singleLine = face, twoLines = face, regions = ClockRegions(stackedClock = true,
            primaryFaces = mapOf(ClockElement.Clock to face), secondary = ClockRegionRow(typography = row),
            tertiary = ClockRegionRow(typography = row), agenda = ClockAgenda(typography = row), tasks = ClockTasks(typography = row)))
        val restored = ClockStyle.decode(value.encode())
        assertEquals(face, restored.singleLine)
        assertEquals(face, restored.twoLines)
        val regions = restored.resolvedRegions()
        assertEquals(face, regions.face(ClockElement.Clock))
        listOf(regions.secondary, regions.tertiary).forEach { assertEquals(shadow, it.typography.shadow) }
        assertEquals(shadow, regions.agenda.typography.shadow)
        assertEquals(shadow, regions.tasks.typography.shadow)
    }
}
