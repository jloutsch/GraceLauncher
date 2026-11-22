package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Fork: settings search (#36). The setting row a search result named, for the page that result opened.
 * The first row on the page with this exact title claims it, scrolls into view and flashes once.
 */
@Stable
internal class SettingsSearchTarget(val title: String?) {
    /** Plain field: set when the row is first placed, read by [SettingsSearchScroll] after a frame. */
    var claimed = false
        internal set
    val pending: Boolean get() = title != null && !claimed
}

internal val LocalSettingsSearchTarget = staticCompositionLocalOf { SettingsSearchTarget(null) }

internal const val SettingsSearchTargetKey = "settings_search_target"

/** [amount] runs from 0 to 1 and back while the row flashes; [modifier] lets the row bring itself into view. */
internal class SearchHighlight(val modifier: Modifier, val amount: Float) {
    fun color(base: Color, highlight: Color): Color = if (amount == 0f) base else lerp(base, highlight, amount)

    companion object {
        val None = SearchHighlight(Modifier, 0f)
    }
}

@Composable
internal fun rememberSearchHighlight(title: String): SearchHighlight {
    val target = LocalSettingsSearchTarget.current
    if (target.title != title) return SearchHighlight.None
    val requester = remember { BringIntoViewRequester() }
    val amount = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Claim on placement, not composition: a lazy list composes the next rows ahead of time without placing them,
    // and an unplaced row can't scroll itself into view.
    var claimed by remember(target) { mutableStateOf(false) }
    LaunchedEffect(claimed) {
        if (!claimed) return@LaunchedEffect
        withFrameNanos { }
        // Leave a row's height of space above and below, so the row isn't pinned to an edge.
        val height = size.height.toFloat()
        requester.bringIntoView(Rect(0f, -height, size.width.toFloat(), height * 2))
        repeat(2) {
            amount.animateTo(1f, tween(250))
            amount.animateTo(0f, tween(450))
        }
    }
    return SearchHighlight(Modifier.bringIntoViewRequester(requester).onSizeChanged { size = it }.onPlaced {
        if (!claimed && !target.claimed) { target.claimed = true; claimed = true }
    }, amount.value)
}

/** The row colour while it flashes. */
@Composable
internal fun SearchHighlight.containerColor(base: Color): Color = color(base, MaterialTheme.colorScheme.primaryContainer)

/**
 * A lazy list only composes the rows on screen, so a target further down can't claim itself. Page through the
 * list until it does, or until the end; the row then scrolls itself into place.
 */
@Composable
internal fun SettingsSearchScroll(state: LazyListState) {
    val target = LocalSettingsSearchTarget.current
    LaunchedEffect(target, state) {
        if (!target.pending) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        var steps = 0
        while (target.pending && steps++ < 200) {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: break
            if (last >= info.totalItemsCount - 1) break
            // A row taller than the screen is both first and last; step past it.
            state.scrollToItem(if (last > info.visibleItemsInfo.first().index) last else last + 1)
            repeat(2) { withFrameNanos { } }
        }
    }
}
