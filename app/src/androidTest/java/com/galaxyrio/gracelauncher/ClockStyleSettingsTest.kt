package com.galaxyrio.gracelauncher

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.ClockElement
import com.galaxyrio.gracelauncher.data.ClockFontStore
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClockStyleSettingsTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
    private var writes = 0
    private var failSave = false

    private fun show() {
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(applyClockStyle = {
                    writes++
                    if (!failSave) state = state.copy(settings = state.settings.copy(clockStyle = it))
                    !failSave
                }), onBack = {})
            }
        }
        compose.onNodeWithTag("settings_category_themes").performClick()
        openEditor()
    }
    private fun openEditor() = compose.onNodeWithTag("settings_clock_style").performScrollTo().performClick()
    private fun control(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("clock_style_controls").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag, useUnmergedTree = true)
    }
    private fun chooseLines(stacked: Boolean) = control("clock_primary_lines:$stacked").performClick()
    private fun choosePrimary(element: ClockElement) = control("clock_region:Primary:${element.name}").performClick()
    private fun set(tag: String, value: Float) {
        control("${tag}_item")
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }
    private fun assertSliderValue(tag: String, value: Float) {
        control("${tag}_item")
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().fetchSemanticsNode()
        assertEquals(value, node.config[SemanticsProperties.ProgressBarRangeInfo].current, 0.01f)
    }
    private fun previewText(): String = compose.onNodeWithTag("clock_style_preview_text", useUnmergedTree = true)
        .fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    @Test fun previewsAreLiveAndPrimaryElementsKeepIndependentValuesUntilApply() {
        show()
        assertEquals(1, previewText().lines().size)
        set("clock_weight", 600f)
        assertSliderValue("clock_weight", 600f)
        set("clock_size", 80f)
        chooseLines(true)
        assertEquals(2, previewText().lines().size)
        compose.onNodeWithTag("clock_show_colon").assertDoesNotExist()
        screenshot("clock-style-two-lines.png")
        choosePrimary(ClockElement.Weekday)
        set("clock_size", 104f)
        choosePrimary(ClockElement.Clock)
        assertEquals(2, previewText().lines().size)
        assertSliderValue("clock_size", 80f)
        assertSliderValue("clock_weight", 600f)
        chooseLines(false)
        control("clock_show_colon").performClick()
        assertTrue(previewText().contains(':'))
        compose.runOnIdle { assertEquals(0, writes); assertEquals(ClockStyle(), state.settings.clockStyle) }
        screenshot("clock-style-single-line.png")
        chooseLines(true)
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.onNodeWithTag("settings_themes").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, writes)
            val regions = state.settings.clockStyle.resolvedRegions()
            assertTrue(regions.stackedClock)
            assertEquals(600, regions.face(ClockElement.Clock).weight)
            assertEquals(80, regions.face(ClockElement.Clock).size)
            assertEquals(104, regions.face(ClockElement.Weekday).size)
            assertTrue(regions.face(ClockElement.Clock).showColon)
        }
        openEditor()
        assertEquals(2, previewText().lines().size)
        choosePrimary(ClockElement.Weekday)
        assertSliderValue("clock_size", 104f)
        choosePrimary(ClockElement.Clock)
        assertEquals(2, previewText().lines().size)
        assertSliderValue("clock_size", 80f)
        chooseLines(false)
        assertTrue(previewText().contains(':'))
    }

    @Test fun resetButtonsResetOnlyTheirOwnSliderAndBackDiscardsTheDraft() {
        show()
        set("clock_weight", 700f)
        set("clock_size", 96f)
        set("clock_letter_spacing", 4f)
        compose.onNodeWithTag("clock_size_reset", useUnmergedTree = true).performScrollTo().performClick().assertIsNotEnabled()
        assertSliderValue("clock_size", 72f)
        assertSliderValue("clock_weight", 700f)
        assertSliderValue("clock_letter_spacing", 4f)
        compose.onNodeWithTag("clock_letter_spacing_reset", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("clock_weight_reset", useUnmergedTree = true).performScrollTo().performClick()
        assertSliderValue("clock_weight", 300f)
        chooseLines(true)
        set("clock_size", 112f)
        compose.onNodeWithTag("clock_size_reset", useUnmergedTree = true).performClick()
        assertSliderValue("clock_size", 72f)
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("clock_unsaved_discard").performClick()
        openEditor()
        assertEquals(1, previewText().lines().size)
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun failedApplyKeepsTheEditorAndCanBeRetried() {
        failSave = true
        show()
        chooseLines(true)
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.onNodeWithTag("settings_clock_style_editor").assertIsDisplayed()
        compose.runOnIdle { assertFalse(state.settings.clockStyle.resolvedRegions().stackedClock); failSave = false }
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.onNodeWithTag("settings_themes").assertIsDisplayed()
        compose.runOnIdle { assertTrue(state.settings.clockStyle.resolvedRegions().stackedClock); assertEquals(2, writes) }
    }

    @Test fun importedFontsCanBeSelectedAndReplacedWithTheDefault() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ClockFontStore(context)
        val imported = context.assets.open("fonts/noto_sans.ttf").use { store.importStream(it, "Preview font.ttf") }
        try {
            show()
            compose.onNodeWithTag("clock_font_selector").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("clock_font:${imported.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("clock_font:${imported.id}").performClick()
            compose.onNodeWithTag("clock_font_selector").assertTextContains(imported.name)
            compose.onNodeWithTag("clock_style_apply").performClick()
            compose.runOnIdle { assertEquals(imported.id, state.settings.clockStyle.resolvedRegions().face(ClockElement.Clock).fontId) }
            openEditor()
            compose.onNodeWithTag("clock_font_selector").performClick()
            compose.onNodeWithTag("clock_font:default").performClick()
            compose.onNodeWithTag("clock_style_apply").performClick()
            compose.runOnIdle { assertNull(state.settings.clockStyle.resolvedRegions().face(ClockElement.Clock).fontId) }
        } finally { File(context.filesDir, "clock_fonts/${imported.id}").delete() }
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        File(directory, name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
