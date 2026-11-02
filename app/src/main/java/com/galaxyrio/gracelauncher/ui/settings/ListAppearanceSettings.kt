@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.resolvedFontColor
import kotlin.math.roundToInt

private val ListAppearanceDraftSaver = Saver<ListAppearance, String>(
    save = { it.encode() }, restore = { ListAppearance.decode(it) },
)

private enum class ListAppearancePage { List, Notification }

@Composable
internal fun ListAppearanceSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit, onManageFonts: () -> Unit) = Box(Modifier.fillMaxSize()) {
    val settings = uiState.settings
    var draft by rememberSaveable(settings.listAppearance, stateSaver = ListAppearanceDraftSaver) { mutableStateOf(settings.listAppearance) }
    val savedSize = settings.iconDesign?.design?.iconSize ?: 100
    var iconSize by remember(savedSize) { mutableIntStateOf(savedSize) }
    val previewState = rememberListAppearancePreviewState()
    var page by rememberSaveable { mutableStateOf(ListAppearancePage.List) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val colorPage = dialog?.takeIf { it == "font_color" }
    // Keep the source row in place while its color picker occupies the controls pane.
    val controlsState = rememberLazyListState()
    val notificationControlsState = rememberLazyListState()
    val enabled = LocalSettingsStorageState.current.canEdit
    val wallpaper = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons, iconSize)
    val primary = MaterialTheme.colorScheme.primary
    val heroSpec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    val heroBounds = remember(heroSpec) { BoundsTransform { _, _ -> heroSpec } }
    val heroShape = ListItemDefaults.segmentedShapes(0, 1).shape
    val controlsShape = settingsPreviewControlsShape()
    fun change(transform: (ListAppearance) -> ListAppearance) {
        draft = transform(draft).normalized()
        actions.updateSettings { it.copy(listAppearance = transform(it.listAppearance).normalized()) }
    }
    fun saveSize() {
        val value = iconSize
        actions.updateSettings { current ->
            val defaults = IconDesign.defaults(uiState.themedIcons)
            val icon = current.iconDesign ?: ItemIcon.Theme
            // Change only this property: per-app designs and every other All option are retained.
            current.copy(iconDesign = icon.copy(design = (icon.design?.withThemeDefaults(defaults) ?: defaults).copy(iconSize = value)))
        }
    }
    fun closeColor() {
        if (enabled) {
            val color = draft.fontColor
            change { it.copy(fontColor = color) }
        }
        dialog = null
    }
    BackHandler(colorPage != null) { closeColor() }
    when (dialog) {
        "font" -> FontPickerDialog(settings.fontLibrary, draft.fontId,
            onSelect = { id -> change { it.copy(fontId = id) }; dialog = null },
            onManage = { dialog = null; onManageFonts() }, onDismiss = { dialog = null }, tag = "list_font_picker")
    }
    SettingsPreviewScaffold(uiState, stringResource(R.string.settings_favorites_app_list), "list_appearance",
        { if (colorPage != null) closeColor() else onBack() }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            val notifications = settings.shortcutsFolders
            val previewMaxHeight = (maxHeight - LocalSettingsBottomInset.current).coerceAtLeast(0.dp) * .45f
            Column(Modifier.fillMaxSize()) {
                ListAppearancePreview(uiState, draft, iconSize,
                    Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = previewMaxHeight)
                        .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()), popupState = previewState)
                SharedTransitionLayout(Modifier.weight(1f).fillMaxWidth().settingsPreviewScrim()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp).clipToBounds()) {
                    AnimatedContent(colorPage, modifier = Modifier.fillMaxSize(),
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "listAppearanceColorHero") { activeColor ->
                        val visibility = this
                        val colorModifier: @Composable (String) -> Modifier = { field ->
                            Modifier.sharedBounds(rememberSharedContentState("list_color:$field"), visibility,
                                boundsTransform = heroBounds, clipInOverlayDuringTransition = OverlayClip(heroShape))
                        }
                        if (activeColor != null) {
                            IconDesignerColorPicker(stringResource(R.string.list_font_color), draft.fontColor, primary.toArgb(), null,
                                onChange = { if (enabled && activeColor == colorPage) {
                                    draft = draft.copy(fontColor = it)
                                } }, onClose = { if (activeColor == colorPage) closeColor() },
                                modifier = Modifier.padding(bottom = 16.dp).then(colorModifier(activeColor)), fallback = wallpaper.text.toArgb(),
                                fontColors = settings.fontColors)
                        } else if (page == ListAppearancePage.Notification) {
                            LazyColumn(Modifier.fillMaxSize().clip(controlsShape)
                                .testTag("list_notification_controls"), state = notificationControlsState,
                                contentPadding = PaddingValues(bottom = 96.dp + LocalSettingsBottomInset.current),
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                item("single_line") {
                                    SettingsToggleItem(stringResource(R.string.popup_single_line_previews),
                                        stringResource(R.string.popup_single_line_previews_summary),
                                        notifications.singleLineNotificationPreviews, 0, 2, "popup_single_line_previews",
                                        enabled = notifications.enabled) { value ->
                                        actions.updateSettings { it.copy(shortcutsFolders = it.shortcutsFolders.copy(singleLineNotificationPreviews = value)) }
                                    }
                                }
                                item("multiple_apps") {
                                    SettingsToggleItem(stringResource(R.string.popup_multiple_app_previews),
                                        stringResource(R.string.popup_multiple_app_previews_summary),
                                        notifications.multipleAppNotificationPreviews, 1, 2, "popup_multiple_app_previews",
                                        enabled = notifications.enabled) { value ->
                                        actions.updateSettings { it.copy(shortcutsFolders = it.shortcutsFolders.copy(multipleAppNotificationPreviews = value)) }
                                    }
                                }
                            }
                        } else LazyColumn(Modifier.fillMaxSize().clip(controlsShape)
                            .testTag("list_appearance_controls"), state = controlsState,
                            contentPadding = PaddingValues(bottom = 96.dp + LocalSettingsBottomInset.current), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                            item("icon_size") {
                                DesignerSegment(0, 10, "list_icon_size") {
                                    Text(stringResource(R.string.icon_designer_icon_size_title))
                                    DesignerSlider(stringResource(R.string.icon_designer_icon_size), iconSize.toFloat(), 100f, 80f..150f,
                                        enabled, "list_icon_size_slider", onFinished = { saveSize() }) { iconSize = it.roundToInt() }
                                }
                            }
                            item("app_spacing") {
                                ListSpacingSlider(stringResource(R.string.list_app_spacing), draft.appSpacing, 16, 0..64, 1, "list_app_spacing",
                                    { draft = draft.copy(appSpacing = it) }) { value -> change { it.copy(appSpacing = value) } }
                            }
                            item("side_padding") {
                                ListSpacingSlider(stringResource(R.string.list_side_padding), draft.sidePadding, 44, 8..80, 2, "list_side_padding",
                                    { draft = draft.copy(sidePadding = it) }) { value -> change { it.copy(sidePadding = value) } }
                            }
                            item("icon_gap") {
                                ListSpacingSlider(stringResource(R.string.list_icon_name_gap), draft.iconNameGap, 20, 0..64, 3, "list_icon_name_gap",
                                    { draft = draft.copy(iconNameGap = it) }) { value -> change { it.copy(iconNameGap = value) } }
                            }
                            item("symbol_offset") {
                                ListSpacingSlider(stringResource(R.string.list_symbol_horizontal_offset), draft.symbolHorizontalOffset,
                                    0, -16..16, 4, "list_symbol_horizontal_offset", { draft = draft.copy(symbolHorizontalOffset = it) },
                                    centered = true) { value -> change { it.copy(symbolHorizontalOffset = value) } }
                            }
                            item("font") { SettingsActionItem(stringResource(R.string.settings_font), appFontLabel(draft.fontId),
                                5, 10, "list_font", onClick = { dialog = "font" }) }
                            item("font_size") {
                                ListSpacingSlider(stringResource(R.string.clock_size), draft.fontSize, 16, 10..32, 6, "list_font_size",
                                    { draft = draft.copy(fontSize = it) }, suffix = "sp") { value -> change { it.copy(fontSize = value) } }
                            }
                            item("font_color") {
                                SettingsActionItem(stringResource(R.string.list_font_color),
                                    fontColorSummary(draft.fontColor, onWallpaper = true),
                                    7, 10, "list_font_color", modifier = colorModifier("font_color"), trailing = {
                                        Surface(Modifier.size(24.dp), shape = MaterialTheme.shapes.small,
                                            color = resolvedFontColor(draft.fontColor, onWallpaper = true)) {}
                                    }, onClick = { dialog = "font_color" })
                            }
                            item("names") {
                                DesignerSegment(8, 10, "list_show_names") {
                                    Text(stringResource(R.string.list_show_names))
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                                        listOf(true, false).forEach { home ->
                                            ToggleButton(checked = draft.showNames(favorites = home), enabled = enabled,
                                                onCheckedChange = { checked -> change { current ->
                                                    val homeNames = if (home) checked else current.showNames(favorites = true)
                                                    val appListNames = if (!home) checked else current.showNames(favorites = false)
                                                    current.copy(names = when {
                                                        homeNames && appListNames -> AppNameVisibility.Both
                                                        homeNames -> AppNameVisibility.Favorites
                                                        appListNames -> AppNameVisibility.AppList
                                                        else -> AppNameVisibility.Neither
                                                    })
                                                } }, shapes = if (home) ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                    else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)
                                                    .testTag(if (home) "list_names_home" else "list_names_app_list"),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                                                Text(stringResource(if (home) R.string.settings_home_screen else R.string.settings_folder_app_list))
                                            }
                                        }
                                    }
                                }
                            }
                            item("names_in_folders") {
                                SettingsToggleItem(stringResource(R.string.list_names_in_folders), null,
                                    draft.applyNameVisibilityToFolders, 9, 10, "list_names_in_folders", enabled = enabled) { value ->
                                    change { it.copy(applyNameVisibilityToFolders = value) }
                                }
                            }
                        }
                    }
                }
            }
            if (colorPage == null) SettingsModeToolbar(
                listOf(stringResource(R.string.list_appearance_tab_list), stringResource(R.string.list_appearance_tab_notification)),
                page.ordinal, enabled, "list_appearance_toolbar",
                ListAppearancePage.entries.map { "list_appearance_tab:${it.name}" },
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = FloatingToolbarDefaults.ScreenOffset + LocalSettingsBottomInset.current),
                itemWidth = 120.dp,
            ) { page = ListAppearancePage.entries[it] }
        }
    }
    ListAppearancePreviewPopup(uiState, draft, iconSize, previewState)
}

@Composable
private fun ListSpacingSlider(label: String, value: Int, default: Int, range: IntRange, index: Int, tag: String,
    onPreview: (Int) -> Unit, suffix: String = "dp", centered: Boolean = false, onCommit: (Int) -> Unit) {
    DesignerSegment(index, 10, tag) {
        DesignerSlider(label, value.toFloat(), default.toFloat(), range.first.toFloat()..range.last.toFloat(),
            LocalSettingsStorageState.current.canEdit, "${tag}_slider", suffix, centered = centered,
            onFinished = { onCommit(it.roundToInt()) }, onChange = { onPreview(it.roundToInt()) })
    }
}
