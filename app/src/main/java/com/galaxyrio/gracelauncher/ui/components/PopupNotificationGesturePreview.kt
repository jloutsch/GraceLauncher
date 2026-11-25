package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.data.PopupGesture
import com.galaxyrio.gracelauncher.data.PopupGestureAction
import com.galaxyrio.gracelauncher.data.ShortcutsFoldersSettings

@Composable
internal fun PopupNotificationGesturePreview(
    key: String, revision: Long, enabled: Boolean, settings: ShortcutsFoldersSettings,
    resolve: (PopupGesture) -> PopupGestureAction?, perform: (PopupGesture, Rect) -> Unit,
    onDrag: (Rect, Boolean) -> Unit, onDragEnd: (Boolean) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onClick: () -> Unit, onLongClick: (() -> Unit)?, content: @Composable () -> Unit,
) {
    var bounds by remember(key) { mutableStateOf(Rect.Zero) }
    var pull by remember(key) { mutableFloatStateOf(0f) }
    var dragging by remember(key) { mutableStateOf(false) }
    val resistance = with(LocalDensity.current) { 72.dp.toPx() }
    val offset by animateFloatAsState(resistedSignedPopupPull(pull, resistance),
        animationSpec = if (dragging) snap() else spring(dampingRatio = .58f, stiffness = 420f), label = "notificationPull")
    Box(Modifier.fillMaxWidth().testTag("notification_gesture:$key")
        .onGloballyPositioned { bounds = it.boundsInWindow(); onBoundsChanged(bounds) }
        .popupRowGestureInput("$key:$revision", enabled, settings, resolve, { perform(it, bounds) }, prepare = {},
            onPopupDrag = { onDrag(bounds, it) }, onPopupDragEnd = onDragEnd,
            onPull = { distance, active -> pull = distance; dragging = active })
        .combinedClickable(enabled = enabled,
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = onClick, onLongClick = onLongClick,
            onDoubleClick = if (settings.doubleTap.selected.isNotEmpty()) ({ perform(PopupGesture.DoubleTap, bounds) }) else null)) {
        Column(Modifier.fillMaxWidth().graphicsLayer { translationX = offset }) { content() }
    }
}
