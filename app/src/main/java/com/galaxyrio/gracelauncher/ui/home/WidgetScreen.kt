package com.galaxyrio.gracelauncher.ui.home

import com.galaxyrio.gracelauncher.ui.theme.WithoutFontShadow

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.galaxyrio.gracelauncher.ui.theme.FontShadowText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.scrollBy as semanticsScrollBy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.platform.widthBounds
import com.galaxyrio.gracelauncher.platform.heightBounds
import com.galaxyrio.gracelauncher.platform.widgetTargetSize
import com.galaxyrio.gracelauncher.platform.widgetGridOuterPadding
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.*
import com.galaxyrio.gracelauncher.ui.overlays.PopupWidgetControls
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.effectiveDisplay
import com.galaxyrio.gracelauncher.ui.widgets.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/** An unbounded grid with a stationary touch surface and viewport-only composition. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun WidgetScreen(
    uiState: LauncherUiState, actions: LauncherActions, topInset: Dp, bottomInset: Dp,
    editing: Boolean, enabled: Boolean, onEditingChange: (Boolean) -> Unit, onEdit: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit, onAppDetails: (LauncherApp) -> Unit,
    onOpenFolder: (LauncherFolder, Rect) -> Unit,
    modifier: Modifier = Modifier,
    /** Fork: opens a stack's own editor. */
    onEditStack: (LauncherFolder) -> Unit = {},
) {
    val title = stringResource(R.string.widget_screen_title)
    val owner = remember(title) { widgetScreenOwner(title) }
    // Keep disabled/unavailable profiles saved, but do not expose their launchers.
    val savedEntries = uiState.popupItems(owner, emptyList()).filter {
        (it.key != PrivateSpaceFolderKey || uiState.canFavoritePrivateSpace) &&
            (it.key != WorkProfileFolderKey || uiState.canFavoriteWorkProfile)
    }
    val widgetDrafts = remember { mutableStateMapOf<String, HomeLayout>() }
    val dirtyWidgetDrafts = remember { mutableStateMapOf<String, Boolean>() }
    val entries = savedEntries.map { entry ->
        widgetDrafts[entry.key]?.takeIf { it.widgetId == entry.widget?.widgetId }
            ?.let { entry.copy(widget = it) } ?: entry
    }
    fun saveWidgetDraft(key: String, value: HomeLayout) {
        dirtyWidgetDrafts.remove(key)
        actions.updatePopup(owner, emptyList()) { current -> current.map { entry ->
            // Mapping existing entries, never appending, prevents a late flush from
            // bringing back a widget deleted while its controls were closing.
            if (entry.key == key && entry.widget?.widgetId == value.widgetId) entry.copy(widget = value) else entry
        } }
    }
    val flushWidgetDraft by rememberUpdatedState<(String) -> Unit>({ key ->
        if (dirtyWidgetDrafts[key] == true) widgetDrafts[key]?.let { saveWidgetDraft(key, it) }
    })
    LaunchedEffect(savedEntries) {
        val saved = savedEntries.associateBy { it.key }
        widgetDrafts.keys.toList().forEach { key ->
            val value = saved[key]?.widget
            if (value == null || value == widgetDrafts[key]) {
                widgetDrafts.remove(key)
                dirtyWidgetDrafts.remove(key)
            }
        }
    }
    val byKey = entries.associateBy { it.key }
    val stacks = uiState.folders.filter { it.isAppStack && it.key in byKey }.associateBy { it.key }
    val widgetStacks = uiState.folders.filter { it.isWidgetStack && it.key in byKey }.associateBy { it.key }
    val currentStackKeys by rememberUpdatedState(stacks.keys)
    val interactiveStackKeys by rememberUpdatedState(stacks.keys + widgetStacks.keys)
    val widgetKeys = entries.filter { it.widget != null }.map { it.key }.toSet()
    val inputEnabled = enabled && !uiState.isLoadingSettings && !uiState.settingsLoadFailed
    val editable = inputEnabled && !uiState.settings.widgetScreen.editingLocked
    val currentInputEnabled by rememberUpdatedState(inputEnabled)
    val currentEditable by rememberUpdatedState(editable)
    val currentEditingChange by rememberUpdatedState(onEditingChange)
    val currentEdit by rememberUpdatedState(onEdit)
    val currentWidgetKeys by rememberUpdatedState(widgetKeys)
    val persist by rememberUpdatedState<(List<WidgetGridCell>, String?) -> Unit>({ cells, removed ->
        if (removed != null) { widgetDrafts.remove(removed); dirtyWidgetDrafts.remove(removed) }
        actions.updatePopup(owner, emptyList()) { current ->
            val positions = collapseWidgetStacks(cells, currentStackKeys).associateBy { it.key }
            current.filterNot { it.key == removed }.map { entry ->
                positions[entry.key]?.let { cell -> entry.copy(grid = cell.placement().copy(
                    restoreColumn = entry.grid?.restoreColumn, restoreColumns = entry.grid?.restoreColumns)) } ?: entry
            }.sortedWith(compareBy({ it.grid?.row ?: Int.MAX_VALUE }, { it.grid?.column ?: Int.MAX_VALUE }))
        }
    })
    val grid = remember { WidgetGridState({ cells, removed -> persist(cells, removed) }, { currentEditingChange(it) }) }
    var previousSelection by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(grid.selectedKey) {
        if (previousSelection != grid.selectedKey) previousSelection?.let { flushWidgetDraft(it) }
        previousSelection = grid.selectedKey
    }
    LaunchedEffect(editing, enabled, editable) {
        if (!editing || !editable) grid.cancelInteraction()
        if (!enabled) grid.scrolling.stopScroll()
    }
    DisposableEffect(grid) { onDispose {
        dirtyWidgetDrafts.keys.toList().forEach { flushWidgetDraft(it) }
        grid.cancelInteraction()
    } }
    val appearance = LocalLauncherAppearance.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(haptics)
    val iconCoordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    val launch by rememberUpdatedState<(String) -> Unit>({ key ->
        uiState.findItem(key)?.let { app ->
            val bounds = iconCoordinates[key]?.takeIf { it.isAttached }?.boundsInWindow()
            if (app.folderId != null) {
                val folder = when (app.folderId) {
                    PrivateSpaceFolderId -> uiState.privateFolder
                    WorkProfileFolderId -> uiState.workFolder
                    else -> uiState.folders.firstOrNull { it.id == app.folderId }
                }
                if (folder != null) onOpenFolder(folder, bounds ?: Rect.Zero)
            } else if (bounds != null && actions.launchAppAt != null) actions.launchAppAt.invoke(app, bounds) else onLaunchApp(app)
        }
    })
    val startDrag: (String, Offset) -> Unit = { key, pointer ->
        if (currentEditable && !grid.busy) {
            grid.startDrag(key, pointer)
            currentHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val currentStart by rememberUpdatedState(startDrag)
    val removeLabel = stringResource(R.string.widget_remove)
    val resizeLabel = stringResource(R.string.widget_resize)
    val previousLabel = stringResource(R.string.favorites_move_up)
    val nextLabel = stringResource(R.string.favorites_move_down)
    val infoLabel = stringResource(R.string.app_info)
    val editLabel = stringResource(R.string.widget_screen_edit)

    var screenCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().testTag("widget_screen")
        .onGloballyPositioned { screenCoordinates = it }) {
        val density = LocalDensity.current
        val context = LocalContext.current
        val pageWidth = maxWidth
        val columns = uiState.settings.widgetScreen.columns.coerceIn(2, 8)
        // Symmetric page margins; the alphabet is an overlay, not reserved grid space.
        val side = 16.dp
        val gap = 8.dp
        val cellWidth = ((maxWidth - side * 2) / columns).coerceAtLeast(1.dp)
        val cellHeight = cellWidth.coerceIn(88.dp, 112.dp)
        val widthPx = with(density) { cellWidth.toPx() }
        val heightPx = with(density) { cellHeight.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val origin = with(density) { Offset(side.toPx(), (topInset + 12.dp).toPx()) }
        val viewportPx = with(density) { maxHeight.toPx() }
        val bottomPx = with(density) { bottomInset.toPx() + 12.dp.toPx() }
        val widgetRevision = LocalWidgetHost.current?.revision
        val specs = remember(entries.map { Triple(it.key, it.widget?.widgetId, it.widget?.widgetProvider) }, columns, widthPx, heightPx, gapPx, widgetRevision) {
            val manager = AppWidgetManager.getInstance(context)
            entries.filter { it.widget != null }.associate { entry ->
                val info = runCatching { manager.getAppWidgetInfo(entry.widget!!.widgetId) }.getOrNull()
                val outer = info?.let { widgetGridOuterPadding(context, it) }
                entry.key to widgetGridSpec(info, columns, widthPx, heightPx,
                    gapPx + (outer?.left ?: 0) + (outer?.right ?: 0),
                    gapPx)
            }
        }
        val stackIconSize = 40f * appearance.iconSize / 100f
        val stackColumns = appStackColumns((maxWidth - side * 2 - gap).value, stackIconSize)
        val stackRows = stacks.mapValues { (_, stack) ->
            val count = uiState.popupItems(uiState.folderItem(stack), emptyList()).count { it.widget == null && uiState.findItem(it.key) != null }
            // Fork: app stacks page instead of expanding, so they are one row plus page dots when they overflow.
            ceil((appStackHeight(stackIconSize, paged = count > stackColumns) + gap.value) / cellHeight.value).toInt().coerceAtLeast(1)
        }
        val widgetStackSizes = widgetStacks.mapValues { (_, stack) ->
            // Fork: apps in a widget stack are pages too, so they count towards its page dots.
            val count = uiState.popups[stack.key].orEmpty().count { it.widget?.hasWidget == true || it.widget == null }
            val height = requireNotNull(stack.widgetStack).layoutHeight(count)
            WidgetGridSize(columns, ceil((height + gap.value) / cellHeight.value).toInt().coerceIn(1, 20))
        }
        val baseCells = remember(entries, columns, specs, stacks.keys, widgetStackSizes) {
            restoreWidgetGrid(entries, columns, specs.mapValues { it.value.first } + stacks.keys.associateWith { WidgetGridSize(columns, 1) } + widgetStackSizes)
        }
        val resolved = remember(baseCells, stackRows) { expandWidgetStacks(baseCells, stackRows) }
        LaunchedEffect(resolved, entries, uiState.isLoadingSettings, uiState.settingsLoadFailed) {
            if (!grid.busy) {
                grid.synchronize(resolved)
                if (!uiState.isLoadingSettings && !uiState.settingsLoadFailed &&
                    baseCells.any { cell ->
                        val saved = byKey[cell.key]?.grid
                        saved == null || saved.column != cell.column || saved.row != cell.row ||
                            saved.columns != cell.size.columns || saved.rows != cell.size.rows
                    }) persist(resolved, null)
            }
        }
        SideEffect { grid.configure(columns, widthPx, heightPx, origin, viewportPx, bottomPx, widgetKeys) }
        val hitCell by rememberUpdatedState<(Offset) -> WidgetGridCell?>({ pointer ->
            grid.cellAt(pointer) ?: grid.cells.firstOrNull { cell ->
                byKey[cell.key]?.widget?.widgetFullWidth == true &&
                    pointer.y >= grid.viewportPosition(cell).y && pointer.y < grid.viewportPosition(cell).y + cell.size.rows * heightPx
            }
        })
        fun setFullWidth(key: String, value: HomeLayout) {
            if (!editable || grid.busy) return
            val fullWidth = value.widgetFullWidth
            val source = byKey[key] ?: return
            if (source.widget == null) return
            val normalCells = collapseWidgetStacks(grid.cells, currentStackKeys)
            val cell = normalCells.firstOrNull { it.key == key } ?: return
            val placement = cell.placement().copy(restoreColumn = source.grid?.restoreColumn,
                restoreColumns = source.grid?.restoreColumns).withFullWidth(fullWidth, columns)
            val moved = cell.copy(column = placement.column ?: 0,
                size = cell.size.copy(columns = placement.columns))
            val positions = compactWidgetRows(moveWidgetGrid(normalCells, moved, columns))
            val byPosition = positions.associateBy { it.key }
            actions.updatePopup(owner, emptyList()) { current ->
                current.map { entry ->
                    val position = byPosition[entry.key] ?: return@map entry
                    val stored = position.placement().copy(
                        restoreColumn = if (entry.key == key) placement.restoreColumn else entry.grid?.restoreColumn,
                        restoreColumns = if (entry.key == key) placement.restoreColumns else entry.grid?.restoreColumns)
                    entry.copy(grid = stored, widget = if (entry.key == key && entry.widget?.widgetId == value.widgetId) value else entry.widget)
                }.sortedWith(compareBy({ it.grid?.row ?: Int.MAX_VALUE }, { it.grid?.column ?: Int.MAX_VALUE }))
            }
            grid.synchronize(expandWidgetStacks(positions, stackRows))
        }
        val selectedWidget = byKey[grid.selectedKey]?.widget
        val showControls = editable && grid.draggingKey == null && selectedWidget != null
        var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var selectedWidgetTop by remember(grid.selectedKey) { mutableFloatStateOf(Float.NaN) }
        var controlsBottom by remember(grid.selectedKey) { mutableFloatStateOf(Float.NaN) }
        val revealGap = with(density) { (WidgetEditOutlineGap + 12.dp).toPx() }
        val contentReveal by rememberWidgetEditorOffset(showControls, selectedWidgetTop.takeIf { it.isFinite() },
            controlsBottom.takeIf { it.isFinite() } ?: 0f, revealGap, grid.busy)
        val currentContentReveal by rememberUpdatedState(contentReveal)
        LaunchedEffect(grid.draggingKey, heightPx) {
            if (grid.draggingKey != null) {
                var last = withFrameNanos { it }
                while (isActive && grid.draggingKey != null) {
                    val now = withFrameNanos { it }
                    val seconds = ((now - last) / 1_000_000_000f).coerceIn(0f, .032f)
                    last = now
                    grid.autoScroll(with(density) { 72.dp.toPx() }, with(density) { 640.dp.toPx() } * seconds, currentContentReveal)
                }
            }
        }
        // The fade belongs to the stationary content viewport. Controls remain a
        // separate sibling, while the translated layer contains the whole input tree.
        Box(Modifier.fillMaxSize().statusBarContentFade()) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = contentReveal }
            .onGloballyPositioned { gridCoordinates = it }.testTag("widget_screen_grid")
            // Keep this node enabled during a held drag. Consumed pointer changes stop scrolling;
            // toggling/removing gesture modifiers here would cancel the same pointer stream.
            .scrollable(grid.scrolling, Orientation.Vertical, enabled = enabled)
            .widgetGridInput(grid, { hitCell(it) }, { currentInputEnabled }, { currentEditable }, { currentWidgetKeys }, { interactiveStackKeys }, { launch(it) },
                { key, point -> currentStart(key, point) }, { currentEdit() })
            .semantics {
                verticalScrollAxisRange = ScrollAxisRange({ grid.scroll }, { grid.maxScroll })
                customActions = listOf(CustomAccessibilityAction(editLabel) { if (inputEnabled) onEdit(); inputEnabled })
                semanticsScrollBy { _, y ->
                    if (!enabled || grid.busy) false else { scope.launch { grid.scrolling.scrollBy(-y) }; true }
                }
            }) {
            if (entries.isEmpty()) WithoutFontShadow {
                FilledTonalButton(onClick = onEdit, enabled = inputEnabled,
                    modifier = Modifier.align(Alignment.Center).testTag("widget_screen_add")) {
                    LauncherIcon(LauncherSymbol.Plus)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.widget_screen_add_items))
                }
            }
            // Render identity is independent of row order. In particular, moving a native
            // widget upward must not detach its AndroidView while it owns a touch stream.
            // Keep the key outside visibility branches so animation state survives culling.
            val renderCells = remember(grid.cells) { grid.cells.sortedBy { it.key } }
            renderCells.forEach { cell -> key(cell.key) {
                val entry = byKey[cell.key]
                val fullWidth = entry?.widget?.widgetFullWidth == true
                val dragging = grid.draggingKey == cell.key
                val selected = grid.selectedKey == cell.key
                val destination = IntOffset((origin.x + cell.column * widthPx).roundToInt(), (origin.y + cell.row * heightPx).roundToInt())
                val dragPosition = grid.dragPosition + origin
                val target = if (dragging) IntOffset(dragPosition.x.roundToInt(), dragPosition.y.roundToInt()) else destination
                val animated by animateIntOffsetAsState(target,
                    if (dragging || (selected && grid.resizing)) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(), label = "gridReflow")
                val scale by animateFloatAsState(if (dragging) 1.06f else 1f, spring(dampingRatio = .7f), label = "gridLift")
                val position = if (dragging) dragPosition else if (grid.resizing && selected)
                    Offset(destination.x.toFloat(), destination.y.toFloat()) else Offset(animated.x.toFloat(), animated.y.toFloat())
                val width = if (fullWidth) pageWidth else cellWidth * cell.size.columns
                val stackHeight by animateDpAsState(cellHeight * cell.size.rows,
                    MaterialTheme.motionScheme.defaultSpatialSpec(), label = "stackGridHeight")
                val height = if (cell.key in stacks) stackHeight else cellHeight * cell.size.rows
                val top = position.y - grid.scroll
                val destinationTop = destination.y - grid.scroll
                fun inViewport(y: Float) = y + contentReveal + cell.size.rows * heightPx >= -heightPx &&
                    y + contentReveal <= viewportPx + heightPx
                // Keep departing content until its animated position leaves the viewport,
                // and create incoming content at the start of its move, not at the endpoint.
                if (entry != null && (dragging || selected || inViewport(top) || inViewport(destinationTop))) {
                    val app = uiState.findItem(entry.key)
                    val hosted = entry.widget?.let { rememberHostedWidget(it) }
                    val label = app?.label ?: entry.widget?.widgetLabel ?: stringResource(R.string.popup_item_unavailable)
                    DisposableEffect(cell.key) { onDispose { iconCoordinates.remove(cell.key) } }
                    Box(Modifier.absoluteOffset { IntOffset((position.x - if (fullWidth) origin.x else 0f).roundToInt(), (position.y - grid.scroll).roundToInt()) }
                        .wrapContentSize(Alignment.TopStart, unbounded = true).size(width, height)
                        .zIndex(if (dragging) 3f else if (selected) 2f else 0f)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .padding(horizontal = if (fullWidth) 0.dp else gap / 2, vertical = gap / 2).testTag("widget_screen_item:${entry.key}")
                        .semantics {
                            contentDescription = label
                            if (app != null && cell.key !in stacks && cell.key !in widgetStacks) onClick { if (enabled) launch(cell.key); enabled }
                            customActions = if (!editable) emptyList() else buildList {
                                add(CustomAccessibilityAction(previousLabel) { grid.moveBy(cell.key, -1); true })
                                add(CustomAccessibilityAction(nextLabel) { grid.moveBy(cell.key, 1); true })
                                add(CustomAccessibilityAction(removeLabel) { grid.remove(cell.key); true })
                                if (entry.widget != null) add(CustomAccessibilityAction(resizeLabel) { grid.selectWidget(cell.key); true })
                                if (app != null) add(CustomAccessibilityAction(infoLabel) { onAppDetails(app); true })
                            }
                        }) {
                        val dragBackground = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .3f)
                        Box(Modifier.matchParentSize()
                            // Round only the drag backdrop; clipping this ancestor
                            // would cut off the widget's resize outline and handles.
                            .drawBehind {
                                if (dragging && entry.widget == null)
                                    drawRoundRect(dragBackground, cornerRadius = CornerRadius(16.dp.toPx()))
                            }
                            .then(if (entry.widget == null && cell.key !in widgetStacks)
                                Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)) {
                            val stack = stacks[cell.key]
                            val widgetStack = widgetStacks[cell.key]
                            if (widgetStack != null) com.galaxyrio.gracelauncher.ui.widgets.WidgetStack(widgetStack, uiState,
                                enabled = enabled && !grid.busy, gridManaged = true, modifier = Modifier.fillMaxWidth(),
                                onLaunch = { item, bounds -> actions.launchAppAt?.invoke(item, bounds) ?: onLaunchApp(item) })
                            else if (stack != null) AppStack(stack, uiState,
                                onLaunch = { item, bounds -> actions.launchAppAt?.invoke(item, bounds) ?: onLaunchApp(item) },
                                onDetails = onAppDetails, onEdit = { onEditStack(stack) },
                                enabled = enabled && !grid.busy, gridManaged = true,
                                modifier = Modifier.fillMaxWidth())
                            else if (entry.widget != null && hosted != null) {
                                // Normal grid cells retain their previous inset externally; the
                                // host itself no longer adds hidden horizontal padding.
                                val padding = hosted.info?.let { widgetGridOuterPadding(context, it, fullWidth) }
                                val left = with(density) { (padding?.left ?: 0).toDp() }
                                val right = with(density) { (padding?.right ?: 0).toDp() }
                                Box(Modifier.fillMaxSize().padding(start = left, end = right)
                                    .drawBehind {
                                        if (dragging) {
                                            // Expand around the actual widget surface, after its
                                            // horizontal inset, without resizing the host or cell.
                                            val inset = WidgetEditOutlineGap.toPx()
                                            drawRoundRect(dragBackground, topLeft = Offset(-inset, -inset),
                                                size = Size(size.width + inset * 2, size.height + inset * 2),
                                                cornerRadius = CornerRadius(16.dp.toPx() + inset))
                                        }
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        if (selected) gridCoordinates?.takeIf { it.isAttached }?.let { viewport ->
                                            // Both coordinates share the translated ancestor, so
                                            // this remains the unshifted top throughout the spring.
                                            selectedWidgetTop = viewport.localPositionOf(coordinates, Offset.Zero).y
                                        }
                                    }) {
                                    Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                                        .background(entry.widget.widgetBackgroundColor())) {
                                        HomeWidget(entry.widget, hosted, (height - gap).value.roundToInt(),
                                            editing = selected && editable, enabled = enabled, hapticsEnabled = false,
                                            editGesturesEnabled = editable,
                                            onLongPress = {},
                                            onDragStart = { x, y ->
                                                val leftOffset = if (fullWidth) -origin.x else gapPx / 2 + (padding?.left ?: 0)
                                                currentStart(cell.key, grid.viewportPosition(cell) + Offset(x + leftOffset, y + gapPx / 2))
                                            },
                                            onDrag = { x, y -> grid.drag(Offset(x, y)) }, onDragEnd = grid::finishDrag,
                                            contentScale = entry.widget.popupContentScale / 100f)
                                    }
                                    if (editable && selected) {
                                        // Frame geometry follows actual widget content, not the
                                        // grid cell or its retained normal horizontal inset.
                                        Box(Modifier.matchParentSize().pointerInput(Unit) {
                                            awaitPointerEventScope { while (true) awaitPointerEvent() }
                                        })
                                        val providerLimits = specs[cell.key]?.second ?: WidgetResizeLimits(1, columns, 1, 20, true, true)
                                        val limits = if (fullWidth) providerLimits.copy(minColumns = columns, maxColumns = columns, horizontal = false) else providerLimits
                                        WidgetResizeFrame(grid, cell.key, limits, resizeLabel, fullWidth)
                                    }
                                }
                            } else Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                if (app != null) AppIcon(app, Modifier.onGloballyPositioned { iconCoordinates[cell.key] = it },
                                    size = 44.dp.coerceAtMost((cellWidth - 16.dp).coerceAtLeast(16.dp)), display = app.effectiveDisplay(appearance))
                                else LauncherIcon(LauncherSymbol.Apps, tint = appearance.text)
                                Spacer(Modifier.height(6.dp))
                                Text(label, style = listLabelStyle().copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            } }
        }
        }
        if (showControls) {
            val selectedKey = requireNotNull(grid.selectedKey)
            Box(Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp, start = 4.dp, end = 4.dp)
                .widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight * .55f).zIndex(5f)
                .onGloballyPositioned { coordinates ->
                    screenCoordinates?.takeIf { it.isAttached }?.let { screen ->
                        controlsBottom = screen.localPositionOf(coordinates, Offset.Zero).y + coordinates.size.height
                    }
                }
                .verticalScroll(rememberScrollState())) {
                key(selectedKey) {
                    PopupWidgetControls(selectedWidget, onChange = { value, save ->
                        if (value.widgetFullWidth != selectedWidget.widgetFullWidth) {
                            widgetDrafts.remove(selectedKey)
                            dirtyWidgetDrafts.remove(selectedKey)
                            setFullWidth(selectedKey, value)
                        } else {
                            widgetDrafts[selectedKey] = value
                            if (save) saveWidgetDraft(selectedKey, value) else dirtyWidgetDrafts[selectedKey] = true
                        }
                    }, onDone = {
                        flushWidgetDraft(selectedKey)
                        grid.deselect()
                    }, onDelete = {
                        widgetDrafts.remove(selectedKey)
                        dirtyWidgetDrafts.remove(selectedKey)
                        grid.remove(selectedKey)
                    })
                }
            }
        }
    }
}

/** Apps and blank space share one recognizer. No moving hit target or clickable can cancel a held drag. */
private fun Modifier.widgetGridInput(
    grid: WidgetGridState, cellAt: (Offset) -> WidgetGridCell?, enabled: () -> Boolean, canEdit: () -> Boolean, nativeWidgets: () -> Set<String>,
    interactiveItems: () -> Set<String>,
    onTap: (String) -> Unit, onHold: (String, Offset) -> Unit, onEmptyHold: () -> Unit,
) = pointerInput(grid) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled() || down.isConsumed) return@awaitEachGesture
        val cell = cellAt(down.position)
        val interactive = cell?.key in interactiveItems()
        // Native widgets retain button/collection interaction and report their own long press.
        if (cell != null && cell.key in nativeWidgets() && grid.selectedKey != cell.key) {
            if (grid.selectedKey != null) grid.deselect()
            return@awaitEachGesture
        }
        var released = false
        var cancelled = false
        var pointer = down.position
        val holdTimeout = if (cell?.key in nativeWidgets())
            com.galaxyrio.gracelauncher.platform.widgetLongPressTimeout(viewConfiguration.longPressTimeoutMillis)
        else viewConfiguration.longPressTimeoutMillis
        withTimeoutOrNull(holdTimeout) {
            while (true) {
                val event = awaitPointerEvent(if (interactive) PointerEventPass.Initial else PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || event.changes.count { it.pressed } > 1 || (!interactive && change.isConsumed) ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop || !enabled()) {
                    cancelled = true; break
                }
                pointer = change.position
                if (!change.pressed) { released = true; break }
                // A parent pager/scrollable may claim this movement after our Main pass.
                if (!interactive && awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) { cancelled = true; break }
            }
        }
        if (cancelled) return@awaitEachGesture
        if (released) {
            if (grid.selectedKey != null) grid.deselect()
            else if (cell != null && !interactive) onTap(cell.key)
        } else if (!canEdit()) {
            // Still recognize taps while locked; only the held editing path is blocked.
            return@awaitEachGesture
        } else if (cell == null) {
            grid.deselect()
            onEmptyHold()
        } else {
            onHold(cell.key, pointer)
            var completed = false
            try {
                while (grid.draggingKey == cell.key && enabled() && canEdit()) {
                    // Consume before the pager/native descendants; especially UP, even without a MOVE.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1) break
                    grid.drag(change.position - pointer)
                    pointer = change.position
                    change.consume()
                    if (!change.pressed) { grid.finishDrag(false); completed = true; break }
                }
            } finally { if (!completed) grid.finishDrag(true) }
        }
    }
}

@Composable
private fun BoxScope.WidgetResizeFrame(grid: WidgetGridState, key: String, limits: WidgetResizeLimits, label: String, fullWidth: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val latestLimits by rememberUpdatedState(limits)
    Box(Modifier.matchParentSize().drawBehind {
        val gap = WidgetEditOutlineGap.toPx()
        val stroke = 2.dp.toPx()
        val left = if (fullWidth) stroke / 2 else -gap
        val right = if (fullWidth) size.width - stroke / 2 else size.width + gap
        drawRoundRect(color, topLeft = Offset(left, -gap), size = Size(right - left, size.height + gap * 2),
            cornerRadius = CornerRadius(16.dp.toPx() + gap), style = Stroke(stroke))
    })
    val edges = buildList {
        if (limits.horizontal && limits.minColumns < limits.maxColumns) { add(WidgetResizeEdge.Left); add(WidgetResizeEdge.Right) }
        if (limits.vertical && limits.minRows < limits.maxRows) { add(WidgetResizeEdge.Top); add(WidgetResizeEdge.Bottom) }
    }
    for (edge in edges) key(edge) {
        val alignment = when (edge) {
            WidgetResizeEdge.Left -> Alignment.CenterStart
            WidgetResizeEdge.Right -> Alignment.CenterEnd
            WidgetResizeEdge.Top -> Alignment.TopCenter
            WidgetResizeEdge.Bottom -> Alignment.BottomCenter
        }
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val handleOffset = 20.dp + WidgetEditOutlineGap
        Box(Modifier.align(alignment).offset(
            x = when (edge) { WidgetResizeEdge.Left -> -handleOffset; WidgetResizeEdge.Right -> handleOffset; else -> 0.dp },
            y = when (edge) { WidgetResizeEdge.Top -> -handleOffset; WidgetResizeEdge.Bottom -> handleOffset; else -> 0.dp }).size(40.dp)
            .onGloballyPositioned { coordinates = it }
            .semantics { contentDescription = label }
            .testTag("widget_resize:${edge.name.lowercase()}")
            .pointerInput(grid, key, edge) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    grid.startResize(key)
                    var previous = coordinates?.localToRoot(down.position) ?: down.position
                    var completed = false
                    try {
                        while (grid.resizing) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.count { it.pressed } > 1) break
                            val position = coordinates?.localToRoot(change.position) ?: change.position
                            grid.resize(edge, position - previous, latestLimits)
                            previous = position
                            change.consume()
                            if (!change.pressed) { grid.finishResize(false); completed = true; break }
                        }
                    } finally { if (!completed) grid.finishResize(true) }
                }
            }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).background(color, CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
        }
    }
}

/** Provider dimensions are pixels; convert once to grid spans, including the host's widget padding. */
private fun widgetGridSpec(info: AppWidgetProviderInfo?, columns: Int, width: Float, height: Float,
    horizontalPadding: Float, verticalPadding: Float): Pair<WidgetGridSize, WidgetResizeLimits> {
    if (info == null) return WidgetGridSize(2.coerceAtMost(columns), 2) to WidgetResizeLimits(1, columns, 1, 20, true, true)
    val x = info.widthBounds(horizontalPadding, width, columns)
    val y = info.heightBounds(verticalPadding, height, 20)
    val target = if (Build.VERSION.SDK_INT >= 31) widgetTargetSize(x, y, info.targetCellWidth, info.targetCellHeight)
        else x.initial to y.initial
    return WidgetGridSize(target.first, target.second) to WidgetResizeLimits(x.min, x.max, y.min, y.max, x.canResize, y.canResize)
}
