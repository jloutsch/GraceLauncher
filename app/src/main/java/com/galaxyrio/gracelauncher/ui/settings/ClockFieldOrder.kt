@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.ClockAgendaField
import com.galaxyrio.gracelauncher.data.ClockWeatherField
import com.galaxyrio.gracelauncher.ui.overlays.FavoritesReorderState
import com.galaxyrio.gracelauncher.ui.overlays.rememberReorderTranslation
import kotlinx.coroutines.isActive

/** Like the color picker, this panel stays below the live preview and edits the current draft. */
@Composable
internal fun <T : Enum<T>> ClockContentPicker(
    title: String,
    order: List<T>,
    selected: Set<T>,
    enabled: Boolean,
    hapticsEnabled: Boolean,
    tag: String,
    label: @Composable (T) -> String,
    onSelect: (T, Boolean) -> Unit,
    onReorder: (List<T>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    labelShown: (T) -> Boolean? = { null },
    onLabelChange: (T, Boolean) -> Unit = { _, _ -> },
) {
    Column(modifier.fillMaxSize().testTag("${tag}_picker")
        .clip(ListItemDefaults.segmentedShapes(0, 1).shape)
        .background(MaterialTheme.colorScheme.surfaceBright)
        .padding(16.dp).padding(bottom = LocalSettingsBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClose, Modifier.testTag("${tag}_close")) {
                Icon(painterResource(R.drawable.ms_close), stringResource(R.string.done))
            }
        }
        ClockFieldOrder(order, selected, enabled, hapticsEnabled, tag, label, onSelect, onReorder,
            Modifier.weight(1f), labelShown, onLabelChange)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClose, Modifier.testTag("${tag}_done")) { Text(stringResource(R.string.done)) }
        }
    }
}

/** Selection belongs to the checkbox; all fields retain their position when hidden. */
@Composable
internal fun <T : Enum<T>> ClockFieldOrder(
    order: List<T>,
    selected: Set<T>,
    enabled: Boolean,
    hapticsEnabled: Boolean,
    tag: String,
    label: @Composable (T) -> String,
    onSelect: (T, Boolean) -> Unit,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    labelShown: (T) -> Boolean? = { null },
    onLabelChange: (T, Boolean) -> Unit = { _, _ -> },
) {
    val list = rememberLazyListState()
    val fields by rememberUpdatedState(order.associateBy { it.name })
    val commit by rememberUpdatedState(onReorder)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val feedbackEnabled by rememberUpdatedState(hapticsEnabled)
    val keys = order.map { it.name }
    val reorder = remember(list) {
        FavoritesReorderState(list, keys, { saved -> commit(saved.mapNotNull { fields[it] }) }) {
            if (feedbackEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(keys) { reorder.synchronize(keys) }
    val density = LocalDensity.current
    LaunchedEffect(reorder.draggingKey, density) {
        if (reorder.draggingKey == null) return@LaunchedEffect
        val edge = with(density) { 64.dp.toPx() }
        val speed = with(density) { 640.dp.toPx() }
        var previous = withFrameNanos { it }
        while (isActive && reorder.draggingKey != null) {
            val frame = withFrameNanos { it }
            reorder.autoScroll(edge, speed * ((frame - previous) / 1_000_000_000f).coerceIn(0f, .032f))
            previous = frame
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("${tag}_list"),
        state = list,
        // Read-only settings still need to expose fields below the viewport.
        userScrollEnabled = reorder.draggingKey == null,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(reorder.keys, key = { FavoritesReorderState.itemKey(it) }) { key ->
            val field = fields[key] ?: return@items
            val index = reorder.keys.indexOf(key)
            val title = label(field)
            val up = stringResource(R.string.favorites_move_up)
            val down = stringResource(R.string.favorites_move_down)
            val dragging = reorder.draggingKey == key
            val offset by rememberReorderTranslation(reorder, key)
            val background by animateColorAsState(
                if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                label = "clockFieldLift",
            )
            Row(
                modifier = Modifier.then(if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging || reorder.drop?.key == key) 1f else 0f)
                    .graphicsLayer { translationY = offset }
                    .fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(background)
                    .testTag("$tag:$key")
                    .semantics {
                        customActions = buildList {
                            if (enabled && index > 0) add(CustomAccessibilityAction(up) { reorder.moveBy(key, -1) })
                            if (enabled && index < reorder.keys.lastIndex) add(CustomAccessibilityAction(down) { reorder.moveBy(key, 1) })
                        }
                    }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = field in selected,
                    onCheckedChange = { onSelect(field, it) },
                    enabled = enabled,
                    modifier = Modifier.testTag("${tag}_selected:$key").semantics { contentDescription = title },
                )
                Text(title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
                labelShown(field)?.let { shown ->
                    IconToggleButton(
                        checked = shown,
                        onCheckedChange = { onLabelChange(field, it) },
                        enabled = enabled,
                        modifier = Modifier.testTag("${tag}_label:$key"),
                    ) {
                        Icon(painterResource(if (shown) R.drawable.ms_label else R.drawable.ms_label_off),
                            stringResource(if (shown) R.string.clock_hide_field_label else R.string.clock_show_field_label, title),
                            Modifier.size(24.dp))
                    }
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { menu = true },
                        enabled = enabled,
                        modifier = Modifier.testTag("${tag}_drag:$key")
                            .pointerInput(reorder, key, enabled) {
                                if (enabled) detectDragGestures(
                                    onDragStart = { reorder.start(key) },
                                    onDragEnd = { reorder.finish(false) },
                                    onDragCancel = { reorder.finish(true) },
                                ) { change, amount -> change.consume(); reorder.drag(amount.y) }
                            },
                    ) {
                        Icon(painterResource(R.drawable.ms_drag_indicator),
                            stringResource(R.string.favorites_reorder, title), Modifier.size(24.dp))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(up) }, enabled = enabled && index > 0,
                            onClick = { reorder.moveBy(key, -1); menu = false })
                        DropdownMenuItem(text = { Text(down) }, enabled = enabled && index < reorder.keys.lastIndex,
                            onClick = { reorder.moveBy(key, 1); menu = false })
                    }
                }
            }
        }
    }
}

@Composable
internal fun ClockAgendaField.label(): String = stringResource(when (this) {
    ClockAgendaField.Title -> R.string.clock_agenda_field_title
    ClockAgendaField.Countdown -> R.string.clock_agenda_field_countdown
    ClockAgendaField.Time -> R.string.clock_agenda_field_time
    ClockAgendaField.Date -> R.string.clock_agenda_field_date
    ClockAgendaField.Duration -> R.string.clock_agenda_field_duration
    ClockAgendaField.Location -> R.string.clock_agenda_field_location
    ClockAgendaField.Color -> R.string.clock_agenda_field_color
    ClockAgendaField.Calendar -> R.string.clock_agenda_field_calendar
})

@Composable
internal fun ClockWeatherField.label(): String = stringResource(when (this) {
    ClockWeatherField.Icon -> R.string.clock_weather_field_icon
    ClockWeatherField.Temperature -> R.string.clock_weather_field_temperature
    ClockWeatherField.Description -> R.string.clock_weather_field_description
    ClockWeatherField.FeelsLike -> R.string.clock_weather_field_feels_like
    ClockWeatherField.Humidity -> R.string.clock_weather_field_humidity
    ClockWeatherField.Wind -> R.string.clock_weather_field_wind
    ClockWeatherField.AirQuality -> R.string.clock_weather_field_air_quality
    ClockWeatherField.UV -> R.string.clock_weather_field_uv
    ClockWeatherField.Location -> R.string.clock_weather_field_location
    ClockWeatherField.HighLow -> R.string.clock_weather_field_high_low
    ClockWeatherField.UpdatedAt -> R.string.clock_weather_field_updated_at
})
