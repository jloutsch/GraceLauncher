package com.galaxyrio.gracelauncher.ui.theme

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import com.galaxyrio.gracelauncher.data.IconDisplaySettings
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.data.IconColor
import com.materialkolor.palettes.TonalPalette
import java.util.function.Consumer

data class LauncherAppearance(val darkText: Boolean = false, val themedIcons: Boolean = false, val iconSize: Int = 100,
    val iconDisplay: IconDisplaySettings = IconDisplaySettings(), val textColor: Color? = null) {
    val text: Color get() = textColor ?: if (darkText) Color(0xFF202025) else Color(0xFFFAF9FE)
    val textShadow: Shadow get() = if (text.luminance() < 0.5f) Shadow.None
        else Shadow(Color.Black.copy(alpha = 0.32f), blurRadius = 4f)
}

val LocalLauncherAppearance = staticCompositionLocalOf { LauncherAppearance() }

/** The settings preview, app list and search resolve the same saved overlay color. */
internal fun LauncherAppearance.wallpaperTint(primary: Color, choice: IconColor? = null,
    colorScheme: androidx.compose.material3.ColorScheme? = null): Color {
    // Adjust primary's tone, not a black scrim. Dark text gets a matching light tint.
    // Change tone within the already selected theme palette; do not generate a
    // second Tonal Spot scheme that discards the user's chosen style/chroma.
    val automatic = Color(TonalPalette.fromInt(primary.toArgb()).tone(if (darkText) 95 else 10))
    return choice?.resolve(primary.toArgb(), colorScheme = colorScheme)?.let(::Color) ?: automatic
}

/** One resolver for every row: a per-app override wins, else the shared setting. */
fun LauncherApp.effectiveDisplay(appearance: LauncherAppearance): IconDisplaySettings =
    iconDisplay ?: appearance.iconDisplay

@Composable
@Suppress("UNUSED_PARAMETER") // Retain the legacy call signature while font colors replace wallpaper text modes.
fun rememberLauncherAppearance(mode: WallpaperTextMode, themedIcons: Boolean, iconSize: Int = 100,
    iconDisplay: IconDisplaySettings = IconDisplaySettings()): LauncherAppearance {
    val text = resolvedFontColor(onWallpaper = true)
    return LauncherAppearance(
        darkText = text.luminance() < 0.5f,
        themedIcons = themedIcons,
        iconSize = iconSize.coerceIn(80, 150),
        iconDisplay = iconDisplay,
        textColor = text,
    )
}

@Composable
fun rememberBatteryPercent(): Int? {
    val context = LocalContext.current
    var percent by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                percent = if (level >= 0 && scale > 0) (100 * level / scale) else null
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}

/** The system may disable cross-window blur at runtime (e.g. battery saver). */
@Composable
internal fun rememberWallpaperBlurAvailable(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(WindowManager::class.java) }
    var available by remember(manager) {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager?.isCrossWindowBlurEnabled == true)
    }
    DisposableEffect(manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager != null) {
            val listener = Consumer<Boolean> { available = it }
            manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
            onDispose { manager.removeCrossWindowBlurEnabledListener(listener) }
        } else onDispose { }
    }
    return available
}

/** Blur the wallpaper through HOME's transparent window, not its foreground content. */
@Composable
internal fun WallpaperBlur(radius: Dp) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? Activity)?.window
    if (window == null || view.isInEditMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }.coerceIn(0, 150)
    // Background blur is part of DecorView, unlike FLAG_BLUR_BEHIND's separate
    // zero-alpha DimLayer, which can stay invisible after returning via HOME.
    // Android owns surface attachment and blur availability; the UI owns only
    // the radius (0 on HOME, animated with the app list's visibility otherwise).
    SideEffect { window.setBackgroundBlurRadius(radiusPx) }
    DisposableEffect(window) {
        onDispose { window.setBackgroundBlurRadius(0) }
    }
}
