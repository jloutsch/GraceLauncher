package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.widgets.HomeWidget
import com.galaxyrio.gracelauncher.ui.widgets.LocalWidgetHost
import com.galaxyrio.gracelauncher.ui.widgets.LocalWidgetSurfaceShown
import com.galaxyrio.gracelauncher.ui.widgets.WidgetViewCache
import com.galaxyrio.gracelauncher.ui.widgets.rememberHostedWidget
import com.galaxyrio.gracelauncher.ui.widgets.widgetBackgroundColor
import androidx.compose.ui.input.pointer.pointerInput

/** Enough pages that nobody reaches either end, so a swipe never falls through to the home pager. */
private const val WrapCycles = 1000
private val IndicatorHeight = StackDotsHeight.dp

/** Pager index of the first widget nearest the middle of the wrapping range. */
internal fun widgetStackStartPage(count: Int): Int =
    if (count <= 1) 0 else count * WrapCycles / 2 / count * count

internal fun widgetStackPageCount(count: Int): Int = if (count <= 1) count.coerceAtLeast(0) else count * WrapCycles

/**
 * One widget at a time; swiping either way wraps around. Only the visible pages host a
 * widget view, so each widget ID is bound to a single view even with two widgets.
 * Fork: apps in the stack get pages of their own, one row of icons each, centred in the
 * tallest widget's height.
 */
@Composable
internal fun WidgetStack(
    stack: LauncherFolder, uiState: LauncherUiState, onEdit: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, onWallpaper: Boolean = true,
    /** The Widget Screen grid sizes the cell; elsewhere the tallest widget sets the height. */
    fillHeight: Boolean = false,
    onLaunch: (LauncherApp, Rect) -> Unit = { _, _ -> }, onDetails: ((LauncherApp) -> Unit)? = null,
    iconSize: Dp = 40.dp,
    /** Height of the pages from the stack's style; null keeps the tallest widget's height. */
    fixedHeight: Int? = null,
    /** The settings editor's preview: widgets show but take no touches; the pager still swipes. */
    preview: Boolean = false,
    /** Keeps the editor's selected widget and the visible page in step, by popup entry key. */
    selectedKey: String? = null,
    onSelectedKeyChange: ((String) -> Unit)? = null,
) {
    val inputEnabled = enabled && LocalLauncherInputEnabled.current
    val appearance = LocalLauncherAppearance.current
    val symbolColor = if (onWallpaper) appearance.text else LocalContentColor.current
    val displayedIconSize = iconSize * (appearance.iconSize / 100f)
    val editLabel = stringResource(R.string.widget_stack_edit)
    val items: List<Any> = uiState.popups[stack.key].orEmpty().mapNotNull { entry ->
        entry.widget?.takeIf(HomeLayout::hasWidget)
            ?: if (entry.widget == null) uiState.findItem(entry.key)?.takeIf { it.folderId == null } else null
    }
    val widgets = items.filterIsInstance<HomeLayout>()
    val hasApps = items.size > widgets.size
    val semantics = Modifier.testTag("widget_stack:${stack.id}").semantics {
        customActions = listOf(CustomAccessibilityAction(editLabel) { if (inputEnabled) onEdit(); inputEnabled })
    }
    val widgetHost = LocalWidgetHost.current
    if (items.isEmpty()) {
        // An emptied stack keeps no widget views alive.
        SideEffect { widgetHost?.stackViews(stack.id)?.retain(emptyList()) { widgetHost?.host?.releaseView(it) } }
        Box(modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(72.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (onWallpaper) 0.25f else 1f))
            .then(semantics), contentAlignment = Alignment.Center) {
            IconButton(onClick = onEdit, enabled = inputEnabled, modifier = Modifier.semantics { contentDescription = editLabel }) {
                LauncherIcon(LauncherSymbol.Plus, tint = symbolColor)
            }
        }
        return
    }
    val hosted = widgets.associate { layout -> layout.widgetId to key(layout.widgetId) { rememberHostedWidget(layout) } }
    val appRowHeight = appStackHeight(displayedIconSize.value).toInt()
    val tallest = maxOf(widgets.maxOfOrNull { layout ->
        val widget = hosted.getValue(layout.widgetId)
        (layout.widgetHeightDp.takeIf { it > 0 } ?: widget.defaultHeight).coerceIn(widget.minHeight, widget.maxHeight)
    } ?: 0, if (hasApps) appRowHeight else 0)
    val viewCache = remember(stack.id, widgetHost) { widgetHost?.stackViews(stack.id) ?: WidgetViewCache() }
    // Fork: the same stack can be composed on Home, the Widget Screen, the drawer and pop-ups at once, but each
    // widget has one live view. Only the copy chosen by StackViewOwners hosts it; a copy that becomes the owner
    // composes its widget anew, which moves the view into its own slot.
    val owners = remember(stack.id, widgetHost) { widgetHost?.stackOwners(stack.id) }
    val slot = remember { Any() }
    val shown = LocalWidgetSurfaceShown.current
    DisposableEffect(owners, slot) { onDispose { owners?.remove(slot) } }
    SideEffect {
        viewCache.retain(widgets.map { it.widgetId }) { widgetHost?.host?.releaseView(it) }
        if (!shown) owners?.report(slot, 0f)
    }
    val hosting = owners == null || owners.owner() === slot
    BoxWithConstraints(modifier.fillMaxWidth().then(semantics).onGloballyPositioned { coordinates ->
        val area = coordinates.size.width.toFloat() * coordinates.size.height
        val visible = coordinates.boundsInWindow().let { it.width * it.height }
        owners?.report(slot, if (!shown || area <= 0f) 0f else visible / area)
    }) {
        val columns = appStackColumns(maxWidth.value, displayedIconSize.value)
        val step = (maxWidth - AppStackInset.dp * 2 - displayedIconSize) / (columns - 1)
        val pages = stackPages(items, { it is HomeLayout }, columns)
        val count = pages.size
        val currentCount by rememberUpdatedState(count)
        val pager = key(stack.id) {
            rememberPagerState(initialPage = widgetStackStartPage(count)) { widgetStackPageCount(currentCount) }
        }
        // Fork: when pages are added or removed (apps installed, the stack resized), stay on the same page
        // number instead of jumping back to the first one, and keep page % count meaningful.
        var laidOutCount by remember(stack.id) { mutableIntStateOf(count) }
        LaunchedEffect(count) {
            if (count != laidOutCount) {
                val current = pager.currentPage % laidOutCount.coerceAtLeast(1)
                laidOutCount = count
                pager.scrollToPage(widgetStackStartPage(count) + current.coerceAtMost((count - 1).coerceAtLeast(0)))
            }
        }
        // Pages the pager prepares off screen can show the same widget as a page on screen (two
        // widgets sit on both sides of each other), so only on-screen pages hold a widget view.
        val onScreen by remember(pager) { derivedStateOf { pager.layoutInfo.visiblePagesInfo.map { it.index }.toSet() } }
        val position = stringResource(if (hasApps) R.string.app_stack_position else R.string.widget_stack_position,
            pager.currentPage % count + 1, count)
        val indicator = if (count > 1) IndicatorHeight else 0.dp
        val height = if (fillHeight && constraints.hasBoundedHeight)
            (maxHeight - indicator).value.toInt().coerceAtLeast(48) else fixedHeight ?: tallest
        // Editor sync (upstream's settings page): a list selection turns to that widget by the shortest way round,
        // and a settled swipe reports the widget it landed on.
        val keyByWidget = remember(uiState.popups[stack.key]) {
            uiState.popups[stack.key].orEmpty().mapNotNull { entry -> entry.widget?.let { it.widgetId to entry.key } }.toMap()
        }
        fun pageKey(index: Int): String? = (pages.getOrNull(index)?.first() as? HomeLayout)?.let { keyByWidget[it.widgetId] }
        LaunchedEffect(selectedKey, count) {
            val target = (0 until count).firstOrNull { pageKey(it) == selectedKey } ?: return@LaunchedEffect
            val current = pager.currentPage
            if (current % count == target) return@LaunchedEffect
            val base = current - current % count + target
            val nearest = listOf(base - count, base, base + count).minBy { kotlin.math.abs(it - current) }
            pager.animateScrollToPage(nearest)
        }
        val reportSelection by rememberUpdatedState(onSelectedKeyChange)
        LaunchedEffect(pager, count) {
            snapshotFlow { pager.settledPage }.collect { page -> if (count > 0) pageKey(page % count)?.let { reportSelection?.invoke(it) } }
        }
        Column(Modifier.fillMaxWidth()) {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(height.dp),
                userScrollEnabled = inputEnabled && count > 1, key = { it }) { page ->
                val content = pages[page % count]
                val widget = content.first() as? HomeLayout
                if (widget == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(appRowHeight.dp)
                        .clip(RoundedCornerShape(displayedIconSize / 2 + AppStackInset.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (onWallpaper) 0.25f else 1f))
                        .then(if (fillHeight) Modifier else Modifier.combinedClickable(enabled = inputEnabled,
                            onClick = {}, onLongClick = onEdit))
                        .semantics { contentDescription = stack.name }) {
                        AppStackRow(content.filterIsInstance<LauncherApp>(), step, iconSize, displayedIconSize,
                            inputEnabled, onLaunch, onDetails)
                    }
                }
                else if (hosting && (onScreen.isEmpty() || page in onScreen)) Box(Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(24.dp)).background(widget.widgetBackgroundColor())) {
                    HomeWidget(widget, hosted.getValue(widget.widgetId), height,
                        editing = false, enabled = inputEnabled, hapticsEnabled = uiState.settings.allowHapticFeedback,
                        onLongPress = onEdit, editGesturesEnabled = !preview && !fillHeight,
                        contentScale = widget.popupContentScale / 100f, viewCache = viewCache, previewOnly = preview)
                    // Keep the widget out of the preview's hit testing without consuming the pager's swipes.
                    if (preview) Box(Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent() }
                    })
                }
                else Spacer(Modifier.fillMaxWidth().height(height.dp))
            }
            if (count > 1) StackPageDots(count, pager.currentPage % count, position, symbolColor)
        }
    }
}

/** Page dots under a widget or app stack; read out as one position, e.g. "Widget 2 of 3". */
@Composable
internal fun StackPageDots(count: Int, current: Int, position: String, color: Color) {
    Row(Modifier.fillMaxWidth().height(IndicatorHeight).clearAndSetSemantics {
            contentDescription = position; liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape)
                .background(color.copy(alpha = if (index == current) 0.9f else 0.35f)))
        }
    }
}
