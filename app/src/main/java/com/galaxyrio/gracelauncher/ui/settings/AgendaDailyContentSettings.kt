@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.weather.labelResource

internal enum class AgendaDailyContentPage { Weather, Tasks, Calendar }

@Composable
internal fun AgendaDailyContentSettings(uiState: LauncherUiState, draft: AgendaAppearance, enabled: Boolean,
    weatherEnabled: Boolean, calendarEnabled: Boolean, tasksEnabled: Boolean, haptics: Boolean,
    page: AgendaDailyContentPage?, onPageChange: (AgendaDailyContentPage?) -> Unit,
    onChange: (AgendaAppearance) -> Unit, modifier: Modifier = Modifier,
) {
    val controlsState = rememberLazyListState()
    val heroSpec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    val heroBounds = remember(heroSpec) { BoundsTransform { _, _ -> heroSpec } }
    val heroShape = ListItemDefaults.segmentedShapes(0, 1).shape
    BoxWithConstraints(modifier) {
        val previewHeight = minOf(320.dp, maxHeight * .4f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AgendaDailyPreview(uiState, draft, Modifier.fillMaxWidth().heightIn(max = previewHeight)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()))
            SharedTransitionLayout(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                AnimatedContent(page, modifier = Modifier.fillMaxSize(),
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "agendaDailyContentHero") { activePage ->
                    val visibility = this
                    val paneModifier: @Composable (AgendaDailyContentPage) -> Modifier = { field ->
                        Modifier.sharedBounds(rememberSharedContentState("agenda_content:${field.name}"), visibility,
                            boundsTransform = heroBounds, clipInOverlayDuringTransition = OverlayClip(heroShape))
                    }
                    if (activePage != null) {
                        AgendaDailyContentPicker(activePage, draft, enabled && activePage == page,
                            weatherEnabled, calendarEnabled, tasksEnabled, haptics,
                            onChange = { if (activePage == page) onChange(it) },
                            onClose = { if (activePage == page) onPageChange(null) },
                            modifier = paneModifier(activePage))
                    } else LazyColumn(Modifier.fillMaxSize().testTag("agenda_daily_content_controls"),
                        state = controlsState,
                        contentPadding = PaddingValues(bottom = 24.dp + LocalSettingsBottomInset.current),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        item("daily_choices") {
                            ClockControlSegment(0, 1, "agenda_header_elements") {
                                val order = draft.headerOrder.filterNot { it in draft.headerRightAligned } + listOf(null) +
                                    draft.headerOrder.filter { it in draft.headerRightAligned }
                                OrderedIconChoices(tag = "agenda_header", order = order, selected = draft.headerSelected,
                                    enabled = enabled && page == null, hapticsEnabled = haptics,
                                    disabledElements = if (weatherEnabled) emptySet() else setOf(AgendaHeaderField.Weather),
                                    labelFor = { it.label() }, iconFor = { when (it) {
                                        AgendaHeaderField.Date -> R.drawable.ms_calendar_month
                                        AgendaHeaderField.Countdown -> R.drawable.ms_hourglass_empty
                                        AgendaHeaderField.Weather -> R.drawable.ms_partly_cloudy_day
                                    } },
                                    onSelect = { field, selected -> onChange(draft.copy(headerSelected = if (selected)
                                        draft.headerSelected + field else draft.headerSelected - field)) },
                                    onReorder = { reordered -> onChange(draft.copy(headerOrder = reordered.filterNotNull(),
                                        headerRightAligned = reordered.drop(reordered.indexOf(null) + 1).filterNotNull().toSet())) })
                            }
                        }
                        item("content_gap") { Spacer(Modifier.height(12.dp)) }
                        AgendaDailyContentPage.entries.forEachIndexed { index, field ->
                            item("content:${field.name}") {
                                val available = when (field) {
                                    AgendaDailyContentPage.Weather -> weatherEnabled
                                    AgendaDailyContentPage.Tasks -> tasksEnabled
                                    AgendaDailyContentPage.Calendar -> calendarEnabled
                                }
                                SettingsActionItem(field.title(), null, index, 4, "agenda_daily_content:${field.name}",
                                    enabled = enabled && available && page == null, modifier = paneModifier(field)) {
                                    onPageChange(field)
                                }
                            }
                        }
                        item("tasks_first") {
                            SettingsToggleItem(stringResource(R.string.agenda_tasks_first), null, draft.tasksFirst, 3, 4,
                                "agenda_tasks_first", enabled = enabled && tasksEnabled && calendarEnabled && page == null) {
                                onChange(draft.copy(tasksFirst = it))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaDailyContentPage.title(): String = stringResource(when (this) {
    AgendaDailyContentPage.Weather -> R.string.clock_weather_contents
    AgendaDailyContentPage.Tasks -> R.string.clock_task_contents
    AgendaDailyContentPage.Calendar -> R.string.agenda_calendar_content
})

@Composable
private fun AgendaDailyContentPicker(page: AgendaDailyContentPage, draft: AgendaAppearance, enabled: Boolean,
    weatherEnabled: Boolean, calendarEnabled: Boolean, tasksEnabled: Boolean, haptics: Boolean,
    onChange: (AgendaAppearance) -> Unit, onClose: () -> Unit, modifier: Modifier,
) {
    val defaults = remember { AgendaAppearance() }
    when (page) {
        AgendaDailyContentPage.Weather -> ClockContentPicker(stringResource(R.string.clock_weather_contents),
            draft.dayWeatherOrder, draft.dayWeatherSelected, enabled && weatherEnabled, haptics, "agenda_day_weather_field",
            label = { stringResource(it.labelResource()) },
            onSelect = { field, checked -> onChange(draft.copy(dayWeatherSelected = if (checked)
                draft.dayWeatherSelected + field else draft.dayWeatherSelected - field)) },
            onReorder = { onChange(draft.copy(dayWeatherOrder = it)) }, onClose = onClose, modifier = modifier,
            labelShown = { if (it.supportsLabel) it in draft.dayWeatherLabeledFields else null },
            onLabelChange = { field, shown -> onChange(draft.copy(dayWeatherLabeledFields =
                if (shown) draft.dayWeatherLabeledFields + field else draft.dayWeatherLabeledFields - field)) },
            onReset = { onChange(draft.copy(dayWeatherOrder = defaults.dayWeatherOrder,
                dayWeatherSelected = defaults.dayWeatherSelected, dayWeatherLabeledFields = defaults.dayWeatherLabeledFields)) })
        AgendaDailyContentPage.Calendar -> {
            val content = draft.calendarContent
            fun change(value: ClockAgenda) = onChange(draft.copy(calendarContent = value))
            ClockContentPicker(stringResource(R.string.agenda_calendar_content), content.order, content.selected,
                enabled && calendarEnabled, haptics, "agenda_calendar_field", label = { it.label() },
                onSelect = { field, checked -> change(content.copy(selected = if (checked) content.selected + field else content.selected - field)) },
                onReorder = { change(content.copy(order = it)) }, onClose = onClose, modifier = modifier,
                rightAligned = content.rightAligned,
                onAlignedReorder = { order, aligned -> change(content.copy(order = order, rightAligned = aligned)) },
                primaryLine = draft.calendarPrimaryFields,
                onLineReorder = { order, selected, primary, aligned -> onChange(draft.copy(
                    calendarContent = content.copy(order = order, selected = selected, rightAligned = aligned),
                    calendarPrimaryFields = primary)) },
                onReset = { onChange(draft.copy(calendarContent = content.copy(
                    order = defaults.calendarContent.order, selected = defaults.calendarContent.selected,
                    rightAligned = defaults.calendarContent.rightAligned),
                    calendarPrimaryFields = defaults.calendarPrimaryFields)) })
        }
        AgendaDailyContentPage.Tasks -> {
            val content = draft.taskContent
            fun change(value: ClockTasks) = onChange(draft.copy(taskContent = value))
            ClockContentPicker(stringResource(R.string.clock_task_contents),
                content.order, content.selected, enabled && tasksEnabled, haptics, "agenda_tasks_field", label = { it.label() },
                onSelect = { field, checked -> change(content.copy(selected = if (checked) content.selected + field else content.selected - field)) },
                onReorder = { change(content.copy(order = it)) }, onClose = onClose, modifier = modifier,
                rightAligned = content.rightAligned,
                onAlignedReorder = { order, aligned -> change(content.copy(order = order, rightAligned = aligned)) },
                primaryLine = draft.taskPrimaryFields,
                onLineReorder = { order, selected, primary, aligned -> onChange(draft.copy(
                    taskContent = content.copy(order = order, selected = selected, rightAligned = aligned),
                    taskPrimaryFields = primary)) },
                onReset = { onChange(draft.copy(taskContent = content.copy(
                    order = defaults.taskContent.order, selected = defaults.taskContent.selected,
                    rightAligned = defaults.taskContent.rightAligned),
                    taskPrimaryFields = defaults.taskPrimaryFields)) })
        }
    }
}

@Composable
private fun AgendaHeaderField.label(): String = stringResource(when (this) {
    AgendaHeaderField.Date -> R.string.clock_agenda_field_date
    AgendaHeaderField.Countdown -> R.string.clock_agenda_field_countdown
    AgendaHeaderField.Weather -> R.string.settings_weather
})
