package com.galaxyrio.gracelauncher.ui.overlays

import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal data class LauncherSheetLayout(
    val windowHeight: Dp,
    val topInset: Dp,
    val bottomInset: Dp,
    val contentInsets: WindowInsets,
)

internal val LocalLauncherSheetLayout = staticCompositionLocalOf<LauncherSheetLayout?> { null }

/** Read before creating the modal: a new dialog starts without measured insets. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun launcherSheetLayout(hideStatusBar: Boolean): LauncherSheetLayout {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    var safeInsets = WindowInsets.navigationBarsIgnoringVisibility
        .union(WindowInsets.captionBarIgnoringVisibility).union(WindowInsets.displayCutout)
    if (!hideStatusBar) safeInsets = safeInsets.union(WindowInsets.statusBarsIgnoringVisibility)
    val left = safeInsets.getLeft(density, direction)
    val top = safeInsets.getTop(density)
    val right = safeInsets.getRight(density, direction)
    val bottom = safeInsets.getBottom(density)
    // Keep geometry independent of bar visibility while focus moves between the two windows.
    return remember(windowHeight, density, left, top, right, bottom) {
        LauncherSheetLayout(
            windowHeight = windowHeight,
            topInset = with(density) { top.toDp() },
            bottomInset = with(density) { bottom.toDp() },
            contentInsets = WindowInsets(left = left, top = top, right = right, bottom = 0),
        )
    }
}

@Composable
internal fun panelWindowHeight(): Dp = LocalLauncherSheetLayout.current?.windowHeight
    ?: with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

/** ModalBottomSheet owns a separate window; changing the activity window doesn't affect its bars. */
@Composable
@Suppress("DEPRECATION")
internal fun LauncherSheetSystemBars(hideStatusBar: Boolean) {
    val view = LocalView.current
    val window = remember(view) {
        generateSequence(view) { it.parent as? View }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    }
    DisposableEffect(window, hideStatusBar) {
        window?.let {
            // ModalBottomSheet already configures edge-to-edge and bar icon appearance.
            // Apply visibility once, without the desktop's resume/focus/auto-hide machinery.
            if (Build.VERSION.SDK_INT < 35) it.statusBarColor = Color.TRANSPARENT
            it.navigationBarColor = Color.TRANSPARENT
            it.navigationBarDividerColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                it.isStatusBarContrastEnforced = false
                it.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                if (hideStatusBar) hide(WindowInsetsCompat.Type.statusBars())
                else show(WindowInsetsCompat.Type.statusBars())
            }
        }
        // Only the sheet's window was changed; it is discarded when the modal closes.
        onDispose { }
    }
}

/** This space belongs inside scroll content, so the final action can scroll clear of system controls. */
@Composable
internal fun launcherSheetBottomInset(): Dp = checkNotNull(LocalLauncherSheetLayout.current) {
    "Launcher sheet layout must come from the host window"
}.bottomInset
