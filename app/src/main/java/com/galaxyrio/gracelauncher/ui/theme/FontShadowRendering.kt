package com.galaxyrio.gracelauncher.ui.theme

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.galaxyrio.gracelauncher.data.FontShadow
import com.galaxyrio.gracelauncher.data.FontShadowOverride
import com.galaxyrio.gracelauncher.data.FontShadowPreset
import com.galaxyrio.gracelauncher.data.FontColors
import kotlin.math.ceil

internal val LocalFontShadow = staticCompositionLocalOf { FontShadow() }
// Only wallpaper content opts in. Filled surfaces and settings remain plain by default.
internal val LocalFontShadowEnabled = staticCompositionLocalOf { false }

internal data class ResolvedFontShadow(val shadow: Shadow = Shadow.None, val spread: Float = 0f)

@Composable
internal fun resolvedFontShadow(override: FontShadowOverride? = null): ResolvedFontShadow {
    if (!LocalFontShadowEnabled.current) return ResolvedFontShadow()
    val value = (override?.takeIf { it.enabled }?.shadow ?: LocalFontShadow.current).normalized()
    if (value.preset == FontShadowPreset.None || value.opacity == 0) return ResolvedFontShadow()
    return resolveFontShadow(value, MaterialTheme.colorScheme, LocalFontColors.current,
        LocalWallpaperFontColor.current, LocalDensity.current.density)
}

internal fun resolveFontShadow(value: FontShadow, scheme: ColorScheme, fontColors: FontColors,
    wallpaperColor: Color, density: Float): ResolvedFontShadow {
    val safe = value.normalized()
    if (safe.preset == FontShadowPreset.None || safe.opacity == 0) return ResolvedFontShadow()
    val color = Color(safe.color.resolve(scheme.primary.toArgb(), colorScheme = scheme,
        fontColors = fontColors, wallpaperColor = wallpaperColor.toArgb()))
    return ResolvedFontShadow(Shadow(color.copy(alpha = color.alpha * safe.opacity / 100f),
        Offset(safe.offsetX * density, safe.offsetY * density), safe.blur * density), safe.spread * density)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun Typography.withFontShadow(shadow: Shadow): Typography = copy(
    displayLarge = displayLarge.copy(shadow = shadow), displayMedium = displayMedium.copy(shadow = shadow),
    displaySmall = displaySmall.copy(shadow = shadow), headlineLarge = headlineLarge.copy(shadow = shadow),
    headlineMedium = headlineMedium.copy(shadow = shadow), headlineSmall = headlineSmall.copy(shadow = shadow),
    titleLarge = titleLarge.copy(shadow = shadow), titleMedium = titleMedium.copy(shadow = shadow),
    titleSmall = titleSmall.copy(shadow = shadow), bodyLarge = bodyLarge.copy(shadow = shadow),
    bodyMedium = bodyMedium.copy(shadow = shadow), bodySmall = bodySmall.copy(shadow = shadow),
    labelLarge = labelLarge.copy(shadow = shadow), labelMedium = labelMedium.copy(shadow = shadow),
    labelSmall = labelSmall.copy(shadow = shadow),
    displayLargeEmphasized = displayLargeEmphasized.copy(shadow = shadow),
    displayMediumEmphasized = displayMediumEmphasized.copy(shadow = shadow),
    displaySmallEmphasized = displaySmallEmphasized.copy(shadow = shadow),
    headlineLargeEmphasized = headlineLargeEmphasized.copy(shadow = shadow),
    headlineMediumEmphasized = headlineMediumEmphasized.copy(shadow = shadow),
    headlineSmallEmphasized = headlineSmallEmphasized.copy(shadow = shadow),
    titleLargeEmphasized = titleLargeEmphasized.copy(shadow = shadow),
    titleMediumEmphasized = titleMediumEmphasized.copy(shadow = shadow),
    titleSmallEmphasized = titleSmallEmphasized.copy(shadow = shadow),
    bodyLargeEmphasized = bodyLargeEmphasized.copy(shadow = shadow),
    bodyMediumEmphasized = bodyMediumEmphasized.copy(shadow = shadow),
    bodySmallEmphasized = bodySmallEmphasized.copy(shadow = shadow),
    labelLargeEmphasized = labelLargeEmphasized.copy(shadow = shadow),
    labelMediumEmphasized = labelMediumEmphasized.copy(shadow = shadow),
    labelSmallEmphasized = labelSmallEmphasized.copy(shadow = shadow),
)

@Composable
internal fun ProvideFontShadow(override: FontShadowOverride, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFontShadow provides if (override.enabled) override.shadow else LocalFontShadow.current,
        content = content)
}

/** Filled surfaces and settings stay unshadowed; wallpaper previews can opt in inside this scope. */
@Composable
internal fun WithoutFontShadow(content: @Composable () -> Unit) {
    val typography = MaterialTheme.typography
    val plain = remember(typography) { typography.withFontShadow(Shadow.None) }
    CompositionLocalProvider(LocalFontShadowEnabled provides false) {
        MaterialTheme(typography = plain, content = content)
    }
}

/** A cached glyph mask gives text real spread without changing its fill, metrics or hit target. */
internal class FontShadowMaskCache {
    private var key: Any? = null
    private var blur = -1f
    private var spread = -1f
    private var mask: Bitmap? = null
    private var originX = 0f
    private var originY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        canvas: Canvas, contentKey: Any, width: Float, height: Float, inkPadding: Float,
        effect: ResolvedFontShadow, drawMask: (Canvas, Float) -> Unit,
    ) {
        if (effect.shadow == Shadow.None || effect.shadow.color.alpha == 0f) return
        if (key != contentKey || blur != effect.shadow.blurRadius || spread != effect.spread) {
            key = contentKey
            blur = effect.shadow.blurRadius
            spread = effect.spread
            mask = null
            val padding = ceil(inkPadding + spread + 2f).toInt().coerceAtLeast(2)
            val bitmapWidth = ceil(width).toInt().coerceAtLeast(1) + padding * 2
            val bitmapHeight = ceil(height).toInt().coerceAtLeast(1) + padding * 2
            // Very large paragraphs should not allocate an unbounded software surface.
            if (bitmapWidth.toLong() * bitmapHeight > 8_000_000L) return
            val source = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            try {
                val target = Canvas(source)
                target.translate(padding.toFloat(), padding.toFloat())
                drawMask(target, spread)
                val offset = IntArray(2)
                val filter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                }
                mask = source.extractAlpha(filter, offset)
                originX = offset[0] - padding.toFloat()
                originY = offset[1] - padding.toFloat()
            } finally {
                // Only this temporary bitmap is recycled; masks recorded by a hardware canvas stay immutable.
                source.recycle()
            }
        }
        val bitmap = mask ?: return
        paint.colorFilter = PorterDuffColorFilter(effect.shadow.color.toArgb(), PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, originX + effect.shadow.offset.x, originY + effect.shadow.offset.y, paint)
    }
}
