package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class ClockRegionsTest {
    @Test fun regionOrderingKeepsFirstOccurrencesAndCompletesMissingRegions() {
        val regions = ClockRegions(regionOrder = listOf(ClockRegion.Tertiary, ClockRegion.Primary,
            ClockRegion.Tertiary)).normalized()
        assertEquals(listOf(ClockRegion.Tertiary, ClockRegion.Primary, ClockRegion.Secondary), regions.regionOrder)
        assertEquals(ClockRegion.entries, ClockRegions(regionOrder = emptyList()).normalized().regionOrder)
    }

    @Test fun legacyCalendarAboveClockMigratesOnlyBeforeExplicitRegionOrderExists() {
        val legacy = ClockStyle()
        val migrated = legacy.resolvedRegions(calendarAboveClock = true)
        assertEquals(listOf(ClockRegion.Secondary, ClockRegion.Primary, ClockRegion.Tertiary), migrated.regionOrder)
        assertEquals(ClockRegion.entries, legacy.resolvedRegions().regionOrder)
        val saved = legacy.withRegions(migrated.copy(regionOrder = listOf(ClockRegion.Tertiary,
            ClockRegion.Primary, ClockRegion.Secondary)))
        assertEquals(saved.regions, saved.resolvedRegions(calendarAboveClock = false))
        assertEquals(saved.regions, saved.resolvedRegions(calendarAboveClock = true))
    }

    @Test fun primarySelectionMustBeClearedBeforeMovingItsElementToAnotherRegion() {
        val face = ClockFaceStyle(fontId = "imported", size = 103, hourMinuteSpacing = 21)
        val original = ClockRegions(primaryFaces = mapOf(ClockElement.Clock to face))
        assertEquals(original, original.select(ClockRegion.Secondary, ClockElement.Clock, true))
        assertEquals(original, original.select(ClockRegion.Tertiary, ClockElement.Clock, true))
        val secondary = original.select(ClockRegion.Primary, ClockElement.Clock, false)
            .select(ClockRegion.Secondary, ClockElement.Clock, true)
        assertNull(secondary.primary)
        assertTrue(ClockElement.Clock in secondary.secondary.selected)
        assertEquals(original.secondary.order, secondary.secondary.order)
        assertEquals(face, secondary.face(ClockElement.Clock))

        val tertiary = secondary.select(ClockRegion.Tertiary, ClockElement.Clock, true)
        assertFalse(ClockElement.Clock in tertiary.secondary.selected)
        assertEquals(listOf(ClockElement.Clock), tertiary.tertiary.leftItems)
        assertNull(tertiary.primary)

        val primary = tertiary.select(ClockRegion.Primary, ClockElement.Clock, true)
        assertEquals(ClockElement.Clock, primary.primary)
        assertTrue(primary.tertiary.selected.isEmpty())
        assertEquals(face, primary.face(ClockElement.Clock))
    }

    @Test fun rotationSwitchesAreIndependentAndPreservedBySelectionAndFontChanges() {
        val original = ClockRegions(primaryFaces = mapOf(ClockElement.Clock to ClockFaceStyle(fontId = "imported")))
        fun flags(value: ClockRegions) = listOf(value.rotateAbovePrimary, value.rotatePrimary, value.rotateAll)
        assertEquals(listOf(false, false, false), flags(original))
        val primary = original.copy(rotateAbovePrimary = true, rotatePrimary = true)
        val all = primary.copy(rotateAll = true)
        assertEquals(listOf(true, true, true), flags(all.normalized()))
        assertEquals(listOf(true, true, true), flags(all.select(ClockRegion.Primary, ClockElement.Date, true)))
        assertEquals(listOf(true, true, true), flags(all.withoutFonts(setOf("imported"))))
        assertEquals(primary, all.copy(rotateAll = false))
        assertEquals(listOf(false, false, false), flags(ClockStyle(layout = ClockLayout.Week).resolvedRegions()))
    }

    @Test fun primaryCanBeEmptyAndClearingAnUnselectedButtonDoesNotRemoveAnotherRegion() {
        val empty = ClockRegions().select(ClockRegion.Primary, ClockElement.Clock, false)
        assertNull(empty.primary)
        assertFalse(ClockElement.Clock in empty.secondary.selected)
        assertTrue(empty.tertiary.selected.isEmpty())
        assertEquals(empty, empty.select(ClockRegion.Tertiary, ClockElement.Weather, false))
        assertTrue(ClockElement.Weather in empty.secondary.selected)
        assertEquals(empty, empty.withPrimaryFace(ClockFaceStyle(size = 120)))
    }

    @Test fun dividerSeparatesOrderedItemsAndUnselectedButtonsKeepTheirPosition() {
        val row = ClockRegionRow(
            order = listOf(ClockElement.Weather, ClockElement.Clock, null, ClockElement.Date,
                ClockElement.Weekday, ClockElement.Battery),
            selected = setOf(ClockElement.Clock, ClockElement.Weekday, ClockElement.Weather),
        )
        assertEquals(listOf(ClockElement.Weather, ClockElement.Clock), row.leftItems)
        assertEquals(listOf(ClockElement.Weekday), row.rightItems)
        assertEquals(listOf(ClockElement.Date, ClockElement.Weekday),
            row.copy(selected = row.selected + ClockElement.Date).rightItems)
        assertEquals(row.order, row.normalized().order)
    }

    @Test fun malformedOrdersKeepFirstOccurrencesAndRestoreMissingButtonsAndDivider() {
        val row = ClockRegionRow(
            order = listOf(ClockElement.Date, null, ClockElement.Date, null, ClockElement.Weather),
            selected = ClockElement.entries.toSet(),
        ).normalized()
        assertEquals(listOf(ClockElement.Date, null, ClockElement.Weather, ClockElement.Clock,
            ClockElement.Weekday, ClockElement.Battery), row.order)
        assertEquals(listOf(ClockElement.Date), row.leftItems)
        assertEquals(listOf(ClockElement.Weather, ClockElement.Clock, ClockElement.Weekday, ClockElement.Battery), row.rightItems)
        val noDivider = row.copy(order = listOf(ClockElement.Battery)).normalized()
        assertNull(noDivider.order.last())
        assertEquals(6, noDivider.order.size)
    }

    @Test fun duplicateSelectionsUseRegionPriorityAndRowTypographyIsBounded() {
        val all = ClockElement.entries.toSet()
        val regions = ClockRegions(
            primary = ClockElement.Date,
            secondary = ClockRegionRow(selected = all, typography = ClockRowTypography(-5, 1500, 40)),
            tertiary = ClockRegionRow(selected = all, typography = ClockRowTypography(100, -5, -5)),
        ).normalized()
        assertEquals(all - ClockElement.Date, regions.secondary.selected)
        assertTrue(regions.tertiary.selected.isEmpty())
        assertEquals(ClockRowTypography(8, 900, 24), regions.secondary.typography)
        assertEquals(ClockRowTypography(72, 100, 0), regions.tertiary.typography)
    }

    @Test fun legacyWeekLayoutKeepsLargeWeekdayAndSmallClockWithHiddenPartsAbsent() {
        val face = ClockFaceStyle(fontId = "imported", size = 99, weight = 500, letterSpacing = 7)
        val style = ClockStyle(layout = ClockLayout.Week, visibility = ClockVisibility(showBattery = false))
            .withFace(face)
        val regions = style.resolvedRegions()
        assertNull(style.regions)
        assertEquals(ClockElement.Weekday, regions.primary)
        assertEquals(face, regions.face(ClockElement.Weekday))
        assertEquals(listOf(ClockElement.Clock, ClockElement.Date, ClockElement.Weather), regions.secondary.leftItems)
        assertTrue(regions.secondary.rightItems.isEmpty())
        assertTrue(regions.tertiary.selected.isEmpty())
    }

    @Test fun hiddenLegacyPrimaryIsNotReplacedByAnotherElement() {
        val single = ClockStyle(visibility = ClockVisibility(showClock = false, showDate = false)).resolvedRegions()
        assertNull(single.primary)
        assertEquals(listOf(ClockElement.Weekday, ClockElement.Battery, ClockElement.Weather), single.secondary.leftItems)
        val hidden = ClockStyle(layout = ClockLayout.Week,
            visibility = ClockVisibility(false, false, false, false, false)).resolvedRegions()
        assertNull(hidden.primary)
        assertTrue(hidden.secondary.selected.isEmpty())
        assertTrue(hidden.tertiary.selected.isEmpty())
    }

    @Test fun legacyWeekKeepsSmallClockFormattingAndBothSavedTypographySlots() {
        val clockFace = ClockFaceStyle(fontId = "clock-font", size = 108, weight = 500,
            letterSpacing = 5, hourMinuteSpacing = 23, fontShadow = 8)
        val weekdayFace = ClockFaceStyle(fontId = AppFont.Sacramento.id, size = 99,
            showColon = true, showLeadingZero = false)
        val style = ClockStyle(layout = ClockLayout.Week, singleLine = clockFace,
            presets = mapOf(ClockLayout.Week to weekdayFace))
        val weekRegions = style.resolvedRegions()
        assertEquals(weekdayFace, weekRegions.face(ClockElement.Weekday))
        assertEquals(clockFace.copy(showColon = true, showLeadingZero = false), weekRegions.face(ClockElement.Clock))
        assertTrue(ClockElement.Clock in weekRegions.secondary.selected)

        val clockRegions = style.copy(layout = ClockLayout.SingleLine).resolvedRegions()
        assertEquals(clockFace, clockRegions.face(ClockElement.Clock))
        assertEquals(weekdayFace, clockRegions.face(ClockElement.Weekday))
    }

    @Test fun legacyStackedClockRetainsItsSavedFaceAndNewRegionsRemainAuthoritative() {
        val face = ClockFaceStyle(fontId = "clock-font", size = 109, fontShadow = 13, showColon = true)
        val legacy = ClockStyle(layout = ClockLayout.TwoLines).withFace(face)
        val migrated = legacy.withRegions()
        assertTrue(migrated.resolvedRegions().stackedClock)
        assertEquals(face, migrated.resolvedRegions().face(ClockElement.Clock))
        assertEquals(migrated.regions, migrated.copy(layout = ClockLayout.Week,
            visibility = ClockVisibility(false, false, false, false, false)).resolvedRegions())
    }

    @Test fun selectingPresetReplacesCustomRegionsAndPreservesColorChoices() {
        val color = IconColor(0, fontColorId = FontColors.ThemeId)
        val custom = ClockStyle(colors = mapOf(ClockColorPart.Weekday to color), regions = ClockRegions(
            primary = ClockElement.Weather, primaryAlignment = ClockAlignment.Right,
            secondary = ClockRegionRow(), tertiary = ClockRegionRow(selected = setOf(ClockElement.Clock)),
        ))
        val preset = custom.selectPreset(ClockLayout.SacramentoWeek)
        val regions = preset.resolvedRegions()
        assertEquals(ClockElement.Weekday, regions.primary)
        assertEquals(ClockAlignment.Left, regions.primaryAlignment)
        assertEquals(AppFont.Sacramento.id, regions.face(ClockElement.Weekday).fontId)
        assertEquals(96, regions.face(ClockElement.Weekday).size)
        assertEquals(listOf(ClockElement.Clock, ClockElement.Date, ClockElement.Battery, ClockElement.Weather), regions.secondary.leftItems)
        assertTrue(regions.tertiary.selected.isEmpty())
        assertEquals(color, preset.colors[ClockColorPart.Weekday])
    }

    @Test fun removingImportedFontCleansInactivePrimaryFacesWithoutChangingLayout() {
        val face = ClockFaceStyle(fontId = "imported", size = 87, fontShadow = 9)
        val regions = ClockRegions(
            primary = ClockElement.Date,
            primaryAlignment = ClockAlignment.Center,
            primaryFaces = mapOf(ClockElement.Clock to face, ClockElement.Date to face,
                ClockElement.Weekday to face.copy(fontId = AppFont.Sacramento.id)),
            secondary = ClockRegionRow(selected = setOf(ClockElement.Clock), typography = ClockRowTypography(22, 600, 4)),
        )
        val cleaned = ClockStyle(regions = regions).withoutFonts(setOf("imported")).resolvedRegions()
        assertNull(cleaned.face(ClockElement.Clock).fontId)
        assertEquals(face.copy(fontId = null), cleaned.face(ClockElement.Date))
        assertEquals(AppFont.Sacramento.id, cleaned.face(ClockElement.Weekday).fontId)
        assertEquals(ClockElement.Date, cleaned.primary)
        assertEquals(ClockAlignment.Center, cleaned.primaryAlignment)
        assertEquals(regions.secondary, cleaned.secondary)
    }
}
