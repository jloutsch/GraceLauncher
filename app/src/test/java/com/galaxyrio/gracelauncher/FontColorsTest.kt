package com.galaxyrio.gracelauncher

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.galaxyrio.gracelauncher.data.FontColorEntry
import com.galaxyrio.gracelauncher.data.FontColors
import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.MaterialColorRole
import com.galaxyrio.gracelauncher.data.WallpaperBrightness
import org.junit.Assert.*
import org.junit.Test

class FontColorsTest {
    @Test fun defaultsStayBoundToMaterialRoles() {
        val colors = FontColors()
        assertEquals(listOf(FontColors.DefaultId, FontColors.ThemeId, FontColors.WallpaperId,
            FontColors.PrimaryId, FontColors.SecondaryId, FontColors.TertiaryId), colors.entries.map { it.id })
        assertEquals(MaterialColorRole.OnSurface, colors.defaultColor.materialRole)
        assertEquals(MaterialColorRole.OnPrimary, colors.themeColor.materialRole)
        assertEquals(MaterialColorRole.OnSurface, colors.color(FontColors.WallpaperId)?.materialRole)
        assertEquals(MaterialColorRole.Primary, colors.color(FontColors.PrimaryId)?.materialRole)
        assertEquals(MaterialColorRole.Secondary, colors.color(FontColors.SecondaryId)?.materialRole)
        assertEquals(MaterialColorRole.Tertiary, colors.color(FontColors.TertiaryId)?.materialRole)
        assertEquals(WallpaperBrightness.Auto, colors.wallpaperBrightness)
        assertFalse(colors.wallpaperColorAccess)
    }

    @Test fun seededThemeEntriesAreEditableAndStayDeletedAfterNormalization() {
        val fixed = IconColor(Color.Magenta.toArgb())
        val colors = FontColors()
        val modified = colors.copy(entries = colors.entries.filterNot { it.id == FontColors.PrimaryId }.map {
            if (it.id == FontColors.SecondaryId) it.copy(name = "Reading", color = fixed) else it
        }).normalized()
        assertNull(modified.color(FontColors.PrimaryId))
        assertEquals(fixed, modified.color(FontColors.SecondaryId))
        assertEquals("Reading", modified.entries.first { it.id == FontColors.SecondaryId }.name)
        assertEquals(listOf("default", "theme", "wallpaper", "secondary", "tertiary"), modified.entries.map { it.id })
        assertEquals(modified, modified.normalized())
        val coreOnly = colors.copy(entries = colors.entries.take(3)).normalized()
        assertEquals(listOf("default", "theme", "wallpaper"), coreOnly.entries.map { it.id })
    }

    @Test fun normalizationRestoresBuiltInsAndPreservesCustomOrderAndValues() {
        val fixed = IconColor(0xFF123456.toInt())
        val colors = FontColors(listOf(
            FontColorEntry("first", "  Reading  ", fixed),
            FontColorEntry(FontColors.ThemeId, "ignored", fixed),
            FontColorEntry("second", "Emphasis", IconColor.Theme),
            FontColorEntry("first", "Duplicate", IconColor.Theme),
            FontColorEntry("", "Missing ID", fixed),
            FontColorEntry("unnamed", "  ", fixed),
        )).normalized()
        assertEquals(listOf("default", "theme", "wallpaper", "first", "second"), colors.entries.map { it.id })
        assertEquals(MaterialColorRole.OnSurface, colors.defaultColor.materialRole)
        assertEquals(fixed, colors.themeColor)
        assertEquals("Reading", colors.entries[3].name)
        assertEquals(fixed, colors.color("first"))
        assertNull(colors.color("missing"))
        assertEquals(colors, colors.normalized())
    }

    @Test fun wallpaperEntryIsImmutableWhileItsPreferencesSurviveNormalization() {
        val colors = FontColors(entries = listOf(
            FontColorEntry(FontColors.WallpaperId, "Changed", IconColor(Color.Red.toArgb())),
            FontColorEntry("custom", "Custom", IconColor(Color.Blue.toArgb())),
        ), wallpaperBrightness = WallpaperBrightness.Dark, wallpaperColorAccess = true)
        val normalized = colors.normalized()
        assertEquals(FontColors.defaults()[2], normalized.entries[2])
        assertEquals(MaterialColorRole.OnSurface, colors.color(FontColors.WallpaperId)?.materialRole)
        assertEquals(WallpaperBrightness.Dark, normalized.wallpaperBrightness)
        assertTrue(normalized.wallpaperColorAccess)
        assertEquals("custom", normalized.entries.last().id)
        assertEquals(normalized, normalized.normalized())
    }

    @Test fun paletteValuesCannotFormReferenceCycles() {
        val colors = FontColors(listOf(
            FontColorEntry("first", "First", IconColor(0xFF123456.toInt(), fontColorId = "second")),
            FontColorEntry("second", "Second", IconColor(0xFF654321.toInt(), fontColorId = "first")),
        )).normalized()
        assertTrue(colors.entries.all { it.color.fontColorId == null })
        assertEquals(0xFF123456.toInt(), colors.color("first")!!.argb)
    }

    @Test fun sharedReferencesFollowPaletteEditsAndThemeChangesWhileFixedOverridesStayIndependent() {
        val firstScheme = lightColorScheme(onSurface = Color.Black, secondary = Color.Blue)
        val nextScheme = firstScheme.copy(onSurface = Color.White, secondary = Color.Yellow)
        val original = FontColors()
        val reference = IconColor(0, fontColorId = FontColors.DefaultId)
        val fixedOverride = IconColor(Color.Red.toArgb())
        fun resolve(color: IconColor, colors: FontColors, nextTheme: Boolean = false): Int =
            color.resolve(0, colorScheme = if (nextTheme) nextScheme else firstScheme, fontColors = colors)

        assertEquals(Color.Black.toArgb(), resolve(reference, original))
        assertEquals(Color.White.toArgb(), resolve(reference, original, nextTheme = true))
        val edited = original.copy(entries = original.entries.map {
            if (it.id == FontColors.DefaultId) it.copy(color = IconColor(Color.Green.toArgb())) else it
        })
        assertEquals(Color.Green.toArgb(), resolve(reference, edited))
        val dynamic = edited.copy(entries = edited.entries +
            FontColorEntry("reading", "Reading", IconColor(0, materialRole = MaterialColorRole.Secondary)))
        val customReference = IconColor(0, fontColorId = "reading")
        assertEquals(Color.Blue.toArgb(), resolve(customReference, dynamic))
        assertEquals(Color.Yellow.toArgb(), resolve(customReference, dynamic, nextTheme = true))
        assertEquals(Color.Red.toArgb(), resolve(fixedOverride, original))
        assertEquals(Color.Red.toArgb(), resolve(fixedOverride, dynamic, nextTheme = true))
    }
}
