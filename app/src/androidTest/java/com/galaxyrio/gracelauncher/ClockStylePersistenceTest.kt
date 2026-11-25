package com.galaxyrio.gracelauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockStylePersistenceTest {
    @Test fun digitAlignmentChoicesSurviveLayoutChangesAndSerializationIndependently() {
        ClockLayout.entries.forEach { layout ->
            val defaults = ClockStyle().selectPreset(layout).resolvedRegions().face(ClockElement.Clock)
            assertFalse(defaults.equalWidthDigits(stacked = false))
            assertFalse(defaults.centerDigits(stacked = false))
            assertTrue(defaults.equalWidthDigits(stacked = true))
            assertTrue(defaults.centerDigits(stacked = true))
        }
        val face = ClockFaceStyle().withEqualWidthDigits(false, true).withCenterDigits(false, true)
            .withEqualWidthDigits(true, false).withCenterDigits(true, false)
        val original = ClockStyle(singleLine = face, twoLines = face,
            presets = mapOf(ClockLayout.DoublePlaster to face),
            regions = ClockRegions(primaryFaces = mapOf(ClockElement.Clock to face)))
        val restored = ClockStyle.decode(original.encode())
        assertEquals(face, restored.singleLine)
        assertEquals(face, restored.twoLines)
        assertEquals(face, restored.presets[ClockLayout.DoublePlaster])
        listOf(false, true, false).forEach { stacked ->
            val selected = restored.resolvedRegions().copy(stackedClock = stacked).face(ClockElement.Clock)
            assertEquals(!stacked, selected.equalWidthDigits(stacked))
            assertEquals(!stacked, selected.centerDigits(stacked))
        }
    }

    @Test fun oldSharedDigitAlignmentMigratesOnlyToVerticalIncludingExplicitFalse() {
        listOf(false, true).forEach { equal ->
            listOf(false, true).forEach { center ->
                val shared = """{"equalWidthDigits":$equal,"centerDigits":$center}"""
                val original = ClockStyle.decode("""{
                    "singleLine":$shared,"twoLines":$shared,
                    "presets":{"DoublePlaster":$shared},
                    "regions":{"stackedClock":false,"primaryFaces":{"Clock":$shared}}
                }""")
                listOf(original, ClockStyle.decode(original.encode())).forEach { style ->
                    listOf(style.singleLine, style.twoLines, style.presets.getValue(ClockLayout.DoublePlaster),
                        style.resolvedRegions().face(ClockElement.Clock)).forEach { face ->
                        assertFalse(face.equalWidthDigits(stacked = false))
                        assertFalse(face.centerDigits(stacked = false))
                        assertEquals(equal, face.equalWidthDigits(stacked = true))
                        assertEquals(center, face.centerDigits(stacked = true))
                    }
                }
            }
        }
    }

    @Test fun emptyPrimaryRegionOrderAndIndependentTypographyRoundTrip() {
        val regions = ClockRegions(
            primary = null,
            primaryAlignment = ClockAlignment.Right,
            primaryFaces = mapOf(
                ClockElement.Clock to ClockFaceStyle(fontId = "imported", weight = 800, size = 117,
                    letterSpacing = 4, hourMinuteSpacing = 26, fontShadow = 7, showColon = true,
                    separateDigits = true, showLeadingZero = false),
                ClockElement.Weekday to ClockFaceStyle(fontId = AppFont.Sacramento.id, size = 96),
            ),
            stackedClock = true,
            secondary = ClockRegionRow(
                order = listOf(ClockElement.Weather, null, ClockElement.Date, ClockElement.Clock,
                    ClockElement.Weekday, ClockElement.Battery),
                selected = setOf(ClockElement.Date, ClockElement.Weekday),
                typography = ClockRowTypography(size = 23, weight = 600, fontShadow = 5),
            ),
            tertiary = ClockRegionRow(
                order = listOf(ClockElement.Battery, ClockElement.Weather, null, ClockElement.Clock,
                    ClockElement.Weekday, ClockElement.Date),
                selected = setOf(ClockElement.Battery, ClockElement.Weather),
                typography = ClockRowTypography(size = 14, weight = 300, fontShadow = 11),
            ),
            regionOrder = listOf(ClockRegion.Tertiary, ClockRegion.Primary, ClockRegion.Secondary),
            rotateAll = true,
        )
        val original = ClockStyle(regions = regions,
            colors = mapOf(ClockColorPart.Weather to IconColor(0, fontColorId = FontColors.WallpaperId)))
        val encoded = original.encode()
        assertTrue(JSONObject(encoded).getJSONObject("regions").isNull("primary"))
        val restored = ClockStyle.decode(encoded)
        assertEquals(original, restored)
        assertNull(restored.resolvedRegions().primary)
        assertEquals(listOf(ClockElement.Date, ClockElement.Weekday), restored.resolvedRegions().secondary.rightItems)
        assertEquals(listOf(ClockElement.Battery, ClockElement.Weather), restored.resolvedRegions().tertiary.leftItems)
    }

    @Test fun rotationFlagsRoundTripAndLegacyModesMigrateWithoutOverridingExplicitFlags() {
        fun flags(value: ClockRegions) = listOf(value.rotateAbovePrimary, value.rotatePrimary, value.rotateAll)
        listOf(ClockRegions(), ClockRegions(rotatePrimary = true),
            ClockRegions(rotateAbovePrimary = true, rotatePrimary = true, rotateAll = true)).forEach { regions ->
            val style = ClockStyle(regions = regions)
            val saved = JSONObject(style.encode()).getJSONObject("regions")
            assertEquals(flags(regions), listOf(saved.getBoolean("rotateAbovePrimary"),
                saved.getBoolean("rotatePrimary"), saved.getBoolean("rotateAll")))
            assertEquals(flags(regions), flags(ClockStyle.decode(style.encode()).resolvedRegions()))
        }
        assertEquals(listOf(false, false, false), flags(ClockStyle.decode("""{"regions":{}}""").resolvedRegions()))
        assertEquals(listOf(false, false, false), flags(ClockStyle.decode("""{"regions":{"rotation":"unknown"}}""").resolvedRegions()))
        assertEquals(listOf(false, false, false), flags(ClockStyle.decode("""{"layout":"Week"}""").resolvedRegions()))
        assertEquals(listOf(false, true, false), flags(ClockStyle.decode("""{"regions":{"rotation":"Primary"}}""").resolvedRegions()))
        assertEquals(listOf(false, false, true), flags(ClockStyle.decode("""{"regions":{"rotation":"All"}}""").resolvedRegions()))
        assertEquals(listOf(true, false, false), flags(ClockStyle.decode(
            """{"regions":{"rotation":"Primary","rotateAbovePrimary":true,"rotatePrimary":false}}""").resolvedRegions()))
        assertEquals(listOf(false, true, false), flags(ClockStyle.decode(
            """{"regions":{"rotation":"All","rotatePrimary":true,"rotateAll":false}}""").resolvedRegions()))
    }

    @Test fun invalidRegionEntriesAreNormalizedWithoutDiscardingValidSettings() {
        val encoded = """{
            "layout":"TwoLines",
            "regions":{
                "primary":"Date","primaryAlignment":"unknown",
                "regionOrder":["Tertiary","unknown","Tertiary"],
                "primaryFaces":{"Date":{"size":-1,"weight":9999,"fontShadow":50}},
                "secondary":{
                    "order":["Weather",null,"Weather",null,"unknown"],
                    "selected":["Date","Clock","Weather","unknown"],
                    "typography":{"size":900,"weight":-1,"fontShadow":-1}
                },
                "tertiary":{"order":[],"selected":["Clock","Battery","Weather"]}
            }
        }"""
        val restored = ClockStyle.decode(encoded)
        val regions = restored.resolvedRegions()
        assertEquals(ClockLayout.TwoLines, restored.layout)
        assertEquals(ClockElement.Date, regions.primary)
        assertEquals(ClockAlignment.Left, regions.primaryAlignment)
        assertEquals(listOf(ClockRegion.Tertiary, ClockRegion.Primary, ClockRegion.Secondary), regions.regionOrder)
        assertEquals(listOf(ClockElement.Weather, null, ClockElement.Clock, ClockElement.Weekday,
            ClockElement.Date, ClockElement.Battery), regions.secondary.order)
        assertEquals(setOf(ClockElement.Clock, ClockElement.Weather), regions.secondary.selected)
        assertEquals(setOf(ClockElement.Battery), regions.tertiary.selected)
        assertEquals(ClockElement.entries + listOf(null), regions.tertiary.order)
        assertEquals(ClockRowTypography(72, 100, 0), regions.secondary.typography)
        assertEquals(32, regions.face(ClockElement.Date).size)
        assertEquals(700, regions.face(ClockElement.Date).weight)
        assertEquals(24, regions.face(ClockElement.Date).fontShadow)
        assertEquals(restored, ClockStyle.decode(restored.encode()))
    }

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
