@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.galaxyrio.gracelauncher.ui.overlays

import com.galaxyrio.gracelauncher.ui.theme.WithoutFontShadow
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.galaxyrio.gracelauncher.ui.theme.FontShadowText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.platform.widthBounds
import com.galaxyrio.gracelauncher.ui.components.LauncherLayout
import com.galaxyrio.gracelauncher.ui.widgets.HomeWidget
import com.galaxyrio.gracelauncher.ui.widgets.WidgetLongPressConfiguration
import com.galaxyrio.gracelauncher.ui.widgets.WidgetEditButtonGroup
import com.galaxyrio.gracelauncher.ui.widgets.rememberWidgetConfigurationAction
import com.galaxyrio.gracelauncher.ui.widgets.WidgetEditOutlineGap
import com.galaxyrio.gracelauncher.ui.widgets.widgetBackgroundColor
import com.galaxyrio.gracelauncher.ui.widgets.rememberHostedWidget
import com.galaxyrio.gracelauncher.ui.settings.IconDesignerColorPicker
import kotlin.math.roundToInt

@Composable
internal fun PopupWidgetControls(
    layout: HomeLayout, onChange: (HomeLayout, Boolean) -> Unit, onDone: () -> Unit,
    onDelete: () -> Unit,
) = WithoutFontShadow {
    val currentLayout by rememberUpdatedState(layout)
    val currentChange by rememberUpdatedState(onChange)
    var latestScale by remember(layout.widgetId) { mutableIntStateOf(layout.popupContentScale) }
    SideEffect { latestScale = layout.popupContentScale }
    var colorOpen by rememberSaveable(layout.widgetId) { mutableStateOf(false) }
    var colorDraft by remember(layout.widgetId) { mutableStateOf(layout.popupBackgroundColor) }
    var colorAnchor by remember { mutableStateOf(Rect.Zero) }
    val colorLabel = stringResource(R.string.popup_widget_background_color)
    val scaleLabel = stringResource(R.string.popup_widget_content_scale, layout.popupContentScale)
    val count = if (layout.popupBackground) 2 else 1
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("popup_widget_controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetEditButtonGroup(layout.widgetFullWidth,
            { onChange(layout.copy(widgetFullWidth = it), true) },
            rememberWidgetConfigurationAction(layout, onDone), onDelete,
            modifier = Modifier.fillMaxWidth().testTag("popup_widget_actions"), background = layout.popupBackground,
            onBackgroundChange = { onChange(layout.copy(popupBackground = it), true) }, onDone = onDone)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            if (layout.popupBackground) SegmentedListItem(
                onClick = {
                    colorDraft = layout.popupBackgroundColor
                    colorOpen = true
                },
                shapes = ListItemDefaults.segmentedShapes(0, count),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                modifier = Modifier.testTag("popup_widget_background_color"),
                leadingContent = {
                    Surface(Modifier.size(28.dp).onGloballyPositioned { colorAnchor = it.boundsInWindow() },
                        shape = CircleShape, color = layout.copy(popupBackgroundAlpha = 1f).widgetBackgroundColor()) {}
                },
                content = { Text(colorLabel) },
                trailingContent = {
                    IconButton(onClick = { onChange(layout.copy(popupBackgroundColor = null), true) },
                        enabled = layout.popupBackgroundColor != null,
                        modifier = Modifier.testTag("popup_widget_background_color_reset")) {
                        Icon(painterResource(R.drawable.ms_restart_alt), stringResource(R.string.clock_reset_value, colorLabel))
                    }
                })
            SegmentedListItem(shapes = ListItemDefaults.segmentedShapes(count - 1, count),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                modifier = Modifier.testTag("popup_widget_scale_segment"), content = {
                    Column {
                        Text(scaleLabel, Modifier.horizontalScroll(rememberScrollState()), maxLines = 1, softWrap = false)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Slider(layout.popupContentScale.toFloat(), {
                                latestScale = it.roundToInt()
                                currentChange(currentLayout.copy(popupContentScale = latestScale), false)
                            }, onValueChangeFinished = { currentChange(currentLayout.copy(popupContentScale = latestScale), true) },
                                valueRange = 50f..150f, modifier = Modifier.weight(1f).testTag("popup_widget_scale"))
                            IconButton(onClick = { latestScale = 100; onChange(layout.copy(popupContentScale = 100), true) },
                                enabled = layout.popupContentScale != 100, modifier = Modifier.testTag("popup_widget_scale_reset")) {
                                Icon(painterResource(R.drawable.ms_restart_alt), stringResource(R.string.clock_reset_value, scaleLabel))
                            }
                        }
                    }
                })
        }
    }
    if (colorOpen) {
        val reveal = remember(layout.widgetId) { ShortcutRevealState() }
        Dialog(onDismissRequest = { reveal.expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            SwipeRevealPanel(colorAnchor, reveal, "popup_widget_color_picker", colorLabel,
                onDismiss = {
                    currentChange(currentLayout.copy(popupBackgroundColor = colorDraft), true)
                    colorOpen = false
                }) { maxHeight ->
                IconDesignerColorPicker(colorLabel, colorDraft, MaterialTheme.colorScheme.primaryContainer.toArgb(), null,
                    onChange = { colorDraft = it; currentChange(currentLayout.copy(popupBackgroundColor = it), false) },
                    onClose = { reveal.expanded = false }, fallback = MaterialTheme.colorScheme.primaryContainer.toArgb(),
                    modifier = Modifier.fillMaxWidth().height(maxHeight))
            }
        }
    }
}

/** A pop-up is a vertical list, so width is centered and height changes the panel's measured size. */
@Composable
internal fun PopupWidgetItem(
    layout: HomeLayout, selected: Boolean, enabled: Boolean, haptics: Boolean,
    maxHeight: Int, onChange: (HomeLayout, Boolean) -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: (Boolean) -> Unit,
    onResizing: (Boolean) -> Unit,
    horizontalInset: Dp = LauncherLayout.PopupIconInset,
    editable: Boolean = true,
) = WidgetLongPressConfiguration {
    val hosted = rememberHostedWidget(layout)
    val density = LocalDensity.current.density
    val currentLayout by rememberUpdatedState(layout)
    val currentChange by rememberUpdatedState(onChange)
    val currentStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentResizing by rememberUpdatedState(onResizing)
    val label = stringResource(R.string.widget_resize)
    val color = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        val available = (maxWidth.value - horizontalInset.value * 2).coerceAtLeast(1f)
        // Horizontal spacing belongs to this container, never to AppWidgetHostView.
        val bounds = hosted.info?.widthBounds(0f,
            density, available.toInt().coerceAtLeast(1), 64.coerceAtMost(available.toInt().coerceAtLeast(1)))
        // List placements fill their content column at 100%, even for providers with
        // a fixed/preferred maximum width. Their resize flag still controls the handles.
        val limitWidth = available
        val minWidth = (bounds?.min?.toFloat() ?: 64f.coerceAtMost(available))
            .coerceAtLeast(available * .2f).coerceAtMost(limitWidth)
        val width = if (layout.widgetFullWidth) maxWidth.value else
            (available * layout.popupWidthPercent.coerceIn(20, 100) / 100f).coerceIn(minWidth, limitWidth)
        val limitHeight = maxHeight.coerceIn(48, 1600).coerceAtMost(hosted.maxHeight)
        val minHeight = hosted.minHeight.coerceAtMost(limitHeight)
        val height = (layout.widgetHeightDp.takeIf { it > 0 } ?: hosted.defaultHeight).coerceIn(minHeight, limitHeight)
        val currentWidth by rememberUpdatedState(width)
        val currentHeight by rememberUpdatedState(height)
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Box(Modifier.width(width.dp).height(height.dp).testTag("popup_widget:${layout.widgetId}")
            .onGloballyPositioned { coordinates = it }) {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                .background(layout.widgetBackgroundColor())) {
                HomeWidget(layout, hosted, height, editing = selected, enabled = enabled, editGesturesEnabled = editable,
                    hapticsEnabled = haptics, onLongPress = {},
                    onDragStart = { _, _ -> currentStart() },
                    onDrag = { _, y -> currentDrag(y) }, onDragEnd = { currentEnd(it) },
                    contentScale = layout.popupContentScale / 100f)
            }
            if (selected) {
                // The native child is disabled while selected. Only this widget's
                // body gets a held-drag recognizer; app rows retain their details action.
                Box(Modifier.matchParentSize().pointerInput(enabled, editable) {
                    if (enabled && editable) {
                        var previous = Offset.Zero
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                previous = coordinates?.localToRoot(it) ?: it
                                currentStart()
                            },
                            onDragEnd = { currentEnd(false) }, onDragCancel = { currentEnd(true) },
                        ) { change, _ ->
                            val root = coordinates?.localToRoot(change.position) ?: change.position
                            change.consume()
                            currentDrag(root.y - previous.y)
                            previous = root
                        }
                    }
                })
                Box(Modifier.matchParentSize().drawBehind {
                    val gap = WidgetEditOutlineGap.toPx()
                    val stroke = 2.dp.toPx()
                    // A full-width view reaches the physical edge: keep its side stroke visible.
                    val left = if (layout.widgetFullWidth) stroke / 2 else -gap
                    val right = if (layout.widgetFullWidth) size.width - stroke / 2 else size.width + gap
                    drawRoundRect(color, topLeft = Offset(left, -gap),
                        size = Size(right - left, size.height + gap * 2),
                        cornerRadius = CornerRadius(16.dp.toPx() + gap), style = Stroke(stroke))
                })
                val horizontal = !layout.widgetFullWidth && minWidth < limitWidth && (hosted.info == null ||
                    hosted.info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0)
                val vertical = minHeight < limitHeight && (hosted.info == null ||
                    hosted.info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0)
                val edges = buildList {
                    if (horizontal) { add("left" to Alignment.CenterStart); add("right" to Alignment.CenterEnd) }
                    if (vertical) { add("top" to Alignment.TopCenter); add("bottom" to Alignment.BottomCenter) }
                }
                for ((edge, alignment) in edges) key(edge) {
                    var handle by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val handleOffset = 20.dp + WidgetEditOutlineGap
                    Box(Modifier.align(alignment).offset(
                        x = when (edge) { "left" -> -handleOffset; "right" -> handleOffset; else -> 0.dp },
                        y = when (edge) { "top" -> -handleOffset; "bottom" -> handleOffset; else -> 0.dp }).size(40.dp)
                        .onGloballyPositioned { handle = it }
                        .testTag("popup_widget_resize:$edge").semantics { contentDescription = label }
                        .pointerInput(enabled, minWidth, limitWidth, available, minHeight, limitHeight) {
                            if (enabled) awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                down.consume()
                                val original = currentLayout
                                var draft = original
                                var w = currentWidth
                                var h = currentHeight.toFloat()
                                var previous = handle?.localToRoot(down.position) ?: down.position
                                var complete = false
                                currentResizing(true)
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (event.changes.count { it.pressed } > 1) break
                                        val root = handle?.localToRoot(change.position) ?: change.position
                                        val delta = (root - previous) / density
                                        previous = root
                                        if (edge == "left" || edge == "right") {
                                            // Centered width: both edges travel half the total size change.
                                            w = (w + delta.x * if (edge == "left") -2f else 2f).coerceIn(minWidth, limitWidth)
                                            draft = draft.copy(popupWidthPercent = (w / available * 100).roundToInt().coerceIn(20, 100))
                                        } else {
                                            h = (h + delta.y * if (edge == "top") -1f else 1f).coerceIn(minHeight.toFloat(), limitHeight.toFloat())
                                            draft = draft.copy(widgetHeightDp = h.roundToInt())
                                        }
                                        change.consume()
                                        currentChange(draft, false)
                                        if (!change.pressed) { complete = true; break }
                                    }
                                } finally {
                                    currentChange(if (complete) draft else original, complete)
                                    currentResizing(false)
                                }
                            }
                        }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(20.dp).background(color, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
                    }
                }
            }
        }
    }
}
