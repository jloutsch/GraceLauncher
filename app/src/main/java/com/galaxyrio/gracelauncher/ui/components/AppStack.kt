package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance

/** One inline container for home, drawer, folders and the widget grid. No visible name. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppStack(
    stack: LauncherFolder, uiState: LauncherUiState,
    onLaunch: (LauncherApp, Rect) -> Unit, onDetails: (LauncherApp) -> Unit, onEdit: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, gridManaged: Boolean = false,
    iconSize: Dp = 40.dp, onWallpaper: Boolean = true,
    /** Contents for a stack that is not stored as a folder; null reads the folder's saved items. */
    contents: List<LauncherApp>? = null,
) {
    // Every surface that places a stack (home, drawer, folders, Widget Screen) shows widget stacks here too.
    if (stack.isWidgetStack) {
        WidgetStack(stack, uiState, onEdit, modifier, enabled = enabled, onWallpaper = onWallpaper, fillHeight = gridManaged)
        return
    }
    val inputEnabled = enabled && LocalLauncherInputEnabled.current
    val appearance = LocalLauncherAppearance.current
    val displayedIconSize = iconSize * (appearance.iconSize / 100f)
    val symbolColor = if (onWallpaper) appearance.text else LocalContentColor.current
    val apps = contents ?: uiState.popupItems(uiState.folderItem(stack), emptyList()).mapNotNull { entry ->
        if (entry.widget != null) null else uiState.findItem(entry.key)?.takeIf { it.folderId == null }
    }
    val editLabel = stringResource(R.string.app_stack_edit)
    BoxWithConstraints(modifier.fillMaxWidth().testTag("app_stack:${stack.id}")) {
        val columns = appStackColumns(maxWidth.value, displayedIconSize.value)
        val inset = AppStackInset.dp
        // Anchor the first icon's LEFT edge, rather than centering it in a wide cell.
        val step = (maxWidth - inset * 2 - displayedIconSize) / (columns - 1)
        val pages = apps.chunked(columns)
        val count = pages.size
        // Same wrap-around paging as widget stacks, so a swipe never falls through to the home pager.
        val pager = key(stack.id, count) { rememberPagerState(initialPage = widgetStackStartPage(count)) { widgetStackPageCount(count) } }
        val position = stringResource(R.string.app_stack_position, if (count == 0) 0 else pager.currentPage % count + 1, count)
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(appStackHeight(displayedIconSize.value).dp)
                .clip(RoundedCornerShape(displayedIconSize / 2 + inset))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (onWallpaper) 0.25f else 1f))
                .then(if (gridManaged) Modifier else Modifier.combinedClickable(enabled = inputEnabled,
                    onClick = {}, onLongClick = onEdit))
                .semantics {
                    contentDescription = stack.name
                    customActions = listOf(CustomAccessibilityAction(editLabel) { if (inputEnabled) onEdit(); inputEnabled })
                }) {
                if (count > 0) HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = inputEnabled && count > 1,
                    key = { it }) { page ->
                    Box(Modifier.fillMaxSize()) {
                        pages[page % count].forEachIndexed { index, app -> key(app.key) {
                            StackIcon(app, iconSize, displayedIconSize, inputEnabled,
                                Modifier.offset(x = inset + step * index, y = inset),
                                onClick = { bounds -> onLaunch(app, bounds) },
                                onLongClick = if (gridManaged) null else ({ onDetails(app) }))
                        } }
                    }
                }
                if (apps.isEmpty()) IconButton(onClick = onEdit, enabled = inputEnabled,
                    modifier = Modifier.align(Alignment.Center).semantics { contentDescription = editLabel }) {
                    LauncherIcon(LauncherSymbol.Plus, tint = symbolColor)
                }
            }
            if (count > 1) StackPageDots(count, pager.currentPage % count, position, symbolColor)
        }
    }
}

@Composable
private fun StackIcon(app: LauncherApp, iconSize: Dp, displayedIconSize: Dp, enabled: Boolean, modifier: Modifier,
    onClick: (Rect) -> Unit, onLongClick: (() -> Unit)?) {
    val appearance = LocalLauncherAppearance.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val interactions = remember { MutableInteractionSource() }
    val rippleMask = app.icon.takeUnless { appearance.themedIcons && app.monochromeIcon != null }
    Box(modifier.size(displayedIconSize)
        .combinedClickable(enabled = enabled, interactionSource = interactions, indication = null,
            onClick = { onClick(bounds) }, onLongClick = onLongClick)
        .semantics { contentDescription = app.label },
        contentAlignment = Alignment.Center) {
        AppIcon(app, Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }, size = iconSize)
        // Mask only the feedback, not the icon or its profile badges. The rendered
        // bitmap preserves custom shapes and icon-pack silhouettes as well.
        Box(Modifier.matchParentSize()
            .then(if (rippleMask == null) Modifier.clip(CircleShape) else Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawImage(rippleMask, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        blendMode = BlendMode.DstIn)
                })
            .indication(interactions, ripple()))
    }
}
