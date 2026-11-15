@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AgendaCreateAction
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState

@Composable
internal fun AgendaCreateButton(uiState: LauncherUiState, actions: LauncherActions, modifier: Modifier = Modifier) {
    val ready = !uiState.isLoadingSettings && !uiState.settingsLoadFailed
    val calendarEnabled = ready && uiState.settings.calendarAgenda
    val tasksEnabled = ready && uiState.settings.calendar.tasksOrg.enabled
    if (!calendarEnabled && !tasksEnabled) return
    fun available(action: AgendaCreateAction) = when (action) {
        AgendaCreateAction.Event -> calendarEnabled
        AgendaCreateAction.Task -> tasksEnabled
    }
    val stored = uiState.settings.agendaAppearance.lastCreateAction
    val selected = stored.takeIf(::available) ?: AgendaCreateAction.entries.first(::available)
    var expanded by rememberSaveable { mutableStateOf(false) }
    fun create(action: AgendaCreateAction) {
        if (!available(action)) return
        expanded = false
        if (action != stored) actions.updateSettings { settings ->
            settings.copy(agendaAppearance = settings.agendaAppearance.copy(lastCreateAction = action))
        }
        when (action) {
            AgendaCreateAction.Event -> actions.newEvent()
            AgendaCreateAction.Task -> actions.newTask()
        }
    }
    val menuDescription = stringResource(R.string.agenda_choose_create_action)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "agenda_create_arrow")
    Box(modifier.testTag("agenda_create")) {
        SplitButton(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(onClick = { create(selected) },
                    modifier = Modifier.testTag("agenda_create_selected")) {
                    Icon(painterResource(R.drawable.ms_add), null, Modifier.size(SplitButtonDefaults.LeadingIconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(selected.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingButton = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(menuDescription) } },
                    state = rememberTooltipState(),
                ) {
                    SplitButtonDefaults.TonalTrailingButton(checked = expanded, onCheckedChange = { expanded = it },
                        modifier = Modifier.testTag("agenda_create_menu").semantics { contentDescription = menuDescription }) {
                        Icon(painterResource(R.drawable.ms_expand_more), null,
                            Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = rotation })
                    }
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AgendaCreateAction.entries.forEach { action ->
                DropdownMenuItem(text = { Text(action.label()) }, onClick = { create(action) },
                    enabled = available(action), modifier = Modifier.testTag("agenda_create:${action.name}"),
                    trailingIcon = if (action == selected) ({ Icon(painterResource(R.drawable.ms_check), null) }) else null)
            }
        }
    }
}

@Composable
private fun AgendaCreateAction.label(): String = stringResource(when (this) {
    AgendaCreateAction.Event -> R.string.new_event
    AgendaCreateAction.Task -> R.string.agenda_new_task
})
