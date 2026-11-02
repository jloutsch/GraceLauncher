package com.galaxyrio.gracelauncher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.galaxyrio.gracelauncher.data.AppFont
import com.galaxyrio.gracelauncher.data.ClockColorPart
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.ClockVisibility
import com.galaxyrio.gracelauncher.data.FontColors
import com.galaxyrio.gracelauncher.data.IconColor
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

    private fun show(style: ClockStyle = ClockStyle(), navigation: Boolean = false) {
        state = state.copy(settings = state.settings.copy(clockStyle = style, weatherEnabled = false))
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false, fontColors = state.settings.fontColors) {
                val actions = LauncherActions(
                    updateSettings = { change -> state = state.copy(settings = change(state.settings)) },
                    applyClockStyle = { saved ->
                        applied = saved
                        state = state.copy(settings = state.settings.copy(clockStyle = saved))
                        true
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
        compose.onNodeWithTag("clock_layout_options").performScrollToNode(hasTestTag("clock_layout:${layout.name}"))
        return compose.onNodeWithTag("clock_layout:${layout.name}")
    }

    @Test fun startsInStyleAndKeepsBaseLayoutsSeparateFromNamedPresets() {
        show()
        compose.onNodeWithTag("clock_tab:Style").assertIsSelected()
        listOf(ClockLayout.SingleLine, ClockLayout.TwoLines, ClockLayout.Week).forEach {
            findLayout(it).assertExists()
        }
        compose.onNodeWithTag("clock_layout:Sacramento").assertDoesNotExist()

        tab("Presets")
        compose.onNodeWithTag("clock_tab:Presets").assertIsSelected()
        ClockLayout.entries.filter { !it.allowsCustomFont }.forEach { findLayout(it).assertExists() }
        listOf(ClockLayout.SingleLine, ClockLayout.TwoLines, ClockLayout.Week).forEach {
            compose.onNodeWithTag("clock_layout:${it.name}").assertDoesNotExist()
        }
        compose.onNodeWithTag("clock_show_clock").assertDoesNotExist()
        compose.runOnIdle { assertNull(applied) }
    }

    @Test fun applyingAPresetCopiesItsTypographyAndRestoresVisiblePartsInTheBaseLayout() {
        show(ClockStyle(visibility = ClockVisibility(false, false, false, false, false)))
        compose.onNodeWithTag("clock_style_preview_text", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("home_weather").assertDoesNotExist()

        tab("Presets")
        findLayout(ClockLayout.DoublePlaster).performClick()
        compose.onNodeWithTag("clock_style_preview_text", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("home_weather").assertExists()
        findControl("clock_size_item")
        compose.onNodeWithTag("clock_size", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        findControl("clock_show_leading_zero").performClick()
        compose.onNodeWithTag("clock_style_apply").performClick()

        compose.runOnIdle {
            val saved = requireNotNull(applied)
            assertEquals(ClockLayout.TwoLines, saved.layout)
            assertEquals(AppFont.Plaster.id, saved.face.fontId)
            assertEquals(100, saved.face.size)
            assertFalse(saved.face.showLeadingZero)
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
            assertEquals(100, saved.face.size)
            assertEquals(IconColor(0, fontColorId = FontColors.PrimaryId,
                hueShift = 30, saturationShift = -20, brightnessShift = 15), saved.colors[ClockColorPart.HourTens])
        }
    }

    @Test fun previewWeatherUsesTheLocalSwitchIndependentlyOfTheWeatherFeature() {
        show()
        compose.runOnIdle { assertFalse(state.settings.weatherEnabled) }
        compose.onNodeWithTag("home_weather").assertExists()
        findControl("clock_show_weather").performClick()
        compose.onNodeWithTag("home_weather").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(weatherEnabled = true)) }
        compose.onNodeWithTag("home_weather").assertDoesNotExist()
        compose.onNodeWithTag("clock_style_apply").performClick()

        compose.runOnIdle {
            assertTrue(state.settings.weatherEnabled)
            assertFalse(requireNotNull(applied).visibility.showWeather)
        }
    }
}
