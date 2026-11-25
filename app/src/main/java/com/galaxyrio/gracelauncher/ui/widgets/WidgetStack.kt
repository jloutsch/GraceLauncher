package com.galaxyrio.gracelauncher.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.ui.LauncherUiState

/**
 * Upstream's entry point for every widget stack placement and for its settings preview.
 *
 * Fork: stacks are drawn by the fork's own stack (ui.components.WidgetStack), so swiping wraps around, apps
 * can follow the widgets as pages of icons, and a stack shown in several places hosts each widget once.
 * Upstream's stack height, per-widget backgrounds and editor selection carry over.
 */
@Composable
internal fun WidgetStack(
    folder: LauncherFolder,
    uiState: LauncherUiState,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    onLongPress: () -> Unit = {},
    enabled: Boolean = true,
    gridManaged: Boolean = false,
    selectedKey: String? = null,
    onSelectedKeyChange: ((String) -> Unit)? = null,
    onLaunch: (LauncherApp, Rect) -> Unit = { _, _ -> },
    onDetails: ((LauncherApp) -> Unit)? = null,
    onWallpaper: Boolean = !preview,
) {
    val style = folder.widgetStack ?: return
    val host = LocalWidgetHost.current ?: rememberWidgetHost()
    CompositionLocalProvider(LocalWidgetHost provides host) {
        com.galaxyrio.gracelauncher.ui.components.WidgetStack(folder, uiState, onEdit = onLongPress, modifier = modifier,
            enabled = enabled, onWallpaper = onWallpaper, fillHeight = gridManaged,
            onLaunch = onLaunch, onDetails = if (gridManaged || preview) null else onDetails,
            fixedHeight = style.height.coerceIn(80, 1200), preview = preview,
            selectedKey = selectedKey, onSelectedKeyChange = onSelectedKeyChange)
    }
}
