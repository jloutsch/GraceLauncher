@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.galaxyrio.gracelauncher.R
import kotlin.math.abs

private class IconButtonDrop(val key: String, val center: Float)

/** Match the clock's long-press reordering: animate visuals while keeping hit-test slots fixed. */
@Composable
internal fun <T : Enum<T>> ReorderableIconButtonGroup(order: List<T>, selected: Set<T>, enabled: Boolean,
    hapticsEnabled: Boolean, tag: String, label: @Composable (T) -> String, icon: (T) -> Int,
    onSelect: (T) -> Unit, onReorder: (List<T>) -> Unit) {
    val latestOrder by rememberUpdatedState(order)
    val reorder by rememberUpdatedState(onReorder)
    val haptics = LocalHapticFeedback.current
    val rects = remember(tag) { mutableStateMapOf<String, Rect>() }
    var dragging by remember(tag) { mutableStateOf<String?>(null) }
    var drop by remember(tag) { mutableStateOf<IconButtonDrop?>(null) }
    var center by remember(tag) { mutableFloatStateOf(0f) }
    var originalOrder by remember(tag) { mutableStateOf<List<T>?>(null) }
    val placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    fun feedback() { if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun move(key: String, to: Int): Boolean {
        val current = latestOrder
        val from = current.indexOfFirst { it.name == key }
        if (!enabled || from < 0 || to !in current.indices || from == to) return false
        reorder(current.toMutableList().apply { add(to, removeAt(from)) })
        feedback()
        return true
    }
    val left = stringResource(R.string.clock_move_left)
    val right = stringResource(R.string.clock_move_right)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically) {
            order.forEachIndexed { index, option -> key(option.name) {
                val id = option.name
                val isDragging = dragging == id
                val released = drop?.takeIf { it.key == id }
                var consumedDrop by remember { mutableStateOf<IconButtonDrop?>(null) }
                val position = rects[id]?.center?.x?.let { target ->
                    val animated = remember { Animatable(target) }
                    LaunchedEffect(target, isDragging, released, placementSpec) {
                        if (isDragging) animated.stop() else {
                            if (released != null && consumedDrop !== released) {
                                animated.snapTo(released.center)
                                consumedDrop = released
                            }
                            animated.animateTo(target, placementSpec)
                            if (released != null && drop === released) drop = null
                        }
                    }
                    animated
                }
                val currentPosition by rememberUpdatedState(position)
                Box(Modifier.weight(1f).heightIn(min = 48.dp)
                    .zIndex(if (isDragging || released != null) 1f else 0f)
                    .onGloballyPositioned { coordinates ->
                        val point = coordinates.positionInParent()
                        rects[id] = Rect(point.x, point.y, point.x + coordinates.size.width, point.y + coordinates.size.height)
                    }
                    .pointerInput(tag, id, enabled) {
                        if (!enabled) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                rects[id]?.let {
                                    originalOrder = latestOrder
                                    center = currentPosition?.value ?: it.center.x
                                    drop = null
                                    dragging = id
                                    feedback()
                                }
                            },
                            onDragCancel = {
                                // Leaving the LazyColumn after a finished drag must not reset its order.
                                if (dragging == id) {
                                    val previous = originalOrder
                                    drop = IconButtonDrop(id, center)
                                    dragging = null
                                    originalOrder = null
                                    if (previous != null) reorder(previous)
                                }
                            },
                            onDragEnd = {
                                if (dragging == id) {
                                    drop = IconButtonDrop(id, center)
                                    dragging = null
                                    originalOrder = null
                                }
                            },
                            onDrag = { change, amount ->
                                if (dragging == id) {
                                    change.consume()
                                    center += amount.x
                                    val from = latestOrder.indexOfFirst { it.name == id }
                                    val target = latestOrder.indices.minByOrNull {
                                        abs(center - (rects[latestOrder[it].name]?.center?.x ?: center))
                                    }
                                    if (target != null) {
                                        val targetCenter = rects[latestOrder[target].name]?.center?.x ?: center
                                        if ((target > from && amount.x > 0 && center >= targetCenter) ||
                                            (target < from && amount.x < 0 && center <= targetCenter)) move(id, target)
                                    }
                                }
                            },
                        )
                    }, contentAlignment = Alignment.Center) {
                    ToggleButton(checked = option in selected, onCheckedChange = { onSelect(option) }, enabled = enabled,
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            order.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .graphicsLayer {
                                val target = rects[id]?.center?.x ?: 0f
                                val release = drop?.takeIf { it.key == id }
                                val visible = when {
                                    dragging == id -> center
                                    release != null && consumedDrop !== release -> release.center
                                    else -> position?.value ?: target
                                }
                                translationX = visible - target
                            }.testTag("$tag:$id").semantics {
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction(left) { move(id, index - 1) })
                                    if (index < order.lastIndex) add(CustomAccessibilityAction(right) { move(id, index + 1) })
                                }
                            }) {
                        Icon(painterResource(icon(option)), label(option), Modifier.size(24.dp))
                    }
                }
            } }
        }
    }
}
