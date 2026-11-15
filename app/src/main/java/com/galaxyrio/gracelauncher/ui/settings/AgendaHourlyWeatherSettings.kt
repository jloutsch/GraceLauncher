@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AgendaAppearance
import com.galaxyrio.gracelauncher.data.AgendaHourWeatherField
import com.galaxyrio.gracelauncher.data.HourWeatherChartType
import com.galaxyrio.gracelauncher.data.HourWeatherFieldStyle
import com.galaxyrio.gracelauncher.data.weather.WeatherSnapshot
import com.galaxyrio.gracelauncher.ui.weather.labelResource

@Composable
internal fun AgendaHourlyWeatherSettings(draft: AgendaAppearance, snapshot: WeatherSnapshot?, enabled: Boolean,
    haptics: Boolean, field: AgendaHourWeatherField?, onFieldChange: (AgendaHourWeatherField?) -> Unit,
    onChange: (AgendaAppearance) -> Unit, modifier: Modifier = Modifier,
) {
    val defaults = remember { AgendaAppearance() }
    val controlsState = rememberLazyListState()
    val heroSpec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    val heroBounds = remember(heroSpec) { BoundsTransform { _, _ -> heroSpec } }
    val heroShape = ListItemDefaults.segmentedShapes(0, 1).shape
    BoxWithConstraints(modifier) {
        val previewHeight = minOf(320.dp, maxHeight * .4f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AgendaHourlyWeatherPreview(snapshot, draft,
                Modifier.fillMaxWidth().heightIn(max = previewHeight)
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()))
            SharedTransitionLayout(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                AnimatedContent(field, modifier = Modifier.fillMaxSize(),
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "agendaHourlyFieldHero") { activeField ->
                    val visibility = this
                    val paneModifier: @Composable (AgendaHourWeatherField) -> Modifier = { item ->
                        Modifier.sharedBounds(rememberSharedContentState("hourly_field:${item.name}"), visibility,
                            boundsTransform = heroBounds, clipInOverlayDuringTransition = OverlayClip(heroShape))
                    }
                    if (activeField != null) {
                        HourlyWeatherFieldEditor(activeField, draft, enabled && activeField == field, haptics,
                            onChange = { if (activeField == field) onChange(it) },
                            onClose = { if (activeField == field) onFieldChange(null) }, modifier = paneModifier(activeField))
                    } else Column(Modifier.fillMaxSize().padding(bottom = LocalSettingsBottomInset.current)) {
                        val canEdit = enabled && field == null
                        ClockFieldOrder(
                            draft.hourWeatherOrder.filter { it != AgendaHourWeatherField.Chart || draft.hourWeatherChartFields.isNotEmpty() },
                            draft.hourWeatherSelected + draft.hourWeatherChartFields, canEdit, haptics, "agenda_hour_weather_field",
                            label = { stringResource(it.labelResource()) },
                            onSelect = { item, checked -> onChange(draft.copy(
                                hourWeatherSelected = if (checked) draft.hourWeatherSelected + item else draft.hourWeatherSelected - item,
                                // The outer checkbox enables text only, or turns both representations off.
                                hourWeatherChartFields = draft.hourWeatherChartFields - item)) },
                            onReorder = { order ->
                                val complete = if (AgendaHourWeatherField.Chart in order) order else order.toMutableList().apply {
                                    add(draft.hourWeatherOrder.indexOf(AgendaHourWeatherField.Chart).coerceIn(0, size), AgendaHourWeatherField.Chart)
                                }
                                onChange(draft.copy(hourWeatherOrder = complete))
                            }, modifier = Modifier.weight(1f),
                            positionIcons = mapOf(AgendaHourWeatherField.Chart to R.drawable.ms_bar_chart),
                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                            onEdit = { onFieldChange(it) }, fieldModifier = paneModifier, listState = controlsState,
                            beforeFields = {
                                item("chart_options") {
                                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                        SettingsToggleItem(stringResource(R.string.weather_chart_always_expanded), null,
                                            draft.hourWeatherChartsExpanded, 0, 3, "agenda_hour_charts_expanded", canEdit) {
                                            onChange(draft.copy(hourWeatherChartsExpanded = it))
                                        }
                                        ClockSlider(stringResource(R.string.weather_chart_height), draft.hourWeatherChartHeight,
                                            defaults.hourWeatherChartHeight, 60..240, 10, 1, 3, "agenda_hour_chart_height", canEdit,
                                            valueLabel = stringResource(R.string.weather_chart_height_value, draft.hourWeatherChartHeight)) {
                                            onChange(draft.copy(hourWeatherChartHeight = it))
                                        }
                                        ClockSlider(stringResource(R.string.weather_hour_column_width), draft.hourWeatherColumnWidth,
                                            defaults.hourWeatherColumnWidth, AgendaAppearance.HourWeatherColumnWidthRange, 10, 2, 3,
                                            "agenda_hour_column_width", canEdit,
                                            valueLabel = stringResource(R.string.weather_chart_height_value, draft.hourWeatherColumnWidth)) {
                                            onChange(draft.copy(hourWeatherColumnWidth = it))
                                        }
                                        Spacer(Modifier.height(14.dp))
                                    }
                                }
                            },
                        )
                        TextButton(onClick = { onChange(draft.copy(hourWeatherOrder = defaults.hourWeatherOrder,
                            hourWeatherSelected = defaults.hourWeatherSelected, hourWeatherChartFields = defaults.hourWeatherChartFields,
                            hourWeatherStyles = defaults.hourWeatherStyles,
                            hourWeatherChartsExpanded = defaults.hourWeatherChartsExpanded, hourWeatherChartHeight = defaults.hourWeatherChartHeight,
                            hourWeatherColumnWidth = defaults.hourWeatherColumnWidth)) },
                            enabled = canEdit, modifier = Modifier.testTag("agenda_hour_weather_field_reset")) {
                            Text(stringResource(R.string.reset))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyWeatherFieldEditor(field: AgendaHourWeatherField, draft: AgendaAppearance, enabled: Boolean,
    haptics: Boolean, onChange: (AgendaAppearance) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    val style = draft.hourWeatherStyle(field)
    val chartEnabled = field in draft.hourWeatherChartFields
    val hasChartType = field.supportsChart && field != AgendaHourWeatherField.WindDirection && chartEnabled
    val chartCount = if (hasChartType) 4 else 2
    val innerColor = MaterialTheme.colorScheme.surfaceContainer
    fun changeStyle(value: HourWeatherFieldStyle) = onChange(draft.copy(hourWeatherStyles =
        if (value == HourWeatherFieldStyle.defaults(field)) draft.hourWeatherStyles - field
        else draft.hourWeatherStyles + (field to value)))
    Column(modifier.fillMaxSize().testTag("agenda_hour_field_editor:${field.name}")
        .clip(ListItemDefaults.segmentedShapes(0, 1).shape).background(MaterialTheme.colorScheme.surfaceBright)
        .padding(16.dp).padding(bottom = LocalSettingsBottomInset.current)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(field.labelResource()), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClose) { Icon(painterResource(R.drawable.ms_close), stringResource(R.string.done)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SettingsToggleItem(stringResource(R.string.weather_hour_show_text), null,
                    field in draft.hourWeatherSelected, 0, 2, "agenda_hour_show_text", enabled, containerColor = innerColor) {
                    onChange(draft.copy(hourWeatherSelected = if (it) draft.hourWeatherSelected + field else draft.hourWeatherSelected - field))
                }
                SettingsToggleItem(stringResource(R.string.weather_hour_always_show), null, style.alwaysShowText,
                    1, 2, "agenda_hour_text_always_show", enabled, containerColor = innerColor) {
                    changeStyle(style.copy(alwaysShowText = it))
                }
            }
            if (field.supportsChart) Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SettingsToggleItem(stringResource(R.string.weather_hour_show_chart), null,
                    chartEnabled, 0, chartCount, "agenda_hour_show_chart", enabled, containerColor = innerColor) {
                    onChange(draft.copy(hourWeatherChartFields = if (it) draft.hourWeatherChartFields + field else draft.hourWeatherChartFields - field))
                }
                SettingsToggleItem(stringResource(R.string.weather_hour_always_show), null, style.alwaysShowChart,
                    1, chartCount, "agenda_hour_chart_always_show", enabled, containerColor = innerColor) {
                    changeStyle(style.copy(alwaysShowChart = it))
                }
                if (hasChartType) {
                    ClockControlSegment(2, chartCount, "agenda_hour_chart_type", containerColor = innerColor) {
                        OrderedIconChoices(tag = "agenda_hour_chart_type", order = HourWeatherChartType.entries,
                            selected = setOf(style.chartType), enabled = enabled, hapticsEnabled = haptics,
                            labelFor = { stringResource(if (it == HourWeatherChartType.Bar) R.string.weather_chart_bar else R.string.weather_chart_line) },
                            iconFor = { if (it == HourWeatherChartType.Bar) R.drawable.ms_bar_chart else R.drawable.ms_show_chart },
                            onSelect = { type, _ -> changeStyle(style.copy(chartType = type)) })
                    }
                    if (style.chartType == HourWeatherChartType.Line) {
                        SettingsToggleItem(stringResource(R.string.weather_chart_wavy), null, style.wavy,
                            3, chartCount, "agenda_hour_chart_wavy", enabled, containerColor = innerColor) {
                            changeStyle(style.copy(wavy = it))
                        }
                    } else ClockSlider(stringResource(R.string.weather_chart_bar_width), style.barWidth,
                        HourWeatherFieldStyle.defaults(field).barWidth, HourWeatherFieldStyle.BarWidthRange, 2,
                        3, chartCount, "agenda_hour_bar_width", enabled,
                        valueLabel = stringResource(R.string.weather_chart_height_value, style.barWidth),
                        containerColor = innerColor) { changeStyle(style.copy(barWidth = it)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                val defaults = AgendaAppearance()
                onChange(draft.copy(
                    hourWeatherSelected = if (field in defaults.hourWeatherSelected) draft.hourWeatherSelected + field else draft.hourWeatherSelected - field,
                    hourWeatherChartFields = draft.hourWeatherChartFields - field,
                    hourWeatherStyles = draft.hourWeatherStyles - field))
            }, enabled = enabled) { Text(stringResource(R.string.reset)) }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClose) { Text(stringResource(R.string.done)) }
        }
    }
}
