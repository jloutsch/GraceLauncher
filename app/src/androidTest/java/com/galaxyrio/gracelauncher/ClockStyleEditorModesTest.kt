package com.galaxyrio.gracelauncher

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import java.io.File
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.ClockStyleSettings
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClockStyleEditorModesTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
    private var applied: ClockStyle? = null
    private var exits = 0
    private var failSave = false

    private fun show(style: ClockStyle = ClockStyle(), navigation: Boolean = false) {
        state = state.copy(settings = state.settings.copy(clockStyle = style, weatherEnabled = false))
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false, fontColors = state.settings.fontColors) {
                val actions = LauncherActions(
                    updateSettings = { change -> state = state.copy(settings = change(state.settings)) },
                    applyClockStyle = { saved ->
                        if (!failSave) {
                            applied = saved
                            state = state.copy(settings = state.settings.copy(clockStyle = saved))
                        }
                        !failSave
                    },
                )
                if (navigation) LauncherSettingsScreen(state, actions, onBack = { exits++ }, initialPage = "ClockStyle")
                else ClockStyleSettings(state, actions, onBack = { exits++ }, onManageFonts = {})
            }
        }
    }

    private fun tab(name: String) = compose.onNodeWithTag("clock_tab:$name").performClick()

    private fun findControl(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("clock_style_controls").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun findLayout(layout: ClockLayout): SemanticsNodeInteraction {
        findControl("clock_layout_selector")
        compose.onNodeWithTag("clock_layout_options", useUnmergedTree = true).performScrollToNode(hasTestTag("clock_layout:${layout.name}"))
        return compose.onNodeWithTag("clock_layout:${layout.name}")
    }

    private fun element(region: ClockRegion, element: ClockElement) =
        findControl("clock_region:${region.name}:${element.name}")

    private fun drag(region: ClockRegion, from: String, to: String) {
        val group = findControl("clock_${region.name.lowercase()}_elements")
        val origin = group.fetchSemanticsNode().boundsInRoot.topLeft
        val start = compose.onNodeWithTag("clock_region:${region.name}:$from", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - origin
        val end = compose.onNodeWithTag("clock_region:${region.name}:$to", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - origin
        group.performTouchInput { down(start); advanceEventTime(700); moveBy(Offset(0.5f, 0f)) }
        compose.waitForIdle()
        group.performTouchInput { moveTo(end, delayMillis = 64) }
        compose.waitForIdle()
        group.performTouchInput { up() }
    }

    @Test fun startsWithThreeElementGroupsAndKeepsNamedPresetsOnTheirOwnTab() {
        show()
        screenshot("clock-regions-primary.png")
        compose.onNodeWithTag("clock_tab:Style").assertIsSelected()
        compose.onNodeWithTag("clock_layout_selector").assertDoesNotExist()
        ClockRegion.entries.forEach { region ->
            ClockElement.entries.forEach { element(region, it).assertExists() }
        }

        tab("Presets")
        compose.onNodeWithTag("clock_tab:Presets").assertIsSelected()
        ClockLayout.entries.filter { !it.allowsCustomFont }.forEach { findLayout(it).assertExists() }
        listOf(ClockLayout.SingleLine, ClockLayout.TwoLines, ClockLayout.Week).forEach {
            compose.onNodeWithTag("clock_layout:${it.name}").assertDoesNotExist()
        }
        compose.onNodeWithTag("clock_show_clock").assertDoesNotExist()
        compose.runOnIdle { assertNull(applied) }
    }

    @Test fun selectingAcrossGroupsMovesAnElementAndDoesNotSaveUntilApply() {
        show()
        element(ClockRegion.Secondary, ClockElement.Clock).assertIsOff().assertIsNotEnabled()
        element(ClockRegion.Tertiary, ClockElement.Clock).assertIsOff().assertIsNotEnabled()
        element(ClockRegion.Primary, ClockElement.Clock).performClick()
        element(ClockRegion.Secondary, ClockElement.Clock).assertIsOff().performClick()
        element(ClockRegion.Primary, ClockElement.Clock).assertIsOff()
        element(ClockRegion.Tertiary, ClockElement.Clock).assertIsOff().performClick()
        element(ClockRegion.Secondary, ClockElement.Clock).assertIsOff()
        element(ClockRegion.Tertiary, ClockElement.Clock).assertIsOn()
        element(ClockRegion.Primary, ClockElement.Weather).performClick()
        element(ClockRegion.Secondary, ClockElement.Weather).assertIsOff()
            .assertIsNotEnabled()
        element(ClockRegion.Primary, ClockElement.Weather).assertIsOn()
        compose.runOnIdle { assertNull(applied); assertEquals(ClockStyle(), state.settings.clockStyle) }
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.runOnIdle {
            val regions = requireNotNull(applied).resolvedRegions()
            assertEquals(ClockElement.Weather, regions.primary)
            assertEquals(setOf(ClockElement.Clock), regions.tertiary.selected)
            assertEquals(setOf(ClockElement.Weekday, ClockElement.Date, ClockElement.Battery), regions.secondary.selected)
        }
    }

    @Test fun clearingPrimaryHidesItsControlsAndLeavesTheOtherRegionsSelected() {
        show()
        findControl("clock_font_selector").assertExists()
        element(ClockRegion.Primary, ClockElement.Clock).assertIsOn().performClick()
        compose.onNodeWithTag("clock_primary_alignment").assertDoesNotExist()
        compose.onNodeWithTag("clock_font_selector").assertDoesNotExist()
        compose.onNodeWithTag("clock_size_item").assertDoesNotExist()
        element(ClockRegion.Secondary, ClockElement.Date).assertIsOn()
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.runOnIdle {
            assertNull(requireNotNull(applied).resolvedRegions().primary)
            assertEquals(ClockRegions().secondary.selected, requireNotNull(applied).resolvedRegions().secondary.selected)
        }
    }

    @Test fun pointerDragsMoveIconsAndDividerWithoutTogglingSelection() {
        show()
        drag(ClockRegion.Secondary, "Weather", "Weekday")
        drag(ClockRegion.Secondary, "Divider", "Date")
        // A locked Primary element remains a sortable, unselected slot.
        element(ClockRegion.Secondary, ClockElement.Clock).assertIsNotEnabled()
        drag(ClockRegion.Secondary, "Clock", "Battery")
        // Both scrolling away and changing tabs dispose the row's pointer handlers.
        findControl("clock_primary_elements")
        tab("Color")
        tab("Style")
        findControl("clock_secondary_elements")
        screenshot("clock-regions-secondary.png")
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.runOnIdle {
            val row = requireNotNull(applied).resolvedRegions().secondary
            assertEquals(listOf(ClockElement.Weather, ClockElement.Weekday,
                null, ClockElement.Date, ClockElement.Battery, ClockElement.Clock), row.order)
            assertEquals(listOf(ClockElement.Weather, ClockElement.Weekday), row.leftItems)
            assertEquals(listOf(ClockElement.Date, ClockElement.Battery), row.rightItems)
            assertEquals(ClockRegions().secondary.selected, row.selected)
        }
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun miscReordersRegionsWithoutChangingTheirContent() {
        show()
        findControl("clock_region_order")
        val list = compose.onNodeWithTag("clock_region_order_list", useUnmergedTree = true)
        val origin = list.fetchSemanticsNode().boundsInRoot.topLeft
        val start = compose.onNodeWithTag("clock_region_drag:Primary").fetchSemanticsNode().boundsInRoot.center - origin
        val last = compose.onNodeWithTag("clock_region_drag:Tertiary").fetchSemanticsNode().boundsInRoot
        val end = last.center - origin + Offset(0f, last.height * 0.25f)
        list.performTouchInput { down(start); moveTo((start + end) / 2f, delayMillis = 100) }
        compose.waitForIdle()
        list.performTouchInput { moveTo(end, delayMillis = 100) }
        compose.waitForIdle()
        list.performTouchInput { up() }
        findControl("clock_primary_elements")
        findControl("clock_region_order")
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.runOnIdle {
            val regions = requireNotNull(applied).resolvedRegions()
            assertEquals(listOf(ClockRegion.Secondary, ClockRegion.Tertiary, ClockRegion.Primary), regions.regionOrder)
            assertEquals(ClockElement.Clock, regions.primary)
            assertEquals(ClockRegions().secondary, regions.secondary)
        }
    }

    @Test fun clockAndWeatherOptionsFollowTheRegionThatOwnsTheElement() {
        show()
        findControl("clock_secondary_weather_icons").assertExists()
        element(ClockRegion.Primary, ClockElement.Clock).performClick()
        element(ClockRegion.Secondary, ClockElement.Clock).performClick()
        findControl("clock_secondary_show_leading_zero").performClick()
        findControl("clock_secondary_show_colon").performClick()
        element(ClockRegion.Tertiary, ClockElement.Clock).performClick()
        findControl("clock_tertiary_show_leading_zero").assertIsOff()
        findControl("clock_tertiary_show_colon").assertIsOn()
        element(ClockRegion.Tertiary, ClockElement.Weather).performClick()
        findControl("clock_tertiary_weather_icons").assertExists()
        element(ClockRegion.Primary, ClockElement.Weather).performClick()
        findControl("clock_primary_weather_icons").assertExists()
        element(ClockRegion.Secondary, ClockElement.Weather).assertIsNotEnabled()
        element(ClockRegion.Tertiary, ClockElement.Weather).assertIsNotEnabled()
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.runOnIdle {
            val regions = requireNotNull(applied).resolvedRegions()
            assertEquals(ClockElement.Weather, regions.primary)
            assertEquals(setOf(ClockElement.Clock), regions.tertiary.selected)
            assertFalse(regions.face(ClockElement.Clock).showLeadingZero)
            assertTrue(regions.face(ClockElement.Clock).showColon)
        }
    }

    @Test fun rotationSwitchesRetainPrimaryWhileAllIsOnAndCanBothBeOff() {
        show()
        findControl("clock_rotate_primary").performClick().assertIsOn()
        findControl("clock_rotate_all").assertIsOff().performClick().assertIsOn()
        findControl("clock_rotate_primary").assertIsOn()
        findControl("clock_rotate_all").performClick().assertIsOff()
        findControl("clock_rotate_primary").assertIsOn().performClick().assertIsOff().performClick()
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.runOnIdle {
            val regions = requireNotNull(applied).resolvedRegions()
            assertFalse(regions.rotateAbovePrimary)
            assertTrue(regions.rotatePrimary)
            assertFalse(regions.rotateAll)
        }
    }

    private fun setSize(value: Float) {
        findControl("clock_size_item")
        compose.onNodeWithTag("clock_size", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }

    @Test fun appBarBackOffersCancelDiscardOrSaveWithoutLosingTheDraft() {
        show()
        setSize(100f)
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("clock_unsaved").assertIsDisplayed()
        compose.onNodeWithTag("clock_unsaved_cancel").performClick()
        compose.onNodeWithTag("clock_unsaved").assertDoesNotExist()
        compose.runOnIdle { assertNull(applied); assertEquals(0, exits) }
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("clock_unsaved_save").performClick()
        compose.runOnIdle {
            assertEquals(100, requireNotNull(applied).resolvedRegions().face(ClockElement.Clock).size)
            assertEquals(1, exits)
        }
    }

    @Test fun systemBackAlsoGuardsChangesAndDiscardDoesNotSave() {
        show()
        setSize(100f)
        pressBack()
        compose.onNodeWithTag("clock_unsaved").assertIsDisplayed()
        compose.onNodeWithTag("clock_unsaved_discard").performClick()
        compose.runOnIdle { assertNull(applied); assertEquals(1, exits) }
    }

    @Test fun revertingTheOnlyEditDoesNotPromptForUnsavedChanges() {
        show()
        setSize(100f)
        compose.onNodeWithTag("clock_size_reset", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("clock_unsaved").assertDoesNotExist()
        compose.runOnIdle { assertNull(applied); assertEquals(1, exits) }
    }

    @Test fun failedSaveFromExitDialogKeepsDraftAndCanBeRetried() {
        show()
        setSize(100f)
        compose.onNodeWithTag("settings_back").performClick()
        compose.runOnIdle { failSave = true }
        compose.onNodeWithTag("clock_unsaved_save").performClick()
        compose.onNodeWithTag("clock_unsaved").assertIsDisplayed()
        compose.runOnIdle { assertNull(applied); assertEquals(0, exits); failSave = false }
        compose.onNodeWithTag("clock_unsaved_save").performClick()
        compose.runOnIdle { assertNotNull(applied); assertEquals(1, exits) }
    }

    @Test fun applyingAPresetCopiesItsTypographyAndRestoresVisiblePartsInRegions() {
        show(ClockStyle(visibility = ClockVisibility(false, false, false, false, false)))
        compose.onNodeWithTag("clock_style_preview_text", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("home_weather").assertDoesNotExist()

        tab("Presets")
        findLayout(ClockLayout.DoublePlaster).performClick()
        compose.onNodeWithTag("clock_style_preview_text", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("home_weather").assertExists()
        tab("Style")
        findControl("clock_size_item")
        compose.onNodeWithTag("clock_size", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        findControl("clock_show_leading_zero").performClick()
        compose.onNodeWithTag("clock_style_apply").performClick()

        compose.runOnIdle {
            val saved = requireNotNull(applied)
            val regions = saved.resolvedRegions()
            assertEquals(ClockLayout.TwoLines, saved.layout)
            assertTrue(regions.stackedClock)
            assertEquals(ClockElement.Clock, regions.primary)
            assertEquals(AppFont.Plaster.id, regions.face(ClockElement.Clock).fontId)
            assertEquals(100, regions.face(ClockElement.Clock).size)
            assertFalse(regions.face(ClockElement.Clock).showLeadingZero)
            assertEquals(ClockRegions().secondary.selected, regions.secondary.selected)
            assertEquals(ClockVisibility(), saved.visibility)
            assertEquals(1, exits)
        }
    }

    @Test fun partColorsRemainSharedWhileSwitchingBetweenPresetAndStyleTabs() {
        show()
        tab("Color")
        findControl("clock_color:HourTens").performClick()
        compose.onNodeWithTag("icon_designer_color_source:Fixed", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_hex").performScrollTo().performTextReplacement("12AB34")
        compose.onNodeWithTag("icon_designer_color_close").performScrollTo().performClick()

        tab("Presets")
        findLayout(ClockLayout.SacramentoWeek).performClick()
        tab("Color")
        findControl("clock_color:HourTens").performClick()
        compose.onNodeWithTag("icon_designer_hex").performScrollTo().assertTextContains("12AB34", substring = true)
        compose.onNodeWithTag("icon_designer_color_close").performScrollTo().performClick()
        tab("Style")
        tab("Color")
        compose.onNodeWithTag("clock_style_apply").performClick()

        compose.runOnIdle {
            assertEquals(IconColor(0xFF12AB34.toInt()), requireNotNull(applied).colors[ClockColorPart.HourTens])
            assertEquals(setOf(ClockColorPart.HourTens), requireNotNull(applied).colors.keys)
            val regions = requireNotNull(applied).resolvedRegions()
            assertEquals(ClockElement.Weekday, regions.primary)
            assertEquals(AppFont.Sacramento.id, regions.face(ClockElement.Weekday).fontId)
        }
    }

    @Test fun editingASavedFontColorAndReturningKeepsTheClockDraftAndRelativeOffsets() {
        show(navigation = true)
        findControl("clock_size_item")
        compose.onNodeWithTag("clock_size", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        tab("Color")
        findControl("clock_color:HourTens").performClick()
        compose.onNodeWithTag("font_color_presets").performScrollTo().performClick()
        compose.onNodeWithTag("saved_font_color:${FontColors.PrimaryId}").performScrollTo().performClick()
        listOf("hue" to 30f, "saturation" to -20f, "brightness" to 15f).forEach { (part, value) ->
            compose.onNodeWithTag("icon_designer_${part}_shift", useUnmergedTree = true).performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        }

        compose.onNodeWithTag("font_color_presets").performScrollTo().performClick()
        compose.onNodeWithTag("saved_font_colors_edit").performClick()
        compose.onNodeWithTag("font_colors").assertExists()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("font_color:${FontColors.PrimaryId}"))
        compose.onNodeWithTag("font_color:${FontColors.PrimaryId}").performClick()
        compose.onNodeWithTag("material_color_presets").assertExists()
        compose.onNodeWithTag("icon_designer_color_source:Fixed", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_hex").performScrollTo().performTextReplacement("345678")
        compose.onNodeWithTag("icon_designer_color_close").performScrollTo().performClick()
        compose.onNodeWithTag("settings_back").performClick()

        compose.onNodeWithTag("settings_clock_style_editor").assertExists()
        compose.onNodeWithTag("icon_designer_color_source:Theme", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("icon_designer_color_close").performScrollTo().performClick()
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.runOnIdle {
            assertEquals(IconColor(0xFF345678.toInt()), state.settings.fontColors.color(FontColors.PrimaryId))
            val saved = requireNotNull(applied)
            assertEquals(100, saved.resolvedRegions().face(ClockElement.Clock).size)
            assertEquals(IconColor(0, fontColorId = FontColors.PrimaryId,
                hueShift = 30, saturationShift = -20, brightnessShift = 15), saved.colors[ClockColorPart.HourTens])
        }
    }

    @Test fun previewWeatherUsesItsRegionSelectionIndependentlyOfTheWeatherFeature() {
        show()
        compose.runOnIdle { assertFalse(state.settings.weatherEnabled) }
        compose.onNodeWithTag("home_weather").assertExists()
        element(ClockRegion.Secondary, ClockElement.Weather).performClick()
        compose.onNodeWithTag("home_weather").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(weatherEnabled = true)) }
        compose.onNodeWithTag("home_weather").assertDoesNotExist()
        compose.onNodeWithTag("clock_style_apply").performClick()

        compose.runOnIdle {
            assertTrue(state.settings.weatherEnabled)
            val regions = requireNotNull(applied).resolvedRegions()
            assertFalse(ClockElement.Weather in regions.secondary.selected)
            assertFalse(ClockElement.Weather in regions.tertiary.selected)
            assertNotEquals(ClockElement.Weather, regions.primary)
        }
    }
}
