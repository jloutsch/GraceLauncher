@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import kotlinx.coroutines.awaitCancellation

@Composable
internal fun CalendarSourcesSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refresh by rememberUpdatedState(actions.refreshCalendarSources)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refresh()
            awaitCancellation()
        }
    }
    val sources = uiState.calendarSources
    val groups = remember(sources) {
        sources.groupBy { it.accountName to it.accountType }.entries
            .sortedWith(compareBy({ it.key.first.lowercase() }, { it.key.second }))
    }
    val selected = uiState.settings.calendar.selectedCalendarIds
        ?: sources.filter { it.visible }.mapTo(linkedSetOf()) { it.id }
    val enabled = LocalSettingsStorageState.current.canEdit
    SettingsScaffold(stringResource(R.string.calendar_sources), "settings_calendar_sources", onBack) { padding ->
        SettingsList(padding) {
            when (uiState.calendarSourcesStatus) {
                ScheduleStatus.PermissionRequired -> item {
                    SettingsHeading(stringResource(R.string.widget_calendar_access))
                    SettingsActionItem(stringResource(R.string.connect_calendar), stringResource(R.string.calendar_permission_description),
                        0, 1, "calendar_sources_access", onClick = actions.requestCalendarAccess)
                }
                ScheduleStatus.Loading -> item {
                    Text(stringResource(R.string.calendar_sources_loading), Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ScheduleStatus.Error -> item {
                    SettingsHeading(stringResource(R.string.calendar_sources_error))
                    SettingsActionItem(stringResource(R.string.retry), null, 0, 1, "calendar_sources_retry", onClick = refresh)
                }
                ScheduleStatus.Ready -> {
                    if (sources.isEmpty()) item {
                        Text(stringResource(R.string.calendar_sources_empty), Modifier.padding(vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    groups.forEach { (account, calendars) ->
                        item(key = "account:${calendars.first().id}") {
                            SettingsHeading(if (account.second == CalendarContract.ACCOUNT_TYPE_LOCAL || account.first.isBlank())
                                stringResource(R.string.calendar_local) else account.first)
                        }
                        itemsIndexed(calendars, key = { _, source -> "calendar:${source.id}" }) { index, source ->
                            val checked = source.id in selected
                            val shapes = ListItemDefaults.segmentedShapes(index = index, count = calendars.size)
                            SegmentedListItem(
                                checked = checked,
                                onCheckedChange = { include ->
                                    actions.updateSettings { settings ->
                                        val current = settings.calendar.selectedCalendarIds
                                            ?: sources.filter { it.visible }.mapTo(linkedSetOf()) { it.id }
                                        settings.copy(calendar = settings.calendar.copy(selectedCalendarIds =
                                            if (include) current + source.id else current - source.id))
                                    }
                                },
                                enabled = enabled,
                                modifier = Modifier.testTag("calendar_source:${source.id}"),
                                shapes = shapes.copy(selectedShape = shapes.shape),
                                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                                leadingContent = { Checkbox(checked, onCheckedChange = null, enabled = enabled) },
                                content = { Text(source.name.ifBlank { stringResource(R.string.calendar_unnamed) }) },
                                supportingContent = if (source.visible) null else ({ Text(stringResource(R.string.calendar_hidden)) }),
                                trailingContent = {
                                    Box(Modifier.size(16.dp).background(source.color?.let { Color(it) }
                                        ?: MaterialTheme.colorScheme.primary, CircleShape))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
