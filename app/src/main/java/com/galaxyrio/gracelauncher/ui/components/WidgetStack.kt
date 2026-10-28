package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.widgets.HomeWidget
import com.galaxyrio.gracelauncher.ui.widgets.rememberHostedWidget

/** Enough pages that nobody reaches either end, so a swipe never falls through to the home pager. */
private const val WrapCycles = 1000
private val IndicatorHeight = 14.dp

/** Pager index of the first widget nearest the middle of the wrapping range. */
internal fun widgetStackStartPage(count: Int): Int =
    if (count <= 1) 0 else count * WrapCycles / 2 / count * count

internal fun widgetStackPageCount(count: Int): Int = if (count <= 1) count.coerceAtLeast(0) else count * WrapCycles

/**
 * One widget at a time; swiping either way wraps around. Only the visible pages host a
 * widget view, so each widget ID is bound to a single view even with two widgets.
 */
@Composable
internal fun WidgetStack(
    stack: LauncherFolder, uiState: LauncherUiState, onEdit: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, onWallpaper: Boolean = true,
    /** The Widget Screen grid sizes the cell; elsewhere the tallest widget sets the height. */
    fillHeight: Boolean = false,
) {
    val inputEnabled = enabled && LocalLauncherInputEnabled.current
    val appearance = LocalLauncherAppearance.current
    val symbolColor = if (onWallpaper) appearance.text else LocalContentColor.current
    val editLabel = stringResource(R.string.widget_stack_edit)
    val widgets = uiState.popups[stack.key].orEmpty().mapNotNull { it.widget?.takeIf(HomeLayout::hasWidget) }
    val semantics = Modifier.testTag("widget_stack:${stack.id}").semantics {
        customActions = listOf(CustomAccessibilityAction(editLabel) { if (inputEnabled) onEdit(); inputEnabled })
    }
    if (widgets.isEmpty()) {
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
    val hosted = widgets.map { layout -> key(layout.widgetId) { rememberHostedWidget(layout) } }
    val tallest = widgets.indices.maxOf { index ->
        val widget = hosted[index]
        (widgets[index].widgetHeightDp.takeIf { it > 0 } ?: widget.defaultHeight).coerceIn(widget.minHeight, widget.maxHeight)
    }
    val count = widgets.size
    // Restart on the first widget when widgets are added or removed, keeping page % count meaningful.
    val pager = key(stack.id, count) {
        rememberPagerState(initialPage = widgetStackStartPage(count)) { widgetStackPageCount(count) }
    }
    BoxWithConstraints(modifier.fillMaxWidth().then(semantics)) {
        val indicator = if (count > 1) IndicatorHeight else 0.dp
        val height = if (fillHeight && constraints.hasBoundedHeight)
            (maxHeight - indicator).value.toInt().coerceAtLeast(48) else tallest
        Column(Modifier.fillMaxWidth()) {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(height.dp),
                userScrollEnabled = inputEnabled && count > 1, key = { it }) { page ->
                val index = page % count
                HomeWidget(widgets[index], hosted[index], height, editing = false, enabled = inputEnabled,
                    hapticsEnabled = uiState.settings.allowHapticFeedback, onLongPress = onEdit)
            }
            if (count > 1) Row(Modifier.fillMaxWidth().height(indicator).clearAndSetSemantics {},
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val current = pager.currentPage % count
                repeat(count) { index ->
                    Box(Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape)
                        .background(symbolColor.copy(alpha = if (index == current) 0.9f else 0.35f)))
                }
            }
        }
    }
}
