@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.LauncherApps
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.overlays.FavoritesReorderState
import com.galaxyrio.gracelauncher.ui.overlays.rememberReorderTranslation
import com.galaxyrio.gracelauncher.ui.overlays.FolderPlacementDialog
import com.galaxyrio.gracelauncher.ui.overlays.TextEntryDialog
import com.galaxyrio.gracelauncher.ui.widgets.WidgetStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

/** Creation and selection share the manager, just as folders and app stacks do. */
@Composable
internal fun WidgetStackSettings(
    uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit, onEdit: (String) -> Unit,
    selectedKeys: Set<String>? = null, onToggle: (LauncherApp) -> Unit = {},
    newPlacement: FolderPlacement = FolderPlacement.Favorites,
) {
    val stacks = uiState.folders.filter { it.isWidgetStack }
    var creating by rememberSaveable { mutableStateOf(false) }
    var openingId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openingId, uiState.folders, uiState.settingsSaveFailed) {
        val id = openingId ?: return@LaunchedEffect
        if (uiState.folders.any { it.id == id }) {
            openingId = null
            onEdit(id)
        } else if (uiState.settingsSaveFailed) openingId = null
    }
    val title = stringResource(R.string.widget_stacks_title)
    val createTitle = stringResource(R.string.widget_stack_create)
    SettingsScaffold(title, "settings_widget_stacks", onBack) { padding ->
        SettingsList(padding) {
            item { Spacer(Modifier.height(24.dp)) }
            item {
                SettingsActionItem(createTitle, null, 0, 1, "widget_stack_create",
                    enabled = openingId == null, leading = { LauncherIcon(LauncherSymbol.Plus) }) { creating = true }
            }
            if (stacks.isEmpty()) {
                item {
                    Text(stringResource(R.string.widget_stacks_empty), Modifier.padding(horizontal = 4.dp, vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                item { SettingsHeading(title) }
                itemsIndexed(stacks, key = { _, stack -> stack.id }) { index, stack ->
                    val count = uiState.popups[stack.key].orEmpty().count { it.widget != null }
                    val checked = selectedKeys?.contains(stack.key)
                    SettingsActionItem(stack.name,
                        pluralStringResource(R.plurals.settings_folder_summary, count, count, stack.placement.label()),
                        index, stacks.size, "widget_stack:${stack.id}",
                        modifier = Modifier.semantics {
                            if (checked != null) {
                                role = Role.Checkbox
                                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                            }
                        }, leading = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (checked != null) Checkbox(checked, onCheckedChange = null,
                                    enabled = LocalSettingsStorageState.current.canEdit)
                                LauncherIcon(LauncherSymbol.Widgets)
                            }
                        }) {
                        if (selectedKeys == null) onEdit(stack.id) else onToggle(uiState.folderItem(stack))
                    }
                }
            }
        }
    }
    if (creating) TextEntryDialog(createTitle, "", { creating = false }, { name ->
        val id = UUID.randomUUID().toString()
        actions.saveFolder(LauncherFolder(id, name, emptyList(), newPlacement, widgetStack = WidgetStackStyle()))
        creating = false
        openingId = id
    })
}

@Composable
internal fun WidgetStackPickerScreen(
    uiState: LauncherUiState, actions: LauncherActions, selectedKeys: Set<String>,
    onBack: () -> Unit, onToggle: (LauncherApp) -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { if (editingId != null) editingId = null else onBack() }
    if (editingId != null) WidgetStackEditorSettings(editingId, uiState, actions, { editingId = null })
    else WidgetStackSettings(uiState, actions, onBack, { editingId = it }, selectedKeys, onToggle, FolderPlacement.None)
}

private data class StackWidgetBackground(val enabled: Boolean, val color: IconColor?, val alpha: Float)

@Composable
internal fun WidgetStackEditorSettings(
    folderId: String?, uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
) {
    val folder = uiState.folders.firstOrNull { it.id == folderId && it.isWidgetStack }
    if (folder == null) {
        SettingsScaffold(stringResource(R.string.widget_stack_edit), "widget_stack_editor", onBack) { padding ->
            Text(stringResource(R.string.folder_unavailable), Modifier.padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val owner = uiState.folderItem(folder)
    val style = requireNotNull(folder.widgetStack)
    val entries = uiState.popups[folder.key].orEmpty().filter { it.widget?.hasWidget == true }
    val keys = entries.map { it.key }
    val providers = rememberStackWidgetProviders(entries, uiState)
    var selectedKey by rememberSaveable(folder.id) { mutableStateOf<String?>(null) }
    val selected = entries.firstOrNull { it.key == selectedKey } ?: entries.firstOrNull()
    val selectedWidget = selected?.widget
    LaunchedEffect(keys) { if (selectedKey !in keys) selectedKey = keys.firstOrNull() }
    var draftHeight by remember(folder.id) { mutableIntStateOf(style.height) }
    var heightDirty by remember(folder.id) { mutableStateOf(false) }
    var pendingHeight by remember(folder.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(style.height) {
        if (pendingHeight == style.height) pendingHeight = null
        if (!heightDirty && pendingHeight == null) draftHeight = style.height
    }
    val savedBackground = StackWidgetBackground(selectedWidget?.popupBackground ?: false,
        selectedWidget?.popupBackgroundColor, selectedWidget?.popupBackgroundAlpha ?: 1f)
    var background by remember(selected?.key) { mutableStateOf(savedBackground) }
    var backgroundDirty by remember(selected?.key) { mutableStateOf(false) }
    var pendingBackground by remember(selected?.key) { mutableStateOf<StackWidgetBackground?>(null) }
    LaunchedEffect(selected?.key, savedBackground) {
        if (pendingBackground == savedBackground) pendingBackground = null
        // A previous asynchronous save must not replace a newer picker/slider draft.
        if (!backgroundDirty && pendingBackground == null) background = savedBackground
    }
    var rename by rememberSaveable(folder.id) { mutableStateOf(false) }
    var placement by rememberSaveable(folder.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(folder.id) { mutableStateOf(false) }
    var removeKey by rememberSaveable(folder.id) { mutableStateOf<String?>(null) }
    var colorOpen by rememberSaveable(folder.id) { mutableStateOf(false) }
    // Fork: apps and shortcuts in a stack are edited in the pop-up editor, which shows them with the widgets.
    var editingApps by rememberSaveable(folder.id) { mutableStateOf(false) }
    if (editingApps) {
        BackHandler { editingApps = false }
        com.galaxyrio.gracelauncher.ui.overlays.PopupEditorScreen(owner, uiState, actions, onBack = { editingApps = false })
        return
    }
    val appCount = uiState.popups[folder.key].orEmpty().count { it.widget == null }
    val enabled = LocalSettingsStorageState.current.canEdit && uiState.itemRevision > 0
    val scheme = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    SettingsSearchScroll(list)
    val previewScroll = rememberScrollState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val commitOrder by rememberUpdatedState<(List<String>) -> Unit>({ order ->
        actions.updatePopup(owner, emptyList()) { current ->
            val byKey = current.associateBy { it.key }
            order.mapNotNull(byKey::get) + current.filterNot { it.key in order }
        }
    })
    val reorder = remember(folder.id, list) {
        FavoritesReorderState(list, keys, { commitOrder(it) }) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(keys) { reorder.synchronize(keys) }
    LaunchedEffect(selected?.key) { previewScroll.scrollTo(0) }
    LaunchedEffect(reorder.draggingKey, density) {
        if (reorder.draggingKey == null) return@LaunchedEffect
        val edge = with(density) { 64.dp.toPx() }
        val speed = with(density) { 640.dp.toPx() }
        var previous = withFrameNanos { it }
        while (isActive && reorder.draggingKey != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previous) / 1_000_000_000f).coerceIn(0f, .032f)
            previous = frame
            reorder.autoScroll(edge, speed * seconds)
        }
    }
    fun saveBackground(value: StackWidgetBackground, all: Boolean = false) {
        val key = selected?.key ?: return
        background = value
        backgroundDirty = false
        pendingBackground = value
        actions.updatePopup(owner, emptyList()) { current -> current.map { entry ->
            if (entry.widget != null && (all || entry.key == key)) entry.copy(widget = entry.widget.copy(
                popupBackground = value.enabled, popupBackgroundColor = value.color,
                popupBackgroundAlpha = value.alpha.coerceIn(0f, 1f))) else entry
        } }
    }
    fun closeColor() {
        if (enabled) saveBackground(background)
        colorOpen = false
    }
    fun select(key: String) {
        if (key == selected?.key) return
        if (colorOpen) closeColor()
        selectedKey = key
    }
    // Fork: the preview keeps the stack's apps too; they are pages after the widgets.
    val previewEntries = uiState.popups[folder.key].orEmpty().map { entry ->
        if (entry.key == selected?.key && entry.widget != null) entry.copy(widget = entry.widget.copy(
            popupBackground = background.enabled, popupBackgroundColor = background.color,
            popupBackgroundAlpha = background.alpha)) else entry
    }
    val previewState = uiState.copy(popups = uiState.popups + (folder.key to previewEntries))
    val previewFolder = folder.copy(widgetStack = style.copy(height = draftHeight))
    val heroSpec = MaterialTheme.motionScheme.slowSpatialSpec<Rect>()
    val heroBounds = remember(heroSpec) { BoundsTransform { _, _ -> heroSpec } }
    val heroShape = ListItemDefaults.segmentedShapes(0, 1).shape
    val back = { if (colorOpen) closeColor() else onBack() }
    BackHandler(colorOpen) { closeColor() }
    SettingsScaffold(stringResource(R.string.widget_stack_edit), "widget_stack_editor", back, fixedCollapsed = true,
        actions = { TextButton(onClick = back) { Text(stringResource(R.string.done)) } }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            // Keep settings usable on short screens. Tall previews scroll at their real size.
            val previewLimit = minOf(560.dp, (maxHeight - 160.dp).coerceAtLeast(80.dp))
            val previewHeight = minOf(requireNotNull(previewFolder.widgetStack).layoutHeight(entries.size).dp, previewLimit)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(previewHeight)
                    .clipToBounds().testTag("widget_stack_live_preview")) {
                    Column(Modifier.fillMaxWidth().verticalScroll(previewScroll)) {
                        WidgetStack(previewFolder, previewState, preview = true,
                            selectedKey = selected?.key, onSelectedKeyChange = ::select)
                    }
                }
                SharedTransitionLayout(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp).clipToBounds()) {
                    AnimatedContent(colorOpen && selected != null, modifier = Modifier.fillMaxSize(),
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "widgetStackColorHero") { pickingColor ->
                        val colorModifier = Modifier.sharedBounds(rememberSharedContentState("widget_stack_background"), this,
                            boundsTransform = heroBounds, clipInOverlayDuringTransition = OverlayClip(heroShape))
                        if (pickingColor) {
                            key(selected?.key) {
                            IconDesignerColorPicker(stringResource(R.string.widget_stack_background_color), background.color,
                                scheme.primaryContainer.toArgb(), null,
                                onChange = { if (enabled && colorOpen) {
                                    backgroundDirty = true
                                    background = background.copy(enabled = true, color = it)
                                } },
                                onClose = { if (colorOpen) closeColor() },
                                modifier = Modifier.padding(bottom = 16.dp).then(colorModifier), fallback = scheme.primaryContainer.toArgb())
                            }
                        } else LazyColumn(Modifier.fillMaxSize().testTag("widget_stack_controls"), state = list,
                            userScrollEnabled = reorder.draggingKey == null,
                            contentPadding = PaddingValues(bottom = 24.dp + LocalSettingsBottomInset.current),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                            item("add_widget") {
                                SettingsActionItem(stringResource(R.string.settings_add_widget), null, 0, entries.size + 1,
                                    "widget_stack_add_widget", enabled = enabled && reorder.draggingKey == null,
                                    leading = { LauncherIcon(LauncherSymbol.Plus) }) { actions.addPopupWidget(owner, emptyList()) }
                            }
                            val byKey = entries.associateBy { it.key }
                            val ordered = reorder.keys.mapNotNull(byKey::get)
                            itemsIndexed(ordered, key = { _, entry -> FavoritesReorderState.itemKey(entry.key) }) { index, entry ->
                                val widget = requireNotNull(entry.widget)
                                val app = providers[entry.key]
                                val dragging = reorder.draggingKey == entry.key
                                val offset by rememberReorderTranslation(reorder, entry.key)
                                val moveUp = stringResource(R.string.favorites_move_up)
                                val moveDown = stringResource(R.string.favorites_move_down)
                                SegmentedListItem(onClick = { select(entry.key) }, enabled = enabled && reorder.draggingKey == null,
                                    shapes = ListItemDefaults.segmentedShapes(index + 1, entries.size + 1),
                                    colors = ListItemDefaults.segmentedColors(containerColor = if (entry.key == selected?.key || dragging)
                                        scheme.surfaceContainerHighest else scheme.surfaceBright),
                                    modifier = Modifier.then(if (dragging) Modifier else Modifier.animateItem())
                                        .zIndex(if (dragging || reorder.drop?.key == entry.key) 1f else 0f)
                                        .graphicsLayer { translationY = offset }.testTag("widget_stack_member:${entry.key}")
                                        .semantics {
                                            this.selected = entry.key == selected?.key
                                            customActions = buildList {
                                                if (enabled && index > 0) add(CustomAccessibilityAction(moveUp) { reorder.moveBy(entry.key, -1) })
                                                if (enabled && index < ordered.lastIndex) add(CustomAccessibilityAction(moveDown) { reorder.moveBy(entry.key, 1) })
                                            }
                                        },
                                    leadingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Checkbox(true, { checked -> if (!checked) removeKey = entry.key }, enabled = enabled,
                                                modifier = Modifier.testTag("widget_stack_remove:${entry.key}"))
                                            if (app != null) AppIcon(app, size = 36.dp) else LauncherIcon(LauncherSymbol.Widgets)
                                        }
                                    }, content = {
                                        Text(app?.label ?: widget.widgetProvider?.substringBefore('/') ?: stringResource(R.string.popup_item_unavailable),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }, supportingContent = {
                                        Text(widget.widgetLabel.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }, trailingContent = {
                                        var moveMenu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { moveMenu = true }, enabled = enabled,
                                                modifier = Modifier.testTag("widget_stack_reorder:${entry.key}")
                                                    .pointerInput(reorder, entry.key, enabled) {
                                                        if (enabled) detectDragGestures(
                                                            onDragStart = { reorder.start(entry.key) },
                                                            onDragEnd = { reorder.finish(false) }, onDragCancel = { reorder.finish(true) },
                                                        ) { change, amount -> change.consume(); reorder.drag(amount.y) }
                                                    }) {
                                                Icon(painterResource(R.drawable.ms_drag_indicator), stringResource(R.string.favorites_reorder, widget.widgetLabel.orEmpty()))
                                            }
                                            DropdownMenu(moveMenu, { moveMenu = false }) {
                                                DropdownMenuItem(text = { Text(moveUp) }, enabled = index > 0,
                                                    onClick = { reorder.moveBy(entry.key, -1); moveMenu = false })
                                                DropdownMenuItem(text = { Text(moveDown) }, enabled = index < ordered.lastIndex,
                                                    onClick = { reorder.moveBy(entry.key, 1); moveMenu = false })
                                            }
                                        }
                                    })
                            }
                            item("apps_gap") { Spacer(Modifier.height(20.dp)) }
                            item("apps") {
                                SettingsActionItem(stringResource(R.string.widget_stack_apps),
                                    pluralStringResource(R.plurals.widget_stack_apps_summary, appCount, appCount), 0, 1,
                                    "widget_stack_apps", enabled = enabled && reorder.draggingKey == null,
                                    leading = { LauncherIcon(LauncherSymbol.Apps) }) { editingApps = true }
                            }
                            item("height_gap") { Spacer(Modifier.height(20.dp)) }
                            item("height") {
                                DesignerSegment(0, 1, "widget_stack_height") {
                                    DesignerSlider(stringResource(R.string.widget_stack_height), draftHeight.toFloat(), 240f,
                                        80f..1200f, enabled, "widget_stack_height_slider", suffix = "dp",
                                        onFinished = {
                                            heightDirty = false
                                            pendingHeight = it.roundToInt()
                                            actions.updateWidgetStackStyle(folder.id, style.copy(height = it.roundToInt()))
                                        }) {
                                        heightDirty = true
                                        draftHeight = it.roundToInt()
                                    }
                                }
                            }
                            if (selected != null) {
                                item("background_gap") { Spacer(Modifier.height(20.dp)) }
                                item("background_enabled") {
                                    SettingsToggleItem(stringResource(R.string.popup_widget_background), selected.widget?.widgetLabel,
                                        background.enabled, 0, 4, "widget_stack_background_enabled", enabled = enabled) {
                                        saveBackground(background.copy(enabled = it))
                                    }
                                }
                                item("background_color") {
                                    SettingsActionItem(stringResource(R.string.widget_stack_background_color), null, 1, 4,
                                        "widget_stack_background_color", enabled = enabled, modifier = colorModifier,
                                        trailing = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(Modifier.size(24.dp), shape = MaterialTheme.shapes.small,
                                                    color = Color(background.color?.resolve(scheme.primaryContainer.toArgb(), colorScheme = scheme)
                                                        ?: scheme.primaryContainer.toArgb())) {}
                                                IconButton(onClick = { saveBackground(background.copy(color = null)) },
                                                    enabled = enabled && background.color != null,
                                                    modifier = Modifier.testTag("widget_stack_background_color_reset")) {
                                                    Icon(painterResource(R.drawable.ms_restart_alt), stringResource(R.string.clock_reset_value,
                                                        stringResource(R.string.widget_stack_background_color)))
                                                }
                                            }
                                        }) { colorOpen = true }
                                }
                                item("background_alpha") {
                                    DesignerSegment(2, 4, "widget_stack_opacity") {
                                        DesignerSlider(stringResource(R.string.widget_stack_opacity), background.alpha * 100f, 100f,
                                            0f..100f, enabled, "widget_stack_opacity_slider",
                                            onFinished = { saveBackground(background.copy(alpha = it / 100f, enabled = it > 0f)) }) {
                                            backgroundDirty = true
                                            background = background.copy(alpha = it / 100f, enabled = it > 0f)
                                        }
                                    }
                                }
                                item("background_all") {
                                    SettingsActionItem(stringResource(R.string.widget_stack_apply_background_all), null, 3, 4,
                                        "widget_stack_background_all", enabled = enabled) { saveBackground(background, all = true) }
                                }
                            }
                            item("details_gap") { Spacer(Modifier.height(20.dp)) }
                            item("name") {
                                SettingsActionItem(folder.name, stringResource(R.string.widget_stack_name), 0, 2, "widget_stack_name",
                                    leading = { LauncherIcon(LauncherSymbol.Widgets) }) { rename = true }
                            }
                            item("placement") {
                                SettingsActionItem(stringResource(R.string.widget_stack_placement), folder.placement.label(), 1, 2,
                                    "widget_stack_placement", leading = { LauncherIcon(LauncherSymbol.Folder) }) { placement = true }
                            }
                            item("delete") {
                                TextButton(onClick = { confirmDelete = true }, enabled = enabled, modifier = Modifier.testTag("widget_stack_delete")) {
                                    Text(stringResource(R.string.widget_stack_delete), color = scheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (rename) TextEntryDialog(stringResource(R.string.widget_stack_name), folder.name, { rename = false }, {
        actions.rename(owner, it); rename = false
    })
    if (placement) FolderPlacementDialog(folder.placement, folder.appListAtBottom, { placement = false },
        titleRes = R.string.widget_stack_placement) { selectedPlacement, atBottom ->
        actions.updateFolder(folder.id, null, selectedPlacement, atBottom); placement = false
    }
    val removing = entries.firstOrNull { it.key == removeKey }
    if (removing != null) AlertDialog(onDismissRequest = { removeKey = null },
        title = { Text(stringResource(R.string.widget_stack_remove_widget_title)) },
        text = { Text(stringResource(R.string.widget_stack_remove_widget_confirmation, removing.widget?.widgetLabel.orEmpty())) },
        confirmButton = { TextButton(onClick = {
            actions.updatePopup(owner, emptyList()) { current -> current.filterNot { it.key == removing.key } }
            removeKey = null
        }, enabled = enabled) { Text(stringResource(R.string.settings_delete), color = scheme.error) } },
        dismissButton = { TextButton(onClick = { removeKey = null }) { Text(stringResource(R.string.cancel)) } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.widget_stack_delete)) },
        text = { Text(stringResource(R.string.widget_stack_delete_confirmation, folder.name)) },
        confirmButton = { TextButton(onClick = { actions.deleteFolder(folder.id); onBack() }, enabled = enabled) {
            Text(stringResource(R.string.settings_delete), color = scheme.error)
        } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

/** Resolve the provider's profile and reuse the icon-pack-aware app identity where possible. */
@Composable
private fun rememberStackWidgetProviders(entries: List<PopupItem>, state: LauncherUiState): Map<String, LauncherApp> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val result by produceState(emptyMap<String, LauncherApp>(), entries.map { it.key to it.widget?.widgetId },
        state.allApps, state.settings.enabledIconPackPackages, configuration) {
        value = withContext(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(context)
            val launchers = context.getSystemService(LauncherApps::class.java)
            val pm = context.packageManager
            val density = context.resources.displayMetrics.densityDpi
            val repository = AppRepository.get(context, IconPackRepository.get(context))
            entries.mapNotNull { entry ->
                val widget = entry.widget ?: return@mapNotNull null
                val info = runCatching { manager.getAppWidgetInfo(widget.widgetId) }.getOrNull()
                val provider = info?.provider ?: widget.widgetProvider?.let(ComponentName::unflattenFromString) ?: return@mapNotNull null
                val profile = info?.profile ?: android.os.Process.myUserHandle()
                val known = state.allApps.firstOrNull { it.shortcut == null && it.packageName == provider.packageName &&
                    (it.user ?: android.os.Process.myUserHandle()) == profile }
                if (known != null) return@mapNotNull entry.key to known
                val appInfo = runCatching { launchers.getApplicationInfo(provider.packageName, 0, profile) }.getOrNull()
                val label = runCatching { appInfo?.loadLabel(pm)?.toString() }.getOrNull() ?: provider.packageName
                val icon = runCatching {
                    (appInfo?.loadIcon(pm) ?: info?.loadIcon(context, density))?.let {
                        pm.getUserBadgedIcon(it, profile).toBitmap(96, 96).asImageBitmap()
                    }
                }.getOrNull()
                val app = LauncherApp(provider, pm.getUserBadgedLabel(label, profile).toString(), icon, user = profile)
                entry.key to runCatching {
                    repository.applyIconPacks(listOf(app), state.settings.enabledIconPackPackages).single()
                }.getOrDefault(app)
            }.toMap()
        }
    }
    return result
}
