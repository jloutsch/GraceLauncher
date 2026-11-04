@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.ClockColorPart
import com.galaxyrio.gracelauncher.data.ClockElement
import com.galaxyrio.gracelauncher.data.ClockRegions
import com.galaxyrio.gracelauncher.data.ClockRegion
import com.galaxyrio.gracelauncher.data.ClockFontFile
import com.galaxyrio.gracelauncher.data.ClockFontStore
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.visibleAgendaEvents
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.endPadding
import com.galaxyrio.gracelauncher.ui.components.startPadding
import com.galaxyrio.gracelauncher.ui.home.ClockFace
import com.galaxyrio.gracelauncher.ui.home.HomeClockHeader
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherTypography
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.resolvedFontColor
import com.galaxyrio.gracelauncher.data.weather.WeatherCurrent
import com.galaxyrio.gracelauncher.data.weather.WeatherCondition
import com.galaxyrio.gracelauncher.data.weather.WeatherTemperature
import com.galaxyrio.gracelauncher.data.weather.WeatherDay
import com.galaxyrio.gracelauncher.data.weather.WeatherLocation
import com.galaxyrio.gracelauncher.data.weather.WeatherMeasure
import com.galaxyrio.gracelauncher.data.weather.WeatherSnapshot
import com.galaxyrio.gracelauncher.data.weather.WeatherWind
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

private val ClockStyleDraftSaver = Saver<ClockStyle, String>(save = { it.encode() }, restore = { ClockStyle.decode(it) })

private enum class ClockEditorPage { Presets, Style, Color }
private enum class ClockContentPage { Agenda, Weather }

private val ClockColorGroups = listOf(
    R.string.settings_clock to listOf(ClockColorPart.HourTens, ClockColorPart.HourOnes,
        ClockColorPart.MinuteTens, ClockColorPart.MinuteOnes),
    R.string.clock_color_date_group to listOf(ClockColorPart.Weekday, ClockColorPart.Month, ClockColorPart.Day),
    R.string.settings_weather to listOf(ClockColorPart.Weather, ClockColorPart.WeatherIcon),
    R.string.clock_color_battery to listOf(ClockColorPart.Battery, ClockColorPart.BatteryIcon),
    R.string.settings_agenda to listOf(ClockColorPart.Agenda),
)

@Composable
internal fun ClockStyleSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit, onManageFonts: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = ClockStyleDraftSaver) {
        mutableStateOf(uiState.settings.clockStyle.withRegions(
            uiState.settings.clockStyle.resolvedRegions(uiState.settings.calendarAboveClock)))
    }
    var baseline by rememberSaveable(stateSaver = ClockStyleDraftSaver) { mutableStateOf(draft) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(ClockEditorPage.Style) }
    var presetLayout by rememberSaveable {
        mutableStateOf(uiState.settings.clockStyle.layout.takeIf {
            uiState.settings.clockStyle.regions == null && !it.allowsCustomFont
        })
    }
    var editingColor by rememberSaveable { mutableStateOf<ClockColorPart?>(null) }
    var editingContent by rememberSaveable { mutableStateOf<ClockContentPage?>(null) }
    var editingFont by rememberSaveable { mutableStateOf<ClockElement?>(null) }
    var editingRegionFont by rememberSaveable { mutableStateOf<ClockRegion?>(null) }
    var weatherIcons by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val enabled = !saving && LocalSettingsStorageState.current.canEdit
    val regions = draft.resolvedRegions()
    // Explicit default faces and migrated legacy slots should not make an untouched editor dirty.
    fun comparableRegions(style: ClockStyle): ClockRegions = style.resolvedRegions().let { value ->
        value.copy(primaryFaces = ClockElement.entries.associateWith(value::face))
    }
    val dirty = comparableRegions(draft) != comparableRegions(baseline) || draft.colors != baseline.colors
    fun updateRegions(transform: (ClockRegions) -> ClockRegions) {
        draft = draft.withRegions(transform(draft.resolvedRegions()))
        presetLayout = null
    }
    fun saveAndExit() {
        if (!enabled) return
        val applied = draft.withRegions()
        saving = true
        scope.launch {
            try {
                if (actions.applyClockStyle(applied)) {
                    baseline = applied
                    confirmExit = false
                    onBack()
                } else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_LONG).show()
            } finally { saving = false }
        }
    }
    val back: () -> Unit = {
        if (!saving) when {
            editingColor != null -> editingColor = null
            editingContent != null -> editingContent = null
            dirty -> confirmExit = true
            else -> onBack()
        }
    }
    val store = remember(context) { ClockFontStore(context) }
    val fonts by produceState<List<ClockFontFile>?>(null, store) { value = store.list() }
    LaunchedEffect(fonts) {
        val available = fonts?.map { it.id }?.toSet() ?: return@LaunchedEffect
        val current = draft
        val fontIds = listOf(current, baseline).flatMap { style ->
            val regions = style.resolvedRegions()
            (listOf(style.singleLine, style.twoLines) + style.presets.values + regions.primaryFaces.values).mapNotNull { it.fontId } +
                listOfNotNull(regions.secondary.typography.fontId, regions.tertiary.typography.fontId, regions.agenda.typography.fontId)
        }
        val missing = fontIds
            .filter { ClockFontStore.displayName(it) != null && it !in available }.toSet()
        if (missing.isNotEmpty()) {
            draft = current.withoutFonts(missing)
            baseline = baseline.withoutFonts(missing)
        }
    }
    editingFont?.let { element ->
        FontPickerDialog(uiState.settings.fontLibrary, regions.face(element).fontId,
            onSelect = { id ->
                updateRegions { current -> current.copy(primaryFaces = current.primaryFaces +
                    (element to current.face(element).copy(fontId = id))) }
                editingFont = null
            }, onManage = { editingFont = null; onManageFonts() }, onDismiss = { editingFont = null }, tag = "clock_font")
    }
    editingRegionFont?.let { region ->
        val fontId = when (region) {
            ClockRegion.Secondary -> regions.secondary.typography.fontId
            ClockRegion.Tertiary -> regions.tertiary.typography.fontId
            ClockRegion.Agenda -> regions.agenda.typography.fontId
            ClockRegion.Primary -> null
        }
        FontPickerDialog(uiState.settings.fontLibrary, fontId,
            onSelect = { id ->
                updateRegions { current -> when (region) {
                    ClockRegion.Secondary -> current.copy(secondary = current.secondary.copy(
                        typography = current.secondary.typography.copy(fontId = id)))
                    ClockRegion.Tertiary -> current.copy(tertiary = current.tertiary.copy(
                        typography = current.tertiary.typography.copy(fontId = id)))
                    ClockRegion.Agenda -> current.copy(agenda = current.agenda.copy(
                        typography = current.agenda.typography.copy(fontId = id)))
                    ClockRegion.Primary -> current
                } }
                editingRegionFont = null
            }, onManage = { editingRegionFont = null; onManageFonts() }, onDismiss = { editingRegionFont = null },
            tag = "clock_${region.name.lowercase()}_font")
    }
    if (weatherIcons) SettingsSelectionDialog(stringResource(R.string.clock_weather_icons), listOf("monochrome"), "monochrome",
        "clock_weather_icons", { it }, { stringResource(R.string.clock_weather_icons_monochrome) },
        { weatherIcons = false }, { weatherIcons = false })
    BackHandler(onBack = back)
    if (confirmExit) AlertDialog(
        onDismissRequest = { if (!saving) confirmExit = false },
        title = { Text(stringResource(R.string.icon_designer_unsaved_title)) },
        text = { Text(stringResource(R.string.clock_unsaved_message)) },
        confirmButton = {
            TextButton(onClick = ::saveAndExit, enabled = enabled, modifier = Modifier.testTag("clock_unsaved_save")) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmExit = false }, enabled = !saving,
                    modifier = Modifier.testTag("clock_unsaved_cancel")) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = { confirmExit = false; onBack() }, enabled = !saving,
                    modifier = Modifier.testTag("clock_unsaved_discard")) { Text(stringResource(R.string.icon_designer_discard)) }
            }
        },
        modifier = Modifier.testTag("clock_unsaved"),
    )
    val heroSpec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    val heroBounds = remember(heroSpec) { BoundsTransform { _, _ -> heroSpec } }
    val styleListState = rememberLazyListState()
    val presetsListState = rememberLazyListState()
    val colorsListState = rememberLazyListState()
    SettingsPreviewScaffold(uiState, stringResource(R.string.settings_clock_style), "settings_clock_style_editor",
        back,
        actions = {
            SettingsApplyAction(
                contentDescription = stringResource(R.string.clock_style_apply),
                modifier = Modifier.testTag("clock_style_apply"), enabled = enabled,
                onClick = ::saveAndExit,
            )
        },
    ) { padding ->
        LookaheadScope {
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                Column(Modifier.fillMaxSize()) {
                    ClockStylePreview(draft, uiState, this@LookaheadScope, heroBounds, Modifier.padding(top = 8.dp))
                    val activeColor = editingColor
                    val activeContent = editingContent
                    val controlsModifier = Modifier.weight(1f).fillMaxWidth()
                        .animateBounds(this@LookaheadScope, boundsTransform = heroBounds)
                        .settingsPreviewScrim()
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                        .clip(settingsPreviewControlsShape())
                    if (activeColor != null) key(activeColor) {
                        IconDesignerColorPicker(activeColor.label(), draft.colors[activeColor],
                            MaterialTheme.colorScheme.primary.toArgb(), null,
                            onChange = { color -> if (enabled) draft = draft.copy(colors =
                                if (color == null) draft.colors - activeColor else draft.colors + (activeColor to color)) },
                            onClose = { editingColor = null }, modifier = controlsModifier.padding(bottom = 16.dp),
                            fallback = resolvedFontColor(onWallpaper = true).toArgb(), fontColors = uiState.settings.fontColors)
                    } else if (activeContent != null) key(activeContent) {
                        when (activeContent) {
                            ClockContentPage.Agenda -> ClockContentPicker(
                                stringResource(R.string.clock_agenda_contents), regions.agenda.order, regions.agenda.selected,
                                enabled, uiState.settings.allowHapticFeedback, "clock_agenda_field", label = { it.label() },
                                onSelect = { field, checked -> if (enabled) updateRegions { current ->
                                    current.copy(agenda = current.agenda.copy(selected = if (checked)
                                        current.agenda.selected + field else current.agenda.selected - field))
                                } },
                                onReorder = { order -> if (enabled) updateRegions { current ->
                                    current.copy(agenda = current.agenda.copy(order = order))
                                } },
                                onClose = { editingContent = null }, modifier = controlsModifier.padding(bottom = 16.dp),
                            )
                            ClockContentPage.Weather -> ClockContentPicker(
                                stringResource(R.string.clock_weather_contents), regions.weather.order, regions.weather.selected,
                                enabled, uiState.settings.allowHapticFeedback, "clock_weather_field", label = { it.label() },
                                onSelect = { field, checked -> if (enabled) updateRegions { current ->
                                    current.copy(weather = current.weather.copy(selected = if (checked)
                                        current.weather.selected + field else current.weather.selected - field))
                                } },
                                onReorder = { order -> if (enabled) updateRegions { current ->
                                    current.copy(weather = current.weather.copy(order = order))
                                } },
                                onClose = { editingContent = null }, modifier = controlsModifier.padding(bottom = 16.dp),
                                labelShown = { field ->
                                    if (field.supportsLabel) field in regions.weather.labeledFields else null
                                },
                                onLabelChange = { field, shown -> if (enabled) updateRegions { current ->
                                    current.copy(weather = current.weather.copy(labeledFields = if (shown)
                                        current.weather.labeledFields + field else current.weather.labeledFields - field))
                                } },
                            )
                        }
                    } else LazyColumn(
                        controlsModifier.testTag("clock_style_controls"),
                        state = when (page) {
                            ClockEditorPage.Presets -> presetsListState
                            ClockEditorPage.Style -> styleListState
                            ClockEditorPage.Color -> colorsListState
                        },
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        contentPadding = PaddingValues(bottom = 96.dp + LocalSettingsBottomInset.current),
                        userScrollEnabled = !saving,
                    ) {
                        when (page) {
                            ClockEditorPage.Color -> ClockColorGroups.forEach { (title, parts) ->
                                item("clock_color_heading:${parts.first().name}") { SettingsHeading(stringResource(title)) }
                                itemsIndexed(parts, key = { _, part -> part.name }) { index, part ->
                                    SettingsActionItem(part.label(), fontColorSummary(draft.colors[part], onWallpaper = true),
                                        index, parts.size, "clock_color:${part.name}", enabled = enabled,
                                        trailing = {
                                            Surface(Modifier.size(28.dp), shape = CircleShape,
                                                color = resolvedFontColor(draft.colors[part], onWallpaper = true)) {}
                                        }) { editingColor = part }
                                }
                            }
                            ClockEditorPage.Presets -> clockSetting("presets") {
                                ClockLayoutSelector(draft, ClockLayout.entries.filter { !it.allowsCustomFont },
                                    1, enabled, selectedLayout = presetLayout) { layout ->
                                    draft = draft.selectPreset(layout)
                                    presetLayout = layout
                                }
                            }
                            ClockEditorPage.Style -> clockRegionSettings(regions, enabled, uiState.settings.allowHapticFeedback,
                                onChange = ::updateRegions, onFont = { editingFont = it }, onWeatherIcons = { weatherIcons = true },
                                onWeatherContent = { editingContent = ClockContentPage.Weather },
                                onAgendaContent = { editingContent = ClockContentPage.Agenda },
                                onRegionFont = { editingRegionFont = it })
                        }
                    }
                }
                if (editingColor == null && editingContent == null) SettingsModeToolbar(
                    listOf(stringResource(R.string.clock_tab_presets), stringResource(R.string.clock_tab_style), stringResource(R.string.icon_designer_color)),
                    page.ordinal, enabled, "clock_style_toolbar",
                    ClockEditorPage.entries.map { "clock_tab:${it.name}" },
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = FloatingToolbarDefaults.ScreenOffset + LocalSettingsBottomInset.current),
                ) { page = ClockEditorPage.entries[it] }
            }
        }
    }
}

internal fun LazyListScope.clockSetting(key: String, content: @Composable () -> Unit) {
    item(key = key) {
        // Retain item identity and animate the rows when a preset adds/removes controls.
        Box(Modifier.animateItem(
            fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        )) { content() }
    }
}

@Composable
private fun ClockLayoutSelector(style: ClockStyle, layouts: List<ClockLayout>, count: Int, enabled: Boolean,
    selectedLayout: ClockLayout? = style.layout, onSelect: (ClockLayout) -> Unit) {
    // Restore a useful initial viewport, but never reorder or auto-scroll on selection.
    val listState = key(layouts) { rememberLazyListState(initialFirstVisibleItemIndex = (layouts.indexOf(selectedLayout) - 1).coerceAtLeast(0)) }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(0, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.testTag("clock_layout_selector"),
        content = {
            LazyRow(
                Modifier.fillMaxWidth().selectableGroup().testTag("clock_layout_options"),
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
            ) {
                items(layouts, key = { it.name }) { layout ->
                    ClockLayoutOption(style.copy(layout = layout), selected = layout == selectedLayout,
                        enabled = enabled, onSelect = onSelect)
                }
            }
        },
    )
}

@Composable
private fun ClockLayoutOption(style: ClockStyle, selected: Boolean, enabled: Boolean, onSelect: (ClockLayout) -> Unit) {
    val layout = style.layout
    val label = layout.label()
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val interactions = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    val pressSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val releaseSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val container by animateColorAsState(
        if (selected) colors.primaryContainer else colors.primaryContainer.copy(alpha = 0f),
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "clockCardContainer",
    )
    val outline by animateColorAsState(
        colors.outline.copy(alpha = if (selected) 0f else 1f),
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "clockCardOutline",
    )
    val contentColor by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "clockCardContent",
    )
    LaunchedEffect(interactions, pressSpec, releaseSpec) {
        interactions.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(0.9f, pressSpec)
                is PressInteraction.Release -> {
                    // A quick tap must still visibly compress before springing back.
                    if (scale.value > 0.94f) scale.animateTo(0.9f, pressSpec)
                    scale.animateTo(1f, releaseSpec)
                }
                is PressInteraction.Cancel -> scale.animateTo(1f, pressSpec)
            }
        }
    }
    Box(
        Modifier.size(72.dp).testTag("clock_layout:${layout.name}")
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(shape)
            .background(container)
            .border(1.dp, outline, shape)
            .selectable(selected, enabled = enabled && LocalSettingsStorageState.current.canEdit,
                interactionSource = interactions, indication = ripple(),
                role = Role.RadioButton, onClick = { onSelect(layout) })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ClockFace(
            time = if (layout.week) "Fri" else if (style.face.showLeadingZero) "09:30" else "9:30",
            style = style,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).clearAndSetSemantics {},
            fontScale = 0.3f,
            textAlign = TextAlign.Center,
            color = if (layout.week) style.colors[ClockColorPart.Weekday]?.let { resolvedFontColor(it, onWallpaper = true) } ?: contentColor else contentColor,
            digitColors = listOf(ClockColorPart.HourTens, ClockColorPart.HourOnes, ClockColorPart.MinuteTens, ClockColorPart.MinuteOnes)
                .map { part -> style.colors[part]?.let { resolvedFontColor(it, onWallpaper = true) } ?: contentColor },
        )
    }
}

@Composable
private fun ClockStylePreview(
    style: ClockStyle,
    uiState: LauncherUiState,
    lookaheadScope: LookaheadScope,
    boundsTransform: BoundsTransform,
    modifier: Modifier = Modifier,
) {
    val appearance = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons, uiState.settings.iconDesign?.design?.iconSize ?: 100)
    val now by produceState(Instant.now()) {
        while (true) { value = Instant.now(); delay(60_000 - System.currentTimeMillis() % 60_000) }
    }
    val sampleEvent = ScheduleEvent(
        id = -1L, title = stringResource(R.string.clock_agenda_preview_title),
        startsAt = now.plusSeconds(3600), endsAt = now.plusSeconds(7200), isAllDay = false,
        location = stringResource(R.string.clock_agenda_preview_location),
        calendarColor = MaterialTheme.colorScheme.primary.toArgb(),
        calendarName = stringResource(R.string.clock_agenda_preview_calendar),
    )
    val events = visibleAgendaEvents(uiState.events, now, 0).ifEmpty {
        List(9) { index -> sampleEvent.copy(
            id = -(index + 1L),
            title = if (index == 0) sampleEvent.title else stringResource(R.string.clock_agenda_preview_event, index + 1),
            startsAt = sampleEvent.startsAt.plusSeconds(index * 7200L),
            endsAt = sampleEvent.endsAt.plusSeconds(index * 7200L),
        ) }
    }
    val zone = ZoneId.systemDefault()
    val weatherSnapshot = uiState.weather.snapshot ?: WeatherSnapshot(
        location = WeatherLocation("preview", stringResource(R.string.clock_weather_preview_location), zone),
        current = WeatherCurrent(
            WeatherTemperature(22.0, "c"), WeatherCondition.Clear, true, stringResource(R.string.weather_clear),
            feelsLike = WeatherTemperature(23.0, "c"), humidity = WeatherMeasure(60.0, "percent"),
            wind = WeatherWind(90.0, WeatherMeasure(8.0, "kph")), airQuality = WeatherMeasure(35.0, "aqi"),
            uv = WeatherMeasure(3.0, "uvi"),
        ),
        hourly = emptyList(),
        daily = listOf(WeatherDay(now.atZone(zone).toLocalDate(), WeatherTemperature(25.0, "c"),
            WeatherTemperature(17.0, "c"), WeatherCondition.Clear, null)),
        updatedAt = now, attribution = "",
    )
    val maximumPreviewHeight = (LocalConfiguration.current.screenHeightDp.dp * .45f).coerceIn(168.dp, 320.dp)
    val minimumPreviewHeight = minOf(maximumPreviewHeight,
        if (style.resolvedRegions().let { it.primary == ClockElement.Clock && it.stackedClock }) 232.dp else 168.dp)
    Box(modifier.fillMaxWidth().testTag("clock_style_preview")
        .animateBounds(
            lookaheadScope = lookaheadScope,
            modifier = Modifier.heightIn(min = minimumPreviewHeight, max = maximumPreviewHeight),
            boundsTransform = boundsTransform,
        ).clipToBounds(), contentAlignment = Alignment.CenterStart) {
        CompositionLocalProvider(LocalLauncherAppearance provides appearance) {
            MaterialTheme(typography = LocalLauncherTypography.current) {
                Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    HomeClockHeader(
                        now = now, event = events.firstOrNull(), onDateClick = {}, onClockClick = {}, agendaEvents = events,
                        showBattery = true, clockStyle = style,
                        showClock = true, showCalendar = true,
                        calendarAboveClock = uiState.settings.calendarAboveClock,
                        weather = weatherSnapshot.current,
                        weatherSnapshot = weatherSnapshot,
                        modifier = Modifier.fillMaxWidth().padding(
                            start = uiState.settings.listAppearance.startPadding,
                            end = uiState.settings.listAppearance.endPadding, top = 12.dp, bottom = 12.dp),
                        interactive = false, clockTag = "clock_style_preview_text", dateTag = "clock_style_preview_date",
                    )
                }
            }
        }
    }
}

@Composable
internal fun ClockSlider(
    title: String, value: Int, default: Int, range: IntRange, step: Int,
    index: Int, count: Int, tag: String, enabled: Boolean, valueLabel: String? = null,
    discrete: Boolean = false, onChange: (Int) -> Unit,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.testTag("${tag}_item"),
        content = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, Modifier.weight(1f))
                    if (valueLabel != null) Text(valueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(value = value.toFloat(), onValueChange = {
                        onChange((range.first + ((it - range.first) / step).roundToInt() * step).coerceIn(range))
                    }, valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = if (discrete || step > 1) ((range.last - range.first) / step - 1).coerceAtLeast(0) else 0,
                        enabled = enabled, modifier = Modifier.weight(1f).testTag(tag).semantics {
                            contentDescription = title
                            if (valueLabel != null) stateDescription = valueLabel
                        })
                    IconButton(onClick = { onChange(default) }, enabled = enabled && value != default,
                        modifier = Modifier.size(48.dp).clip(CircleShape).testTag("${tag}_reset")) {
                        Icon(painterResource(R.drawable.ms_restart_alt), stringResource(R.string.clock_reset_value, title))
                    }
                }
            }
        },
    )
}

@Composable
internal fun ClockLayout.label(): String = stringResource(when (this) {
    ClockLayout.SingleLine -> R.string.clock_single_line
    ClockLayout.TwoLines -> R.string.clock_two_lines
    ClockLayout.Week -> R.string.clock_week
    ClockLayout.Sacramento -> R.string.clock_sacramento
    ClockLayout.Bokor -> R.string.clock_bokor
    ClockLayout.Plaster -> R.string.clock_plaster
    ClockLayout.DoublePlaster -> R.string.clock_double_plaster
    ClockLayout.Monoton -> R.string.clock_monoton
    ClockLayout.Lucky -> R.string.clock_lucky
    ClockLayout.SacramentoWeek -> R.string.clock_sacramento_week
})

@Composable
private fun ClockColorPart.label(): String = stringResource(when (this) {
    ClockColorPart.HourTens -> R.string.clock_color_hour_tens
    ClockColorPart.HourOnes -> R.string.clock_color_hour_ones
    ClockColorPart.MinuteTens -> R.string.clock_color_minute_tens
    ClockColorPart.MinuteOnes -> R.string.clock_color_minute_ones
    ClockColorPart.Weekday -> R.string.clock_color_weekday
    ClockColorPart.Month -> R.string.clock_color_month
    ClockColorPart.Day -> R.string.clock_color_day
    ClockColorPart.Battery -> R.string.clock_color_battery_percentage
    ClockColorPart.Weather -> R.string.clock_color_weather
    ClockColorPart.Agenda -> R.string.clock_color_agenda
    ClockColorPart.WeatherIcon -> R.string.clock_color_weather_icons
    ClockColorPart.BatteryIcon -> R.string.clock_color_battery_icons
})
