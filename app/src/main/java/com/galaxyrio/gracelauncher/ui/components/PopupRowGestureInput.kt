package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.data.PopupGesture
import com.galaxyrio.gracelauncher.data.PopupGestureAction
import com.galaxyrio.gracelauncher.data.ShortcutsFoldersSettings

/** Shared by the whole row and each notification's separate, stationary touch region. */
@Composable
internal fun Modifier.popupRowGestureInput(
    key: String,
    enabled: Boolean,
    settings: ShortcutsFoldersSettings,
    resolve: (PopupGesture) -> PopupGestureAction?,
    perform: (PopupGesture) -> Unit,
    prepare: () -> Unit,
    onPopupDrag: (Boolean) -> Unit,
    onPopupDragEnd: (Boolean) -> Unit,
    onPull: (Float, Boolean) -> Unit,
    canStart: (Offset) -> Boolean = { true },
): Modifier {
    val currentResolve by rememberUpdatedState(resolve)
    val currentPerform by rememberUpdatedState(perform)
    val currentPrepare by rememberUpdatedState(prepare)
    val currentDrag by rememberUpdatedState(onPopupDrag)
    val currentEnd by rememberUpdatedState(onPopupDragEnd)
    val currentPull by rememberUpdatedState(onPull)
    val currentCanStart by rememberUpdatedState(canStart)
    val leftEnabled = resolve(PopupGesture.SwipeLeft) != null
    val rightEnabled = resolve(PopupGesture.SwipeRight) != null
    return pointerInput(key, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            currentPrepare()
        }
    }.pointerInput(key, enabled, leftEnabled, rightEnabled, settings) {
        if (!enabled || (!leftEnabled && !rightEnabled)) return@pointerInput
        val popup = PopupSwipeIntent(reversalSlop = 8.dp.toPx())
        var direction: PopupGesture? = null
        var action: PopupGestureAction? = null
        var travel = 0f
        fun reset() {
            popup.reset(); direction = null; action = null; travel = 0f
            currentPull(0f, false)
        }
        try {
            detectPopupDragGestures(
                extraDistance = settings.popupSwipeDistanceDp.coerceIn(0, 48).dp.toPx(),
                maxAngleDegrees = settings.popupSwipeAngleDegrees.coerceIn(10, 90),
                leftEnabled = leftEnabled, rightEnabled = rightEnabled,
                canStart = { currentCanStart(it) },
                onDragStart = { reset(); currentPull(0f, true) },
                onDragCancel = { if (popup.revealed) currentEnd(false); reset() },
                onDragEnd = {
                    if (popup.revealed) currentEnd(popup.expanded)
                    else if (travel >= 40.dp.toPx()) direction?.let(currentPerform)
                    reset()
                },
            ) { change, amount ->
                change.consume()
                if (direction == null) {
                    direction = if (amount < 0f) PopupGesture.SwipeLeft else PopupGesture.SwipeRight
                    action = currentResolve(requireNotNull(direction))
                }
                val sign = if (direction == PopupGesture.SwipeLeft) -1f else 1f
                if (action == PopupGestureAction.OpenPopup) {
                    popup.drag(amount * sign)?.let(currentDrag)
                } else {
                    travel = (travel + amount * sign).coerceAtLeast(0f)
                    currentPull(travel * sign, true)
                }
            }
        } finally {
            if (popup.revealed) currentEnd(false)
            reset()
        }
    }
}

internal fun resistedSignedPopupPull(distance: Float, resistance: Float): Float =
    if (distance <= 0f) resistedPopupPull(-distance, resistance) else -resistedPopupPull(distance, resistance)
