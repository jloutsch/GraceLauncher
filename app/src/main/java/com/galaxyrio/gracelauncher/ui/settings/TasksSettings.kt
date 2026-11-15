@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.tasks.TaskSortMode
import com.galaxyrio.gracelauncher.data.tasks.TasksStatus
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState

@Composable
internal fun tasksStatusSummary(status: TasksStatus): String = stringResource(when (status) {
    TasksStatus.Disabled -> R.string.tasks_disabled
    TasksStatus.Loading -> R.string.tasks_loading
    TasksStatus.Ready -> R.string.tasks_connected
    TasksStatus.NotInstalled -> R.string.tasks_not_installed
    TasksStatus.PermissionRequired -> R.string.tasks_permission_required
    TasksStatus.UpdateRequired -> R.string.tasks_update_required
    TasksStatus.Error -> R.string.tasks_error
})

@Composable
private fun TaskSortMode.label(): String = stringResource(when (this) {
    TaskSortMode.DueDate -> R.string.clock_task_field_due_date
    TaskSortMode.Priority -> R.string.clock_task_field_priority
    TaskSortMode.Name -> R.string.clock_task_field_title
})

@Composable
internal fun TasksSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    onLists: () -> Unit, onAppearance: () -> Unit) {
    val preferences = uiState.settings.calendar.tasksOrg
    val state = uiState.tasks
    var sorting by rememberSaveable { mutableStateOf(false) }
    if (sorting) SettingsSelectionDialog(
        stringResource(R.string.tasks_sort_by), TaskSortMode.entries, preferences.sortMode, "tasks_sort_mode",
        { it.name }, { it.label() },
        onSelect = { mode ->
            actions.updateSettings { it.copy(calendar = it.calendar.copy(tasksOrg = it.calendar.tasksOrg.copy(sortMode = mode))) }
            sorting = false
        }, onDismiss = { sorting = false },
    )
    SettingsScaffold(stringResource(R.string.tasks_org), "settings_tasks", onBack,
        actions = { SettingsAppearanceAction(onAppearance) }) { padding ->
        SettingsList(padding) {
            item {
                SettingsFeatureBanner(stringResource(R.string.tasks_enable), preferences.enabled, "tasks_enabled") { enabled ->
                    actions.updateSettings { it.copy(calendar = it.calendar.copy(tasksOrg = it.calendar.tasksOrg.copy(enabled = enabled))) }
                }
            }
            if (preferences.enabled) {
                item { SettingsHeading(stringResource(R.string.tasks_connection)) }
                item {
                    when (state.status) {
                        TasksStatus.NotInstalled, TasksStatus.UpdateRequired -> SettingsActionItem(
                            stringResource(if (state.status == TasksStatus.NotInstalled) R.string.tasks_get_app else R.string.tasks_update_app),
                            tasksStatusSummary(state.status), 0, 1, "tasks_get_app", onClick = actions.getTasks)
                        TasksStatus.PermissionRequired -> SettingsActionItem(stringResource(R.string.tasks_connect),
                            stringResource(R.string.tasks_access_summary), 0, 2, "tasks_access", onClick = actions.requestTasksAccess)
                        TasksStatus.Error -> SettingsActionItem(stringResource(R.string.retry), tasksStatusSummary(state.status),
                            0, 1, "tasks_retry", onClick = actions.refreshTasks)
                        else -> SettingsActionItem(stringResource(R.string.tasks_open_app), tasksStatusSummary(state.status),
                            0, 1, "tasks_open_app", onClick = actions.openTasks)
                    }
                }
                if (state.status == TasksStatus.PermissionRequired) item {
                    SettingsActionItem(stringResource(R.string.tasks_app_permissions), null, 1, 2, "tasks_app_permissions",
                        onClick = actions.openTasksAccessSettings)
                }
                item { SettingsHeading(stringResource(R.string.tasks_data)) }
                item {
                    val ids = preferences.selectedListIds
                    SettingsActionItem(stringResource(R.string.tasks_choose_lists),
                        if (ids == null) stringResource(R.string.tasks_all_lists)
                        else pluralStringResource(R.plurals.tasks_lists_selected, ids.size, ids.size),
                        0, 2, "tasks_choose_lists", enabled = state.status == TasksStatus.Ready || state.lists.isNotEmpty(), onClick = onLists)
                }
                item {
                    SettingsActionItem(stringResource(R.string.calendar_refresh_now), stringResource(R.string.tasks_refresh_summary),
                        1, 2, "tasks_refresh", enabled = state.status != TasksStatus.Loading, onClick = actions.refreshTasks)
                }
                item { SettingsHeading(stringResource(R.string.tasks_sorting)) }
                item {
                    SettingsActionItem(stringResource(R.string.tasks_sort_by), preferences.sortMode.label(),
                        0, 2, "tasks_sort_by", onClick = { sorting = true })
                }
                item {
                    SettingsToggleItem(stringResource(R.string.tasks_undated_first), null, preferences.undatedFirst,
                        1, 2, "tasks_undated_first") { first ->
                        actions.updateSettings { it.copy(calendar = it.calendar.copy(tasksOrg = it.calendar.tasksOrg.copy(undatedFirst = first))) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TasksListsSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val state = uiState.tasks
    val preferences = uiState.settings.calendar.tasksOrg
    val lists = state.lists
    val groups = remember(lists) { lists.groupBy { it.accountId to it.accountName }.values.toList() }
    val selected = preferences.selectedListIds ?: lists.mapTo(linkedSetOf()) { it.id }
    val enabled = LocalSettingsStorageState.current.canEdit && preferences.enabled &&
        (state.status == TasksStatus.Ready || (state.status == TasksStatus.Loading && lists.isNotEmpty()))
    SettingsScaffold(stringResource(R.string.tasks_choose_lists), "settings_task_lists", onBack) { padding ->
        SettingsList(padding) {
            item {
                SettingsHeading(stringResource(R.string.tasks_choose_lists))
                SettingsToggleItem(stringResource(R.string.tasks_all_lists), null, preferences.selectedListIds == null,
                    0, 1, "tasks_all_lists", enabled) { all ->
                    actions.updateSettings { it.copy(calendar = it.calendar.copy(tasksOrg = it.calendar.tasksOrg.copy(
                        selectedListIds = if (all) null else lists.mapTo(linkedSetOf()) { list -> list.id }))) }
                }
            }
            if (lists.isEmpty()) item {
                Text(if (state.status == TasksStatus.Ready) stringResource(R.string.tasks_no_lists) else tasksStatusSummary(state.status),
                    Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.status == TasksStatus.PermissionRequired) SettingsActionItem(stringResource(R.string.tasks_connect),
                    null, 0, 1, "task_lists_access", onClick = actions.requestTasksAccess)
                if (state.status == TasksStatus.Error) SettingsActionItem(stringResource(R.string.retry),
                    null, 0, 1, "task_lists_retry", onClick = actions.refreshTasks)
            }
            groups.forEach { group ->
                item(key = "account:${group.first().accountId}") {
                    SettingsHeading(group.first().accountName.ifBlank { stringResource(R.string.tasks_org) })
                }
                itemsIndexed(group, key = { _, list -> "task_list:${list.id}" }) { index, list ->
                    val checked = list.id in selected
                    val shapes = ListItemDefaults.segmentedShapes(index, group.size)
                    SegmentedListItem(
                        checked = checked, onCheckedChange = { include ->
                            actions.updateSettings { settings ->
                                val current = settings.calendar.tasksOrg.selectedListIds ?: lists.mapTo(linkedSetOf()) { it.id }
                                settings.copy(calendar = settings.calendar.copy(tasksOrg = settings.calendar.tasksOrg.copy(
                                    selectedListIds = if (include) current + list.id else current - list.id)))
                            }
                        }, enabled = enabled, modifier = Modifier.testTag("task_list:${list.id}"),
                        shapes = shapes.copy(selectedShape = shapes.shape),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                        leadingContent = { Checkbox(checked, onCheckedChange = null, enabled = enabled) },
                        content = { Text(list.title.ifBlank { stringResource(R.string.tasks_unnamed_list) }) },
                        trailingContent = {
                            Box(Modifier.size(16.dp).background(list.color?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.primary, CircleShape))
                        },
                    )
                }
            }
        }
    }
}
