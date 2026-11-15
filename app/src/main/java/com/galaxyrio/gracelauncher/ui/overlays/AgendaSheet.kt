package com.galaxyrio.gracelauncher.ui.overlays

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.tasks.TaskItem
import com.galaxyrio.gracelauncher.data.tasks.TasksStatus
import com.galaxyrio.gracelauncher.data.tasks.visibleTaskItems
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import com.galaxyrio.gracelauncher.data.weather.WeatherConnectionMode
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.components.eventRemainingText
import com.galaxyrio.gracelauncher.ui.components.taskRemainingText
import com.galaxyrio.gracelauncher.ui.home.ClockAgendaContent
import com.galaxyrio.gracelauncher.ui.home.ClockTaskContent
import com.galaxyrio.gracelauncher.ui.home.clockAgendaFields
import com.galaxyrio.gracelauncher.ui.home.clockTaskFields
import com.galaxyrio.gracelauncher.ui.weather.HourlyWeather
import com.galaxyrio.gracelauncher.ui.weather.weatherAgendaDays
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

// Keep one compact column for action icons and markers, and one for agenda text.
private val AgendaItemInset = 4.dp
private val AgendaLeadingWidth = 28.dp
private val AgendaContentGap = 4.dp

@Composable
fun AgendaSheet(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onRequestCalendar: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val zone = ZoneId.systemDefault()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val today = now.atZone(zone).toLocalDate()
    val appearance = uiState.settings.agendaAppearance
    val widget = uiState.popups[AgendaWidgetKey].orEmpty().firstNotNullOfOrNull { it.widget }
    val weatherEnabled = uiState.settings.weatherEnabled && !uiState.isLoadingSettings && !uiState.settingsLoadFailed
    val weather = uiState.weather.snapshot.takeIf { weatherEnabled }
    val forecastDays = uiState.settings.weatherForecastDays
    val forecasts = remember(weather?.daily, today, forecastDays) {
        weather?.daily.orEmpty().filter { it.date >= today && it.date < today.plusDays(forecastDays.toLong()) }.associateBy { it.date }
    }
    val events = uiState.events.takeIf { uiState.settings.calendarAgenda }.orEmpty()
    val taskPreferences = uiState.settings.calendar.tasksOrg
    val tasksEnabled = taskPreferences.enabled && !uiState.isLoadingSettings && !uiState.settingsLoadFailed
    val tasks = remember(uiState.tasks.tasks, taskPreferences, tasksEnabled, zone, locale) {
        if (tasksEnabled) visibleTaskItems(uiState.tasks.tasks, taskPreferences, zone, locale) else emptyList()
    }
    val undatedTasks = remember(tasks) { tasks.filter { it.dueAt == null } }
    val calendarDays = uiState.settings.calendar.daysAhead
    val bottomInset = launcherSheetBottomInset()
    val deprioritizeAllDay = uiState.settings.calendar.deprioritizeAllDay
    val groups = remember(events, tasks, forecasts, today, zone, forecastDays, calendarDays, deprioritizeAllDay,
        appearance.tasksFirst, appearance.showDailyContent) {
        if (appearance.showDailyContent) {
            val eventDays = weatherAgendaDays(events, forecasts.keys.toList(), today, forecastDays, zone,
                calendarDayCount = calendarDays, deprioritizeAllDay = deprioritizeAllDay)
            agendaDaysWithTasks(eventDays, tasks, zone, deprioritizeAllDay, appearance.tasksFirst)
        } else {
            // Keep today's independent sections without creating daily headers, entries, or empty day gaps.
            mapOf(today to emptyList<AgendaEntry>())
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = panelWindowHeight()).padding(horizontal = 30.dp).testTag("agenda_sheet")) {
        val title = when (appearance.title) {
            AgendaTitle.Time -> DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date.from(now))
            AgendaTitle.Quote -> stringResource(R.string.settings_coming_soon)
            AgendaTitle.Text -> appearance.titleText
        }
        Text(title, Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 24.dp),
            fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if ((appearance.showDailyContent && uiState.settings.calendarAgenda && uiState.scheduleStatus == ScheduleStatus.Loading && events.isEmpty()) ||
            ((appearance.showDailyContent || appearance.showUndatedTasks) && tasksEnabled &&
                uiState.tasks.status == TasksStatus.Loading && tasks.isEmpty()) ||
            ((appearance.showDailyContent || appearance.showHourlyWeather) && weatherEnabled &&
                uiState.weather.status == WeatherStatus.Loading && weather == null)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("agenda_list"), state = listState,
            contentPadding = PaddingValues(bottom = 20.dp + bottomInset)) {
            if (widget != null) item(key = "agenda_widget") {
                PopupWidgetItem(widget, selected = false, enabled = true, haptics = false,
                    maxHeight = panelWindowHeight().value.toInt(), onChange = { _, _ -> },
                    onDragStart = {}, onDrag = {}, onDragEnd = {}, onResizing = {},
                    horizontalInset = 0.dp, editable = false)
                Spacer(Modifier.height(12.dp))
            }
            groups.forEach { (date, dayEntries) ->
                if (appearance.showDailyContent) item(key = "day:$date") {
                    AgendaDayHeader(date, today, forecasts[date], weather?.current.takeIf { date == today },
                        appearance, weatherEnabled, weather)
                }
                if (date == today) {
                    if (appearance.showHourlyWeather && weather != null) item(key = "today_weather") {
                        HourlyWeather(weather, now, appearance = appearance, onOpenWeatherApp = actions.openWeatherApp)
                    }
                    else if (appearance.showHourlyWeather && weatherEnabled && uiState.weather.status != WeatherStatus.Loading) item(key = "weather_unavailable") {
                        WeatherConnectionNotice(uiState.weather.status, actions,
                            gadgetbridge = uiState.settings.weatherConnection.mode == WeatherConnectionMode.Gadgetbridge)
                    }
                    if (appearance.showUndatedTasks && undatedTasks.isNotEmpty()) {
                        items(undatedTasks, key = { "undated_task:${it.id}" }) { task ->
                            AgendaTask(task, now, appearance.taskContent, appearance.taskPrimaryFields,
                                completing = task.id in uiState.completingTaskIds,
                                onComplete = { actions.completeTask(task) }, onClick = { actions.openTask(task) })
                        }
                        item(key = "space:undated_tasks") { Spacer(Modifier.height(12.dp)) }
                    }
                    if ((appearance.showDailyContent || appearance.showUndatedTasks) && tasksEnabled) item(key = "tasks_connection") {
                        TasksConnectionNotice(uiState.tasks.status, actions)
                    }
                    // Calendar access is independent of weather access: keep the forecast visible
                    // when the user has not connected a calendar, or its provider fails.
                    if (appearance.showDailyContent && uiState.settings.calendarAgenda) {
                        when (uiState.scheduleStatus) {
                            ScheduleStatus.PermissionRequired -> item(key = "calendar_permission") {
                                Text(stringResource(R.string.calendar_permission_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = onRequestCalendar) { Text(stringResource(R.string.connect_calendar)) }
                            }
                            ScheduleStatus.Error -> item(key = "calendar_error") {
                                Text(stringResource(R.string.calendar_error))
                                TextButton(onClick = actions.refreshAgenda) { Text(stringResource(R.string.retry)) }
                            }
                            else -> Unit
                        }
                    }
                    if (uiState.settings.calendarAgenda || tasksEnabled) item(key = "agenda_create") {
                        AgendaCreateButton(uiState, actions, Modifier.padding(horizontal = AgendaItemInset, vertical = 8.dp))
                    }
                }
                items(dayEntries, key = { "$date:${it.key}" }) { entry ->
                    when (entry) {
                        is AgendaEntry.Calendar -> AgendaEvent(entry.event, now, appearance.calendarContent,
                            appearance.calendarPrimaryFields) { actions.openEvent(entry.event) }
                        is AgendaEntry.Task -> AgendaTask(entry.task, now, appearance.taskContent, appearance.taskPrimaryFields,
                            completing = entry.task.id in uiState.completingTaskIds,
                            onComplete = { actions.completeTask(entry.task) }, onClick = { actions.openTask(entry.task) })
                    }
                }
                if (appearance.showDailyContent) item(key = "space:$date") { Spacer(Modifier.height(20.dp)) }
            }
            val tasksReady = !tasksEnabled || uiState.tasks.status == TasksStatus.Ready
            if (appearance.showDailyContent && uiState.settings.calendarAgenda && events.isEmpty() && tasks.isEmpty() && tasksReady &&
                uiState.scheduleStatus == ScheduleStatus.Ready) item {
                Text(pluralStringResource(R.plurals.calendar_no_upcoming_events, calendarDays, calendarDays),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if ((appearance.showDailyContent || appearance.showUndatedTasks) && tasksEnabled &&
                !uiState.settings.calendarAgenda && tasks.isEmpty() && tasksReady) item {
                Text(stringResource(R.string.tasks_no_unfinished), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TasksConnectionNotice(status: TasksStatus, actions: LauncherActions) {
    val (message, label, onClick) = when (status) {
        TasksStatus.Disabled, TasksStatus.Loading, TasksStatus.Ready -> return
        TasksStatus.NotInstalled -> Triple(R.string.tasks_not_installed, R.string.tasks_get_app, actions.getTasks)
        TasksStatus.PermissionRequired -> Triple(R.string.tasks_permission_required, R.string.tasks_connect, actions.requestTasksAccess)
        TasksStatus.UpdateRequired -> Triple(R.string.tasks_update_required, R.string.tasks_update_app, actions.getTasks)
        TasksStatus.Error -> Triple(R.string.tasks_error, R.string.retry, actions.refreshTasks)
    }
    Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = onClick, modifier = Modifier.testTag("agenda_tasks_connection_action")) {
        Text(stringResource(label))
    }
}

@Composable
private fun WeatherConnectionNotice(status: WeatherStatus, actions: LauncherActions, gadgetbridge: Boolean) {
    if (gadgetbridge) {
        if (status == WeatherStatus.Disabled || status == WeatherStatus.Loading) return
        Text(stringResource(if (status == WeatherStatus.Error) R.string.weather_gadgetbridge_unavailable
            else R.string.weather_gadgetbridge_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(stringResource(R.string.weather_gadgetbridge_setup),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        return
    }
    val (message, label, onClick) = when (status) {
        WeatherStatus.Disabled, WeatherStatus.Loading -> return
        WeatherStatus.NotInstalled -> Triple(R.string.weather_settings_not_installed, R.string.weather_settings_install, actions.installBreezyWeather)
        WeatherStatus.PermissionRequired -> Triple(R.string.weather_settings_permission, R.string.weather_settings_allow, actions.requestWeatherAccess)
        WeatherStatus.UnsupportedVersion -> Triple(R.string.weather_settings_incompatible, R.string.weather_settings_install, actions.installBreezyWeather)
        WeatherStatus.NoLocations -> Triple(R.string.weather_settings_no_locations, R.string.weather_settings_open, actions.openBreezyWeather)
        WeatherStatus.NoWeather, WeatherStatus.Ready -> Triple(R.string.weather_settings_no_data, R.string.weather_settings_open, actions.openBreezyWeather)
        WeatherStatus.Error -> Triple(R.string.weather_settings_unavailable, R.string.retry, actions.refreshWeather)
    }
    Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    TextButton(onClick = onClick, modifier = Modifier.testTag("weather_connection_action")) { Text(stringResource(label)) }
}

@Composable
internal fun AgendaTask(task: TaskItem, now: Instant, content: ClockTasks, primaryFields: Set<ClockTaskField>,
    completing: Boolean, onComplete: () -> Unit, onClick: () -> Unit,
    interactive: Boolean = true,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val clockPattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    val timeFormat = remember(locale, clockPattern, zone) { DateTimeFormatter.ofPattern(clockPattern, locale).withZone(zone) }
    val time = task.dueAt?.let { due ->
        if (task.dueAllDay) stringResource(R.string.all_day) else timeFormat.format(due)
    }
    val remaining = taskRemainingText(task, now).takeIf { it.isNotEmpty() }
    val listName = task.listName.ifBlank { stringResource(R.string.tasks_org) }
    val details = listOfNotNull(time, remaining, listName).joinToString(" · ")
    val completeLabel = stringResource(R.string.tasks_mark_complete, task.title)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).testTag("agenda_task:${task.id}")
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = AgendaItemInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(AgendaLeadingWidth), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Checkbox(
                    checked = completing,
                    onCheckedChange = if (interactive) ({ checked -> if (checked) onComplete() }) else null,
                    enabled = !completing && !task.isReadOnly,
                    modifier = Modifier.size(AgendaLeadingWidth).testTag("agenda_task_complete:${task.id}")
                        .semantics { contentDescription = completeLabel },
                    colors = CheckboxDefaults.colors(uncheckedColor = task.listColor?.let { Color(it).copy(alpha = 1f) }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
        Spacer(Modifier.width(AgendaContentGap))
        Column(Modifier.weight(1f).heightIn(min = 66.dp).clickable(enabled = interactive, role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center) {
            val primary = clockTaskFields(task, content.visibleFields.filter { it in primaryFields })
            if (primary == listOf(ClockTaskField.Title)) {
                Text(task.title, Modifier.fillMaxWidth(), fontSize = 16.sp,
                    textAlign = if (ClockTaskField.Title in content.rightAligned) TextAlign.Right else TextAlign.Left)
            } else if (primary.isNotEmpty()) {
                ClockTaskContent(task, now, primary, content.copy(typography = content.typography.copy(size = 16)),
                    MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("agenda_task_primary:${task.id}"))
            }
            val requested = content.visibleFields.filterNot { it in primaryFields }
            val defaultFields = AgendaAppearance.defaultTaskContent().visibleFields.filterNot { it == ClockTaskField.Title }
            if (primary.all { it == ClockTaskField.Title } && requested == defaultFields && requested.none { it in content.rightAligned }) {
                Text(details, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val fields = clockTaskFields(task, requested)
                if (fields.isNotEmpty()) ClockTaskContent(task, now, fields, content, MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("agenda_task_supporting:${task.id}"))
            }
        }
    }
}

@Composable
internal fun AgendaEvent(event: ScheduleEvent, now: Instant, content: ClockAgenda,
    primaryFields: Set<ClockAgendaField>, interactive: Boolean = true, onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val clockPattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    val timeFormat = remember(locale, clockPattern) { DateTimeFormatter.ofPattern(clockPattern, locale).withZone(ZoneId.systemDefault()) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (event.isAllDay) 48.dp else 66.dp).testTag("agenda_event:${event.id}")
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = interactive, role = Role.Button, onClick = onClick)
            .padding(horizontal = AgendaItemInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(AgendaLeadingWidth), contentAlignment = Alignment.Center) {
            Box(Modifier.width(5.dp).height(if (event.isAllDay) 22.dp else 40.dp)
                .testTag("agenda_event_indicator:${event.id}")
                .background(event.calendarColor?.let { Color(it).copy(alpha = 1f) } ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
        }
        Spacer(Modifier.width(AgendaContentGap))
        Column(Modifier.weight(1f)) {
            val primary = clockAgendaFields(event, content.visibleFields.filter { it in primaryFields })
            if (primary == listOf(ClockAgendaField.Title)) {
                Text(event.title, Modifier.fillMaxWidth(), fontSize = 16.sp,
                    textAlign = if (ClockAgendaField.Title in content.rightAligned) TextAlign.Right else TextAlign.Left)
            } else if (primary.isNotEmpty()) {
                ClockAgendaContent(event, now, primary, content.typography.copy(size = 16),
                    modifier = Modifier.testTag("agenda_event_primary:${event.id}"),
                    color = MaterialTheme.colorScheme.onSurface, rightAligned = content.rightAligned)
            }
            val requested = content.visibleFields.filterNot { it in primaryFields }
            val defaultFields = AgendaAppearance.defaultCalendarContent().visibleFields.filterNot { it == ClockAgendaField.Title }
            if (primary.all { it == ClockAgendaField.Title } && requested == defaultFields && requested.none { it in content.rightAligned }) {
                if (!event.isAllDay) Text("${timeFormat.format(event.startsAt)}–${timeFormat.format(event.endsAt)} · ${eventRemainingText(event, now)}",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val fields = clockAgendaFields(event, requested)
                if (fields.isNotEmpty()) ClockAgendaContent(event, now, fields, content.typography,
                    modifier = Modifier.testTag("agenda_event_supporting:${event.id}"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, rightAligned = content.rightAligned)
            }
        }
    }
}
