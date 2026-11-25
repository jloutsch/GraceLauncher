package com.galaxyrio.gracelauncher.ui.widgets

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.ui.theme.LocalFontColors

/** Editing chrome is outside the content; it never changes the provider's measured size. */
internal val WidgetEditOutlineGap = 6.dp

/** Overlay avoidance is a visual translation, never part of the persisted page geometry. */
@Composable
internal fun rememberWidgetEditorOffset(
    active: Boolean, widgetTop: Float?, toolbarBottom: Float, gap: Float, interacting: Boolean,
): State<Float> {
    val desired = if (active && widgetTop != null && toolbarBottom > 0f)
        (toolbarBottom + gap - widgetTop).coerceAtLeast(0f) else 0f
    val offset = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(desired, interacting, spec) {
        // Cancelling the previous effect also freezes an in-flight transition at
        // the moment a drag/resize starts; the held widget stays under the finger.
        if (!interacting) offset.animateTo(desired, spec)
    }
    return offset.asState()
}

@Composable
internal fun HomeLayout.widgetBackgroundColor(): Color {
    if (!popupBackground) return Color.Transparent
    val scheme = MaterialTheme.colorScheme
    return Color(popupBackgroundColor?.resolve(scheme.primaryContainer.toArgb(),
        colorScheme = scheme, fontColors = LocalFontColors.current) ?: scheme.primaryContainer.toArgb())
        .copy(alpha = popupBackgroundAlpha.coerceIn(0f, 1f))
}
