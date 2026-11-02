package com.galaxyrio.gracelauncher

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.IconColorSource
import com.galaxyrio.gracelauncher.data.IconDisplaySettings
import com.galaxyrio.gracelauncher.data.IconDisplayStyle
import com.galaxyrio.gracelauncher.data.IconDotColor
import com.galaxyrio.gracelauncher.data.MaterialColorRole
import com.galaxyrio.gracelauncher.data.FontColors
import com.galaxyrio.gracelauncher.data.FontColorEntry
import org.junit.Assert.*
import org.junit.Test

class IconColorSourceTest {
    private val theme = 0xFF663399.toInt()
    private val app = 0xFF11AA44.toInt()

    @Test fun sourcesStayLiveAndMissingAppColorFallsBackToTheme() {
        assertEquals(theme, IconColor.Theme.resolve(theme, app))
        assertEquals(app, IconColor.Theme.resolve(app, theme))
        assertEquals(app, IconColor.FromApp.resolve(theme, app))
        assertEquals(theme, IconColor.FromApp.resolve(theme))
        val fixed = IconColor(0xFF123456.toInt())
        assertEquals(fixed.argb, fixed.resolve(theme, app))
        assertEquals(fixed.argb, fixed.resolve(app, theme))
    }

    @Test fun oldDynamicIsThemeAndFixedRgbIsPreserved() {
        val migrated = IconColor.fromStored(app, dynamic = true)
        assertEquals(IconColor.Theme, migrated)
        assertEquals(theme, migrated.resolve(theme, app))
        assertEquals(IconColorSource.Theme, migrated.source)
        assertEquals(IconColor(app), IconColor.fromStored(app))
        assertEquals(IconColor.FromApp, IconColor.fromStored(0, fromApp = true))
    }

    @Test fun dotLegacySourcesMapToTheCommonPickerWithoutChangingSizeOrShape() {
        val dots = IconDisplaySettings(style = IconDisplayStyle.Dot, dotSize = 21)
        assertEquals(IconColor.FromApp, dots.color)
        assertEquals(IconColor.Theme, dots.copy(dotColor = IconDotColor.Theme).color)
        assertEquals(IconColor.Theme, dots.copy(dotColor = IconDotColor.Custom,
            customColor = IconColor.fromStored(app, dynamic = true)).color)
        listOf(IconColor.Theme, IconColor.FromApp, IconColor(app),
            IconColor.Theme.copy(hueShift = -30, brightnessShift = 20),
            IconColor.FromApp.copy(saturationShift = -15, brightnessShift = -10)).forEach { color ->
            val changed = dots.withColor(color)
            assertEquals(color, changed.color)
            assertEquals(dots.dotSize, changed.dotSize)
            assertEquals(dots.dotShape, changed.dotShape)
            assertEquals(dots.style, changed.style)
        }
    }

    @Test fun materialRoleFollowsTheSelectedRoleWhenTheThemeChanges() {
        val first = lightColorScheme(primary = Color.Red, onPrimary = Color.White, secondary = Color.Blue)
        val next = first.copy(primary = Color.Green, onPrimary = Color.Black, secondary = Color.Yellow)
        val chosen = IconColor(0, materialRole = MaterialColorRole.Secondary)
        assertEquals(first.secondary.toArgb(), chosen.resolve(theme, colorScheme = first))
        assertEquals(next.secondary.toArgb(), chosen.resolve(theme, colorScheme = next))
        assertEquals(theme, chosen.resolve(theme))
        assertEquals(IconColorSource.Theme, chosen.source)
        val fixed = IconColor(first.secondary.toArgb())
        assertEquals(first.secondary.toArgb(), fixed.resolve(theme, colorScheme = next))
    }

    @Test fun storedRoleAndDotOverridesRetainTheRoleAndRelativeAdjustments() {
        val chosen = IconColor(0, materialRole = MaterialColorRole.OnPrimary, brightnessShift = -10)
        val restored = IconColor.fromStored(0, materialRole = MaterialColorRole.OnPrimary, brightnessShift = -10)
        assertEquals(chosen, restored)
        assertEquals(chosen, IconDisplaySettings().withColor(chosen).normalized().color)
        val scheme = lightColorScheme(onPrimary = Color.White)
        assertEquals(IconColor.Theme.copy(brightnessShift = -10).resolve(Color.White.toArgb()),
            restored.resolve(theme, colorScheme = scheme))
    }

    @Test fun sharedReferenceAdjustmentsLayerOnTheLivePaletteColor() {
        val palette = FontColors(entries = FontColors.defaults() + FontColorEntry("accent", "Accent",
            IconColor(0, materialRole = MaterialColorRole.Secondary, hueShift = 60)))
        val choice = IconColor(0, fontColorId = "accent", hueShift = 120, brightnessShift = -20).normalized()
        val first = lightColorScheme(secondary = Color.Red)
        val next = first.copy(secondary = Color.Blue)
        assertEquals(0xFF00CCCC.toInt(), choice.resolve(theme, colorScheme = first, fontColors = palette))
        assertEquals(0xFFCCCC00.toInt(), choice.resolve(theme, colorScheme = next, fontColors = palette))
        assertEquals("accent", choice.fontColorId)
        assertEquals(120, choice.hueShift)
        assertEquals(-20, choice.brightnessShift)
    }

    @Test fun wallpaperReferenceOffsetsUseTheRuntimeColorAndRemainDynamic() {
        val scheme = lightColorScheme(onSurface = Color.White)
        val choice = IconColor.fromStored(0, fontColorId = FontColors.WallpaperId, hueShift = 120, brightnessShift = -20)
        assertEquals(0xFF00CC00.toInt(), choice.resolve(theme, colorScheme = scheme, fontColors = FontColors(),
            wallpaperColor = Color.Red.toArgb()))
        assertEquals(0xFFCC0000.toInt(), choice.resolve(theme, colorScheme = scheme, fontColors = FontColors(),
            wallpaperColor = Color.Blue.toArgb()))
        assertEquals("Missing runtime data keeps the nominal palette fallback", 0xFFCCCCCC.toInt(),
            choice.resolve(theme, colorScheme = scheme, fontColors = FontColors()))
        assertEquals(FontColors.WallpaperId, choice.fontColorId)
        val fixed = IconColor(0xFF123456.toInt())
        assertEquals(fixed.argb, fixed.resolve(theme, colorScheme = scheme, fontColors = FontColors(),
            wallpaperColor = Color.Blue.toArgb()))
    }
}
