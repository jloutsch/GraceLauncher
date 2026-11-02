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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.ClockColorPart
import com.galaxyrio.gracelauncher.data.ClockVisibility
import com.galaxyrio.gracelauncher.data.ClockFaceStyle
import com.galaxyrio.gracelauncher.data.ClockFontFile
import com.galaxyrio.gracelauncher.data.ClockFontStore
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.data.ClockStyle
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
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

private val ClockStyleDraftSaver = Saver<ClockStyle, String>(save = { it.encode() }, restore = { ClockStyle.decode(it) })

private enum class ClockEditorPage { Presets, Style, Color }

private val ClockColorGroups = listOf(
    R.string.settings_clock to listOf(ClockColorPart.HourTens, ClockColorPart.HourOnes,
        ClockColorPart.MinuteTens, ClockColorPart.MinuteOnes),
    R.string.clock_color_date_group to listOf(ClockColorPart.Weekday, ClockColorPart.Month, ClockColorPart.Day),
    R.string.settings_misc to listOf(ClockColorPart.Battery, ClockColorPart.Weather),
)

@Composable
internal fun ClockStyleSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit, onManageFonts: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = ClockStyleDraftSaver) {
        mutableStateOf(uiState.settings.clockStyle.applyPresetToBase())
    }
    var page by rememberSaveable { mutableStateOf(ClockEditorPage.Style) }
    var baseLayout by rememberSaveable { mutableStateOf(draft.layout.baseLayout) }
    var presetLayout by rememberSaveable {
        mutableStateOf(uiState.settings.clockStyle.layout.takeIf { !it.allowsCustomFont } ?: ClockLayout.Sacramento)
    }
    var editingColor by rememberSaveable { mutableStateOf<ClockColorPart?>(null) }
    val face = draft.face
    fun changeFace(transform: (ClockFaceStyle) -> ClockFaceStyle) { draft = draft.withFace(transform(draft.face)) }
    fun changeVisibility(transform: (ClockVisibility) -> ClockVisibility) { draft = draft.copy(visibility = transform(draft.visibility)) }
    fun selectPage(destination: ClockEditorPage) {
        if (destination == page) return
        draft = when (destination) {
            ClockEditorPage.Style -> draft.copy(layout = baseLayout)
            ClockEditorPage.Presets -> draft.copy(layout = presetLayout)
            ClockEditorPage.Color -> draft
        }
        page = destination
    }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val enabled = !saving && LocalSettingsStorageState.current.canEdit
    val store = remember(context) { ClockFontStore(context) }
    val fonts by produceState<List<ClockFontFile>?>(null, store) { value = store.list() }
    LaunchedEffect(fonts) {
        val available = fonts?.map { it.id }?.toSet() ?: return@LaunchedEffect
        val current = draft
        val missing = (listOf(current.singleLine, current.twoLines) + current.presets.values).mapNotNull { it.fontId }
            .filter { ClockFontStore.displayName(it) != null && it !in available }.toSet()
        if (missing.isNotEmpty()) draft = current.withoutFonts(missing)
    }
    if (dialog == "font" && draft.layout.allowsCustomFont) {
        FontPickerDialog(uiState.settings.fontLibrary, face.fontId,
            onSelect = { id -> changeFace { it.copy(fontId = id) }; dialog = null },
            onManage = { dialog = null; onManageFonts() }, onDismiss = { dialog = null }, tag = "clock_font")
    }
    if (dialog == "weather_icons") {
        SettingsSelectionDialog(stringResource(R.string.clock_weather_icons), listOf("monochrome"), "monochrome",
            "clock_weather_icons", { it }, { stringResource(R.string.clock_weather_icons_monochrome) },
            { dialog = null }, { dialog = null })
    }
    BackHandler(editingColor != null) { editingColor = null }
    val fontLabel = appFontLabel(face.fontId)
    val stylePage = page == ClockEditorPage.Style
    val allowsFont = draft.layout.allowsCustomFont
    val showsColon = !draft.layout.stacked
    val fontIndex = 1 + if (stylePage) 5 else 0
    val weightIndex = fontIndex + if (allowsFont) 1 else 0
    val leadingZeroIndex = weightIndex + 5
    val separationIndex = leadingZeroIndex + 1
    val orderIndex = separationIndex + 1 + if (showsColon) 1 else 0
    val count = orderIndex + 1 + if (stylePage) 1 else 0
    val defaults = ClockStyle.defaults(draft.layout)
    val heroSpec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    val heroBounds = remember(heroSpec) { BoundsTransform { _, _ -> heroSpec } }
    val styleListState = rememberLazyListState()
    val presetsListState = rememberLazyListState()
    val colorsListState = rememberLazyListState()
    SettingsPreviewScaffold(uiState, stringResource(R.string.settings_clock_style), "settings_clock_style_editor",
        { if (editingColor != null) editingColor = null else onBack() },
        actions = {
            SettingsApplyAction(
                contentDescription = stringResource(R.string.clock_style_apply),
                modifier = Modifier.testTag("clock_style_apply"), enabled = enabled,
                onClick = {
                    val applied = draft.applyPresetToBase()
                    saving = true
                    scope.launch {
                        try {
                            if (actions.applyClockStyle(applied)) onBack()
                            else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_LONG).show()
                        } finally { saving = false }
                    }
                },
            )
        },
    ) { padding ->
        LookaheadScope {
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                Column(Modifier.fillMaxSize()) {
                    ClockStylePreview(draft, uiState, this@LookaheadScope, heroBounds,
                        Modifier.padding(top = 8.dp))
                    val activeColor = editingColor
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
                        if (page == ClockEditorPage.Color) {
                            ClockColorGroups.forEach { (title, parts) ->
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
                        } else {
                            clockSetting("layout") {
                                ClockLayoutSelector(draft,
                                    ClockLayout.entries.filter { it.allowsCustomFont == stylePage }, count, enabled) {
                                    draft = draft.copy(layout = it)
                                    if (stylePage) baseLayout = it else presetLayout = it
                                }
                            }
                            if (stylePage) {
                                clockSetting("show_clock") {
                                    SettingsToggleItem(stringResource(R.string.clock_show_clock), null,
                                        draft.visibility.showClock, 1, count, "clock_show_clock", enabled) { value ->
                                        changeVisibility { it.copy(showClock = value) }
                                    }
                                }
                                clockSetting("show_weekday") {
                                    SettingsToggleItem(stringResource(R.string.clock_show_weekday), null,
                                        draft.visibility.showWeekday, 2, count, "clock_show_weekday", enabled) { value ->
                                        changeVisibility { it.copy(showWeekday = value) }
                                    }
                                }
                                clockSetting("show_date") {
                                    SettingsToggleItem(stringResource(R.string.clock_show_date), null,
                                        draft.visibility.showDate, 3, count, "clock_show_date", enabled) { value ->
                                        changeVisibility { it.copy(showDate = value) }
                                    }
                                }
                                clockSetting("show_battery") {
                                    SettingsToggleItem(stringResource(R.string.clock_show_battery), null,
                                        draft.visibility.showBattery, 4, count, "clock_show_battery", enabled) { value ->
                                        changeVisibility { it.copy(showBattery = value) }
                                    }
                                }
                                clockSetting("show_weather") {
                                    SettingsToggleItem(stringResource(R.string.clock_show_weather), null,
                                        draft.visibility.showWeather, 5, count, "clock_show_weather", enabled) { value ->
                                        changeVisibility { it.copy(showWeather = value) }
                                    }
                                }
                            }
                            if (allowsFont) clockSetting("font") {
                                SettingsActionItem(stringResource(R.string.settings_font), fontLabel, fontIndex, count,
                                    "clock_font_selector", enabled = enabled) { dialog = "font" }
                            }
                            clockSetting("weight") {
                                ClockSlider(stringResource(R.string.clock_weight), face.weight, defaults.weight,
                                    100..face.maxWeight(draft.layout), step = 100,
                                    index = weightIndex, count = count, tag = "clock_weight", enabled = enabled,
                                    onChange = { value -> changeFace { it.copy(weight = value) } })
                            }
                            clockSetting("size") {
                                ClockSlider(stringResource(R.string.clock_size), face.size, defaults.size, 32..144, step = 1,
                                    index = weightIndex + 1, count = count, tag = "clock_size", enabled = enabled,
                                    onChange = { value -> changeFace { it.copy(size = value) } })
                            }
                            clockSetting("spacing") {
                                ClockSlider(stringResource(if (draft.layout.week) R.string.clock_letter_spacing else R.string.clock_digit_spacing),
                                    face.letterSpacing, defaults.letterSpacing, -16..16, step = 1,
                                    index = weightIndex + 2, count = count, tag = "clock_letter_spacing", enabled = enabled,
                                    onChange = { value -> changeFace { it.copy(letterSpacing = value) } })
                            }
                            clockSetting("hour_minute_spacing") {
                                ClockSlider(stringResource(R.string.clock_hour_minute_spacing), face.hourMinuteSpacing, defaults.hourMinuteSpacing,
                                    -16..64, step = 1, index = weightIndex + 3, count = count,
                                    tag = "clock_hour_minute_spacing", enabled = enabled,
                                    onChange = { value -> changeFace { it.copy(hourMinuteSpacing = value) } })
                            }
                            clockSetting("font_shadow") {
                                ClockSlider(stringResource(R.string.clock_font_shadow), face.fontShadow, defaults.fontShadow,
                                    0..24, step = 1, index = weightIndex + 4, count = count,
                                    tag = "clock_font_shadow", enabled = enabled,
                                    onChange = { value -> changeFace { it.copy(fontShadow = value) } })
                            }
                            clockSetting("leading_zero") {
                                SettingsToggleItem(stringResource(R.string.clock_show_leading_zero), null,
                                    face.showLeadingZero, leadingZeroIndex, count, "clock_show_leading_zero", enabled) { value ->
                                    changeFace { it.copy(showLeadingZero = value) }
                                }
                            }
                            clockSetting("separation") {
                                SettingsToggleItem(stringResource(R.string.clock_separate_digits), stringResource(R.string.clock_separate_digits_summary),
                                    face.separateDigits, separationIndex, count, "clock_separate_digits", enabled) { value ->
                                    changeFace { it.copy(separateDigits = value) }
                                }
                            }
                            if (showsColon) clockSetting("colon") {
                                SettingsToggleItem(stringResource(R.string.clock_show_colon), stringResource(R.string.clock_show_colon_summary),
                                    face.showColon, separationIndex + 1, count, "clock_show_colon", enabled) { value ->
                                    changeFace { it.copy(showColon = value) }
                                }
                            }
                            clockSetting("calendar_above_clock") {
                                SettingsToggleItem(stringResource(R.string.calendar_above_clock), null,
                                    uiState.settings.calendarAboveClock, orderIndex, count, "calendar_above_clock", enabled) { value ->
                                    actions.updateSettings { it.copy(calendarAboveClock = value) }
                                }
                            }
                            if (stylePage) clockSetting("weather_icons") {
                                SettingsActionItem(stringResource(R.string.clock_weather_icons), stringResource(R.string.clock_weather_icons_monochrome),
                                    count - 1, count, "clock_weather_icons", enabled = enabled) { dialog = "weather_icons" }
                            }
                        }
                    }
                }
                if (editingColor == null) SettingsModeToolbar(
                    listOf(stringResource(R.string.clock_tab_presets), stringResource(R.string.clock_tab_style), stringResource(R.string.icon_designer_color)),
                    page.ordinal, enabled, "clock_style_toolbar",
                    ClockEditorPage.entries.map { "clock_tab:${it.name}" },
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = FloatingToolbarDefaults.ScreenOffset + LocalSettingsBottomInset.current),
                ) { selectPage(ClockEditorPage.entries[it]) }
            }
        }
    }
}

private fun LazyListScope.clockSetting(key: String, content: @Composable () -> Unit) {
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
private fun ClockLayoutSelector(style: ClockStyle, layouts: List<ClockLayout>, count: Int, enabled: Boolean, onSelect: (ClockLayout) -> Unit) {
    // Restore a useful initial viewport, but never reorder or auto-scroll on selection.
    val listState = key(layouts) { rememberLazyListState(initialFirstVisibleItemIndex = (layouts.indexOf(style.layout) - 1).coerceAtLeast(0)) }
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
                    ClockLayoutOption(style.copy(layout = layout), selected = layout == style.layout,
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
    Box(modifier.fillMaxWidth().testTag("clock_style_preview")
        .animateBounds(
            lookaheadScope = lookaheadScope,
            modifier = Modifier.heightIn(min = if (style.layout.stacked) 232.dp else 168.dp),
            boundsTransform = boundsTransform,
        ).clipToBounds(), contentAlignment = Alignment.CenterStart) {
        CompositionLocalProvider(LocalLauncherAppearance provides appearance) {
          MaterialTheme(typography = LocalLauncherTypography.current) {
            HomeClockHeader(
                now = now, event = null, onDateClick = {}, onClockClick = {},
                showBattery = true, clockStyle = style,
                showClock = true, showCalendar = true,
                calendarAboveClock = uiState.settings.calendarAboveClock,
                weather = uiState.weather.snapshot?.current?.takeIf { it.temperature != null }
                    ?: WeatherCurrent(WeatherTemperature(22.0, "c"), WeatherCondition.Clear, true, null),
                modifier = Modifier.fillMaxWidth().padding(
                    start = uiState.settings.listAppearance.startPadding,
                    end = uiState.settings.listAppearance.endPadding, top = 12.dp, bottom = 12.dp),
                interactive = false, clockTag = "clock_style_preview_text", dateTag = "clock_style_preview_date",
            )
          }
        }
    }
}

@Composable
private fun ClockSlider(
    title: String, value: Int, default: Int, range: IntRange, step: Int,
    index: Int, count: Int, tag: String, enabled: Boolean, onChange: (Int) -> Unit,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.testTag("${tag}_item"),
        content = {
            Column {
                Text(title)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(value = value.toFloat(), onValueChange = {
                        onChange((range.first + ((it - range.first) / step).roundToInt() * step).coerceIn(range))
                    }, valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = if (step > 1) (range.last - range.first) / step - 1 else 0,
                        enabled = enabled, modifier = Modifier.weight(1f).testTag(tag).semantics { contentDescription = title })
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
    ClockColorPart.Battery -> R.string.clock_color_battery
    ClockColorPart.Weather -> R.string.clock_color_weather
})
