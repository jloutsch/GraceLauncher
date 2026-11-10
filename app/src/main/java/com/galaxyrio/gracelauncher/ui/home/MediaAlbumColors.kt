package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.galaxyrio.gracelauncher.data.IconColor
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.ThemeMode
import com.galaxyrio.gracelauncher.data.launcherColorScheme
import com.galaxyrio.gracelauncher.ui.theme.resolvedFontColor

@Composable
internal fun rememberMediaAlbumContainerColor(seed: Int?, settings: LauncherSettings): Color {
    val dark = when (settings.darkMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val fallback = MaterialTheme.colorScheme.primaryContainer
    return remember(seed, dark, settings.paletteStyle, fallback) {
        seed?.let { launcherColorScheme(it, dark, settings.paletteStyle).primaryContainer } ?: fallback
    }
}

/** FromApp is the stored artwork-derived source; fixed colors and managed references retain their meaning. */
@Composable
internal fun mediaCoverBorderColor(choice: IconColor, albumContainer: Color): Color =
    if (choice.fromApp) Color(choice.resolve(MaterialTheme.colorScheme.primary.toArgb(), appColor = albumContainer.toArgb()))
    else resolvedFontColor(choice)
