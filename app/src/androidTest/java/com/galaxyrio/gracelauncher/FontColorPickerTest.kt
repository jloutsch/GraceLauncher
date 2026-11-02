package com.galaxyrio.gracelauncher

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.galaxyrio.gracelauncher.data.FontColors
import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.IconColorSource
import com.galaxyrio.gracelauncher.data.MaterialColorRole
import com.galaxyrio.gracelauncher.ui.settings.IconDesignerColorPicker
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import com.galaxyrio.gracelauncher.ui.theme.LocalWallpaperFontColor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FontColorPickerTest {
    @get:Rule val compose = createComposeRule()
    private var choice by mutableStateOf<IconColor?>(null)
    private var primary by mutableStateOf(Color(0xFFB04020))
    private lateinit var scheme: ColorScheme
    private val wallpaper = Color(0xFFE0D0C0)

    private fun show(initial: IconColor? = null, palette: FontColors? = FontColors()) {
        choice = initial
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false, fontColors = palette ?: FontColors()) {
                val colors = lightColorScheme(primary = primary)
                SideEffect { scheme = colors }
                MaterialTheme(colorScheme = colors) {
                    CompositionLocalProvider(LocalWallpaperFontColor provides wallpaper) {
                        IconDesignerColorPicker("Font color", choice, colors.primary.toArgb(), null,
                            onChange = { choice = it }, onClose = {}, fontColors = palette)
                    }
                }
            }
        }
    }

    private fun source(name: String) = compose.onNodeWithTag("icon_designer_color_source:$name", useUnmergedTree = true)
    private fun setOffset(tag: String, value: Float) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }

    @Test fun inheritedFontColorSelectsWallpaperAndFixedHidesSavedColors() {
        show()
        source("Theme").assertIsSelected()
        compose.onNodeWithTag("material_color_presets").assertDoesNotExist()
        compose.onNodeWithTag("icon_designer_hex").assertDoesNotExist()
        listOf("hue", "saturation", "brightness").forEach {
            compose.onNodeWithTag("icon_designer_${it}_shift", useUnmergedTree = true).assertExists()
        }
        compose.runOnIdle { assertNull(choice) }

        compose.onNodeWithTag("font_color_presets").performScrollTo().performClick()
        compose.onNodeWithTag("saved_font_color:${FontColors.WallpaperId}").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(IconColor(0, fontColorId = FontColors.WallpaperId), choice) }

        source("Fixed").performScrollTo().performClick()
        source("Fixed").assertIsSelected()
        compose.onNodeWithTag("font_color_presets").assertDoesNotExist()
        compose.onNodeWithTag("material_color_presets").assertDoesNotExist()
        compose.onNodeWithTag("icon_designer_hex").performScrollTo().assertTextContains("E0D0C0", substring = true)
        compose.runOnIdle { assertEquals(IconColor(wallpaper.toArgb()), choice) }

        source("Theme").performScrollTo().performClick()
        compose.onNodeWithTag("font_color_presets").assertExists()
        compose.runOnIdle { assertEquals(IconColor(0, fontColorId = FontColors.WallpaperId), choice) }
    }

    @Test fun savedColorOffsetsKeepTheirReferenceAcrossThemeChangesUntilFixedIsSelected() {
        val palette = FontColors()
        show(palette = palette)
        compose.onNodeWithTag("font_color_presets").performScrollTo().performClick()
        compose.onNodeWithTag("saved_font_color:${FontColors.PrimaryId}").performScrollTo().performClick()
        setOffset("icon_designer_hue_shift", 30f)
        setOffset("icon_designer_saturation_shift", -25f)
        setOffset("icon_designer_brightness_shift", -15f)
        val reference = IconColor(0, fontColorId = FontColors.PrimaryId,
            hueShift = 30, saturationShift = -25, brightnessShift = -15)
        var originalResolved = 0
        compose.runOnIdle {
            assertEquals(reference, choice)
            originalResolved = reference.resolve(scheme.primary.toArgb(), colorScheme = scheme, fontColors = palette)
            primary = Color(0xFF2040B0)
        }
        var currentResolved = 0
        compose.runOnIdle {
            assertEquals(reference, choice)
            currentResolved = reference.resolve(scheme.primary.toArgb(), colorScheme = scheme, fontColors = palette)
            assertNotEquals(originalResolved, currentResolved)
        }

        source("Fixed").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(IconColor(currentResolved), choice)
            assertEquals(IconColorSource.Fixed, choice?.source)
        }
        compose.onNodeWithTag("font_color_presets").assertDoesNotExist()
    }

    @Test fun paletteEditorStillOffersDirectMaterialRoles() {
        show(initial = IconColor(0, materialRole = MaterialColorRole.OnSurface), palette = null)
        source("Theme").assertIsSelected()
        compose.onNodeWithTag("font_color_presets").assertDoesNotExist()
        compose.onNodeWithTag("material_color_presets").performScrollTo().performClick()
        compose.onNodeWithTag("material_color_role:Secondary").performScrollTo().performClick()
        setOffset("icon_designer_hue_shift", 20f)
        compose.runOnIdle {
            assertEquals(IconColor(0, materialRole = MaterialColorRole.Secondary, hueShift = 20), choice)
            assertNull(choice?.fontColorId)
        }
    }
}
