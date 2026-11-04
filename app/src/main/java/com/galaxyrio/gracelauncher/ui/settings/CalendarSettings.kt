package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.CalendarPreferences
import com.galaxyrio.gracelauncher.data.CalendarRefreshMode
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus

@Composable
internal fun CalendarSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit, onAppearance: () -> Unit = {}) {
    val calendar = uiState.settings.calendar
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    when (dialog) {
        "days" -> SettingsSelectionDialog(
            stringResource(R.string.calendar_read_days), CalendarPreferences.DaysAheadOptions, calendar.daysAhead, "calendar_days",
            { it.toString() }, { pluralStringResource(R.plurals.calendar_days_value, it, it) },
            onSelect = { days ->
                actions.updateSettings { it.copy(calendar = it.calendar.copy(daysAhead = days)) }
                dialog = null
            }, onDismiss = { dialog = null },
        )
        "refresh" -> SettingsSelectionDialog(
            stringResource(R.string.calendar_refresh_frequency), CalendarRefreshMode.entries, calendar.refreshMode,
            "calendar_refresh_mode", { it.name }, { it.label() },
            onSelect = { mode ->
                actions.updateSettings { it.copy(calendar = it.calendar.copy(refreshMode = mode)) }
                dialog = null
            }, onDismiss = { dialog = null },
        )
    }
    SettingsScaffold(stringResource(R.string.settings_calendar_agenda), "settings_calendar", onBack,
        actions = { SettingsAppearanceAction(onAppearance) }) { padding ->
        SettingsList(padding) {
            item {
                SettingsFeatureBanner(stringResource(R.string.calendar_enable), uiState.settings.calendarAgenda, "calendar_agenda") { enabled ->
                    actions.updateSettings { it.copy(calendarAgenda = enabled) }
                }
            }
            if (uiState.scheduleStatus == ScheduleStatus.PermissionRequired) item {
                SettingsHeading(stringResource(R.string.widget_calendar_access))
                SettingsActionItem(stringResource(R.string.connect_calendar), stringResource(R.string.calendar_permission_description),
                    0, 1, "calendar_access", onClick = actions.requestCalendarAccess)
            }
            item { SettingsHeading(stringResource(R.string.calendar_reading)) }
            item {
                SettingsActionItem(stringResource(R.string.calendar_read_days),
                    pluralStringResource(R.plurals.calendar_days_value, calendar.daysAhead, calendar.daysAhead),
                    0, 3, "calendar_read_days", onClick = { dialog = "days" })
            }
            item {
                SettingsActionItem(stringResource(R.string.calendar_refresh_frequency), calendar.refreshMode.label(),
                    1, 3, "calendar_refresh_frequency", onClick = { dialog = "refresh" })
            }
            item {
                val loading = uiState.scheduleStatus == ScheduleStatus.Loading
                SettingsActionItem(stringResource(if (loading) R.string.calendar_refreshing else R.string.calendar_refresh_now),
                    if (uiState.scheduleStatus == ScheduleStatus.Error) stringResource(R.string.calendar_error) else null,
                    2, 3, "calendar_refresh_now", enabled = uiState.settings.calendarAgenda && !loading &&
                        !uiState.isLoadingSettings && !uiState.settingsLoadFailed && uiState.scheduleStatus != ScheduleStatus.PermissionRequired,
                    onClick = actions.refreshAgenda)
            }
        }
    }
}

@Composable
private fun CalendarRefreshMode.label(): String = stringResource(when (this) {
    CalendarRefreshMode.EveryMinute -> R.string.calendar_refresh_minute
    CalendarRefreshMode.Every15Minutes -> R.string.calendar_refresh_15_minutes
    CalendarRefreshMode.Hourly -> R.string.calendar_refresh_hour
    CalendarRefreshMode.OnHomeEntry -> R.string.calendar_refresh_home
    CalendarRefreshMode.Manual -> R.string.calendar_refresh_manual
})
