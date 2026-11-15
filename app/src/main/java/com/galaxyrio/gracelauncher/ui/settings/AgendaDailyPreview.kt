@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AgendaAppearance
import com.galaxyrio.gracelauncher.data.AgendaEntry
import com.galaxyrio.gracelauncher.data.agendaDaysWithTasks
import com.galaxyrio.gracelauncher.data.localBoundary
import com.galaxyrio.gracelauncher.data.tasks.TaskPriority
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.AgendaDayHeader
import com.galaxyrio.gracelauncher.ui.overlays.AgendaEvent
import com.galaxyrio.gracelauncher.ui.overlays.AgendaTask
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherTypography
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun AgendaDailyPreview(uiState: LauncherUiState, appearance: AgendaAppearance, modifier: Modifier = Modifier) {
    val now = remember { Instant.now() }
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()
    val sample = rememberClockPresetPreviewContent()
    val sampleTime = today.atTime(12, 0).atZone(zone).toInstant()
    val sampleNotes = stringResource(R.string.clock_task_preview_notes)
    val event = uiState.events.firstOrNull { it.localBoundary(it.startsAt, zone).atZone(zone).toLocalDate() == today }
        ?: sample.events.first().copy(startsAt = sampleTime, endsAt = sampleTime.plusSeconds(3600))
    val task = uiState.tasks.tasks.firstOrNull { it.dueAt?.atZone(zone)?.toLocalDate() == today }
        ?: sample.tasks.first().copy(dueAt = sampleTime.plusSeconds(7200), startsAt = sampleTime,
            notes = sampleNotes, priority = TaskPriority.Medium)
    val weather = uiState.weather.snapshot ?: sample.weather.copy(updatedAt = now,
        daily = sample.weather.daily.map { it.copy(date = today) })
    val entries = agendaDaysWithTasks(
        mapOf(today to if (uiState.settings.calendarAgenda) listOf(event) else emptyList()),
        if (uiState.settings.calendar.tasksOrg.enabled) listOf(task) else emptyList(), zone,
        uiState.settings.calendar.deprioritizeAllDay, appearance.tasksFirst,
    )[today].orEmpty()
    MaterialTheme(typography = LocalLauncherTypography.current) {
        Surface(modifier.testTag("agenda_daily_preview"), color = MaterialTheme.colorScheme.surfaceContainer,
            shape = ListItemDefaults.segmentedShapes(0, 1).shape,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            // The editor already has 16 dp side insets; match the agenda sheet's 30 dp in total.
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                AgendaDayHeader(today, today, weather.daily.firstOrNull { it.date == today }, weather.current,
                    appearance, uiState.settings.weatherEnabled, weather)
                entries.forEach { entry ->
                    when (entry) {
                        is AgendaEntry.Calendar -> AgendaEvent(entry.event, now, appearance.calendarContent,
                            appearance.calendarPrimaryFields, interactive = false, onClick = {})
                        is AgendaEntry.Task -> AgendaTask(entry.task, now, appearance.taskContent, appearance.taskPrimaryFields,
                            completing = false, onComplete = {}, onClick = {}, interactive = false)
                    }
                }
            }
        }
    }
}
