@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.PopupWidgetControls
import com.galaxyrio.gracelauncher.ui.overlays.PopupWidgetItem
import com.galaxyrio.gracelauncher.ui.overlays.panelWindowHeight
import kotlinx.coroutines.launch

private val AgendaAppearanceSaver = Saver<AgendaAppearance, String>(save = { it.encode() }, restore = { AgendaAppearance.decode(it) })
private enum class AgendaEditorPage { Overview, Hourly, Daily }
private class AgendaControl(val key: String, val content: @Composable (Int, Int) -> Unit)

@Composable
internal fun AgendaAppearanceSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = AgendaAppearanceSaver) { mutableStateOf(uiState.settings.agendaAppearance.normalized()) }
    var baseline by rememberSaveable(stateSaver = AgendaAppearanceSaver) { mutableStateOf(draft) }
    val savedWidget = uiState.popups[AgendaWidgetKey].orEmpty().firstNotNullOfOrNull { it.widget }?.encode()
    val newWidget = uiState.popups[AgendaWidgetDraftKey].orEmpty().firstNotNullOfOrNull { it.widget }
    var baselineWidget by rememberSaveable { mutableStateOf(savedWidget) }
    var widgetJson by rememberSaveable { mutableStateOf(savedWidget) }
    var widgetEdited by rememberSaveable { mutableStateOf(false) }
    var observedWidgetId by rememberSaveable { mutableIntStateOf(-1) }
    var editingWidget by rememberSaveable { mutableStateOf(false) }
    var resizingWidget by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentPage = AgendaEditorPage.entries.firstOrNull { it.name == currentEntry?.destination?.route } ?: AgendaEditorPage.Overview
    var contentPage by rememberSaveable { mutableStateOf<AgendaDailyContentPage?>(null) }
    var hourlyField by rememberSaveable { mutableStateOf<AgendaHourWeatherField?>(null) }
    // The standalone settings activity can retain an older weather snapshot than HOME.
    // Refresh the selected source on entry, and when its location or mode changes.
    LifecycleResumeEffect(uiState.settings.weatherEnabled, uiState.settings.weatherLocationId, uiState.settings.weatherConnection) {
        if (uiState.settings.weatherEnabled) actions.refreshWeather()
        onPauseOrDispose { }
    }
    val distance = with(LocalDensity.current) { 30.dp.roundToPx() }
    var saving by remember { mutableStateOf(false) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(savedWidget) {
        if (!widgetEdited) { baselineWidget = savedWidget; widgetJson = savedWidget }
    }
    LaunchedEffect(newWidget?.widgetId) {
        if (newWidget != null && newWidget.widgetId != observedWidgetId) {
            observedWidgetId = newWidget.widgetId
            widgetJson = newWidget.encode()
            widgetEdited = true
            editingWidget = false
        }
    }
    val widget = remember(widgetJson) { widgetJson?.let { HomeLayout.decode(it) }?.takeIf { it.hasWidget } }
    // The settings snapshot and widget snapshot arrive independently.
    val enabled = uiState.itemRevision > 0 && !saving && LocalSettingsStorageState.current.canEdit
    val weatherEnabled = enabled && uiState.settings.weatherEnabled
    val calendarEnabled = enabled && uiState.settings.calendarAgenda
    val tasksEnabled = enabled && uiState.settings.calendar.tasksOrg.enabled
    val haptics = uiState.settings.allowHapticFeedback
    val dirty = draft != baseline || widgetJson != baselineWidget
    fun changeWidget(layout: HomeLayout) { widgetJson = layout.encode(); widgetEdited = true }
    fun saveAndExit() {
        if (!enabled) return
        val applied = draft.normalized()
        saving = true
        scope.launch {
            try {
                if (actions.applyAgendaAppearance(applied, widget)) {
                    baseline = applied; baselineWidget = widgetJson; confirmExit = false; onBack()
                } else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_LONG).show()
            } finally { saving = false }
        }
    }
    fun discardAndExit() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                if (actions.discardAgendaWidgetDraft()) { confirmExit = false; onBack() }
                else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_LONG).show()
            } finally { saving = false }
        }
    }
    val back: () -> Unit = {
        if (!saving) when {
            hourlyField != null -> hourlyField = null
            contentPage != null -> contentPage = null
            currentPage != AgendaEditorPage.Overview -> navController.popBackStack()
            editingWidget -> editingWidget = false
            dirty -> confirmExit = true
            else -> discardAndExit()
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { if (!saving) confirmExit = false },
        title = { Text(stringResource(R.string.icon_designer_unsaved_title)) },
        text = { Text(stringResource(R.string.agenda_unsaved_message)) },
        confirmButton = { TextButton(::saveAndExit, enabled = enabled) { Text(stringResource(R.string.save)) } },
        dismissButton = { Row {
            TextButton({ confirmExit = false }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
            TextButton(::discardAndExit, enabled = !saving) { Text(stringResource(R.string.icon_designer_discard)) }
        } }, modifier = Modifier.testTag("agenda_appearance_unsaved"),
    )
    NavHost(navController, startDestination = AgendaEditorPage.Overview.name,
        modifier = Modifier.fillMaxSize().testTag("agenda_appearance_navigation"),
        enterTransition = { settingsEnter(distance) }, exitTransition = { settingsExit(distance) },
        popEnterTransition = { settingsEnter(distance, back = true) },
        popExitTransition = { settingsExit(distance, back = true) },
    ) {
        AgendaEditorPage.entries.forEach { displayed ->
            composable(displayed.name) { entry ->
                val pageEnabled = enabled && currentEntry?.id == entry.id
                fun openPage(destination: AgendaEditorPage) {
                    if (pageEnabled && entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        navController.navigate(destination.name) { launchSingleTop = true }
                    }
                }
                val title = stringResource(when (displayed) {
                    AgendaEditorPage.Overview -> R.string.settings_agenda
                    AgendaEditorPage.Hourly -> R.string.agenda_hourly_weather
                    AgendaEditorPage.Daily -> R.string.agenda_daily_content
                })
                val tag = when (displayed) {
                    AgendaEditorPage.Overview -> "agenda_appearance"
                    AgendaEditorPage.Hourly -> "agenda_hourly_weather_page"
                    AgendaEditorPage.Daily -> "agenda_daily_content_page"
                }
                SettingsPreviewScaffold(uiState, title, tag,
                    onBack = { if (currentEntry?.id == entry.id) back() }, previewEnabled = false,
                    actions = { SettingsApplyAction(stringResource(R.string.save), enabled = pageEnabled,
                        modifier = Modifier.testTag("agenda_appearance_apply"), onClick = ::saveAndExit) },
                ) { padding ->
                    val contentModifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                        .padding(horizontal = 16.dp)
                    when (displayed) {
                        AgendaEditorPage.Overview -> LazyColumn(contentModifier.testTag("agenda_appearance_controls"),
                            userScrollEnabled = !saving && !resizingWidget,
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            contentPadding = PaddingValues(bottom = 24.dp + LocalSettingsBottomInset.current),
                        ) {
                            item("title_heading") { SettingsHeading(stringResource(R.string.agenda_title)) }
                            clockSetting("title_choices") {
                                ClockControlSegment(0, if (draft.title == AgendaTitle.Time) 1 else 2, "agenda_title_choices") {
                                    OrderedIconChoices(tag = "agenda_title", order = AgendaTitle.entries, selected = setOf(draft.title),
                                        enabled = pageEnabled, hapticsEnabled = haptics,
                                        labelFor = { it.label() }, iconFor = { when (it) {
                                            AgendaTitle.Time -> R.drawable.ms_schedule
                                            AgendaTitle.Quote -> R.drawable.ms_format_quote
                                            AgendaTitle.Text -> R.drawable.ms_text_fields
                                        } }, onSelect = { choice, checked -> if (checked) draft = draft.copy(title = choice) })
                                }
                            }
                            if (draft.title == AgendaTitle.Text) clockSetting("title_text") {
                                ClockControlSegment(1, 2, "agenda_title_input") {
                                    OutlinedTextField(draft.titleText, { draft = draft.copy(titleText = it.take(500)) }, enabled = pageEnabled,
                                        label = { Text(stringResource(R.string.agenda_title_text)) }, maxLines = 3,
                                        modifier = Modifier.fillMaxWidth().testTag("agenda_title_text"))
                                }
                            }
                            if (draft.title == AgendaTitle.Quote) clockSetting("title_quote") {
                                ClockControlSegment(1, 2, "agenda_title_quote") {
                                    Text(stringResource(R.string.settings_coming_soon))
                                }
                            }
                            item("widget_heading") { SettingsHeading(stringResource(R.string.agenda_widget_region)) }
                            clockSetting("widget_add") {
                                SettingsActionItem(stringResource(R.string.settings_add_widget), null, 0, 1, "agenda_widget_add",
                                    enabled = pageEnabled && widget == null, onClick = actions.addAgendaWidget)
                            }
                            if (widget != null) {
                                clockSetting("widget_preview:${widget.widgetId}") {
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                                        PopupWidgetItem(widget, editingWidget, pageEnabled, haptics,
                                            maxHeight = panelWindowHeight().value.toInt(), onChange = { layout, _ -> changeWidget(layout) },
                                            onDragStart = { editingWidget = true }, onDrag = {}, onDragEnd = {},
                                            onResizing = { resizingWidget = it }, horizontalInset = 0.dp)
                                    }
                                }
                                if (editingWidget) clockSetting("widget_controls") {
                                    PopupWidgetControls(widget, onChange = { layout, _ -> changeWidget(layout) }, onDone = { editingWidget = false })
                                }
                                clockSetting("widget_remove") {
                                    SettingsActionItem(stringResource(R.string.widget_remove), null, 0, 1, "agenda_widget_remove", enabled = pageEnabled) {
                                        widgetJson = null; widgetEdited = true; editingWidget = false
                                    }
                                }
                            }
                            item("today_heading") { SettingsHeading(stringResource(R.string.agenda_today_heading)) }
                            agendaControls(buildList {
                                add(AgendaControl("hourly_weather") { index, count ->
                                    AgendaNavigationToggle(stringResource(R.string.agenda_hourly_weather), draft.showHourlyWeather, index, count,
                                        "agenda_hourly_weather", pageEnabled && weatherEnabled, onCheckedChange = { draft = draft.copy(showHourlyWeather = it) },
                                        onClick = { openPage(AgendaEditorPage.Hourly) })
                                })
                                add(AgendaControl("undated_tasks") { index, count ->
                                    SettingsToggleItem(stringResource(R.string.agenda_undated_tasks), null, draft.showUndatedTasks, index, count,
                                        "agenda_undated_tasks", enabled = pageEnabled && tasksEnabled) { draft = draft.copy(showUndatedTasks = it) }
                                })
                            })
                            item("daily_heading") { SettingsHeading(stringResource(R.string.agenda_daily)) }
                            clockSetting("daily_content") {
                                AgendaNavigationToggle(stringResource(R.string.agenda_daily_content), draft.showDailyContent, 0, 1,
                                    "agenda_daily_content", pageEnabled, onCheckedChange = { draft = draft.copy(showDailyContent = it) },
                                    onClick = { openPage(AgendaEditorPage.Daily) })
                            }
                        }
                        AgendaEditorPage.Hourly -> AgendaHourlyWeatherSettings(draft, uiState.weather.snapshot,
                            pageEnabled && weatherEnabled, haptics, hourlyField,
                            onFieldChange = { if (pageEnabled) hourlyField = it }, onChange = { if (pageEnabled) draft = it },
                            modifier = contentModifier.padding(vertical = 16.dp))
                        AgendaEditorPage.Daily -> AgendaDailyContentSettings(uiState, draft, pageEnabled,
                            weatherEnabled, calendarEnabled, tasksEnabled, haptics, contentPage,
                            onPageChange = { if (pageEnabled) contentPage = it },
                            onChange = { if (pageEnabled) draft = it }, modifier = contentModifier.padding(vertical = 16.dp))
                    }
                }
            }
        }
    }
    // Child destinations keep NavHost's predictive back. Guard only editor exit and inline panels.
    BackHandler(enabled = currentPage == AgendaEditorPage.Overview || contentPage != null || hourlyField != null || saving, onBack = back)
}

private fun LazyListScope.agendaControls(controls: List<AgendaControl>) {
    controls.forEachIndexed { index, control -> clockSetting(control.key) { control.content(index, controls.size) } }
}

@Composable
private fun AgendaNavigationToggle(title: String, checked: Boolean, index: Int, count: Int,
    tag: String, enabled: Boolean, onCheckedChange: (Boolean) -> Unit, onClick: () -> Unit,
) {
    SettingsActionItem(title, null, index, count, tag, enabled = enabled, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerticalDivider(Modifier.height(32.dp))
            Switch(checked, onCheckedChange, enabled = enabled,
                modifier = Modifier.padding(start = 16.dp).testTag("${tag}_switch")
                    .semantics { contentDescription = title })
        }
    }, onClick = onClick)
}

@Composable
private fun AgendaTitle.label(): String = stringResource(when (this) {
    AgendaTitle.Time -> R.string.agenda_title_time
    AgendaTitle.Quote -> R.string.agenda_title_quote
    AgendaTitle.Text -> R.string.agenda_title_text
})
