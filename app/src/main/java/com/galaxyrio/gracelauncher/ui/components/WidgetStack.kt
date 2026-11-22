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
import com.galaxyrio.gracelauncher.ui.widgets.WidgetViewCache
import com.galaxyrio.gracelauncher.ui.widgets.rememberHostedWidget

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
    if (items.isEmpty()) {
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
    val viewCache = remember(stack.id) { WidgetViewCache() }
    SideEffect { viewCache.retain(widgets.map { it.widgetId }) }
    BoxWithConstraints(modifier.fillMaxWidth().then(semantics)) {
        val columns = appStackColumns(maxWidth.value, displayedIconSize.value)
        val step = (maxWidth - AppStackInset.dp * 2 - displayedIconSize) / (columns - 1)
        val pages = stackPages(items, { it is HomeLayout }, columns)
        val count = pages.size
        // Restart on the first page when pages are added or removed, keeping page % count meaningful.
        val pager = key(stack.id, count) {
            rememberPagerState(initialPage = widgetStackStartPage(count)) { widgetStackPageCount(count) }
        }
        // Pages the pager prepares off screen can show the same widget as a page on screen (two
        // widgets sit on both sides of each other), so only on-screen pages hold a widget view.
        val onScreen by remember(pager) { derivedStateOf { pager.layoutInfo.visiblePagesInfo.map { it.index }.toSet() } }
        val position = stringResource(if (hasApps) R.string.app_stack_position else R.string.widget_stack_position,
            pager.currentPage % count + 1, count)
        val indicator = if (count > 1) IndicatorHeight else 0.dp
        val height = if (fillHeight && constraints.hasBoundedHeight)
            (maxHeight - indicator).value.toInt().coerceAtLeast(48) else tallest
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
                            onClick = {}, onLongClick = onEdit))) {
                        AppStackRow(content.filterIsInstance<LauncherApp>(), step, iconSize, displayedIconSize,
                            inputEnabled, onLaunch, onDetails)
                    }
                }
                else if (onScreen.isEmpty() || page in onScreen) HomeWidget(widget, hosted.getValue(widget.widgetId), height,
                    editing = false, enabled = inputEnabled, hapticsEnabled = uiState.settings.allowHapticFeedback,
                    onLongPress = onEdit, viewCache = viewCache)
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
