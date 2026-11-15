@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState

/** Keep the native modal window and extend only its content when the agenda needs more room. */
@Composable
internal fun AgendaPanel(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onRequestCalendar: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val sheetColor = MaterialTheme.colorScheme.surfaceContainer
    val layout = launcherSheetLayout(uiState.settings.hideStatusBar)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        modifier = Modifier.testTag("launcher_sheet"),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = sheetColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = null,
        contentWindowInsets = { layout.contentInsets.union(WindowInsets.ime) },
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = false,
            isAppearanceLightNavigationBars = sheetColor.luminance() > 0.5f,
        ),
    ) {
        CompositionLocalProvider(LocalLauncherSheetLayout provides layout) {
            LauncherSheetSystemBars(uiState.settings.hideStatusBar)
            val density = LocalDensity.current
            val maximumHeight = (layout.windowHeight - layout.topInset).coerceAtLeast(0.dp)
            // Preserve the opening sheet height while moving its bottom inset into the list's scroll area.
            val openingHeight = (layout.windowHeight * 0.66f + layout.bottomInset).coerceAtMost(maximumHeight)
            val maximumGrowth = with(density) { (maximumHeight - openingHeight).toPx() }
            val growth = remember { mutableFloatStateOf(0f) }
            val extendOnScroll = remember(sheetState, listState, maximumGrowth) {
                object : NestedScrollConnection {
                    fun canExtend() = listState.canScrollForward &&
                        sheetState.currentValue == SheetValue.Expanded &&
                        sheetState.targetValue == SheetValue.Expanded && growth.floatValue < maximumGrowth

                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput || available.y >= 0f || !canExtend()) return Offset.Zero
                        val previous = growth.floatValue.coerceIn(0f, maximumGrowth)
                        growth.floatValue = (previous - available.y).coerceAtMost(maximumGrowth)
                        return Offset(0f, previous - growth.floatValue)
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (available.y >= 0f || !canExtend()) return Velocity.Zero
                        animate(
                            initialValue = growth.floatValue,
                            targetValue = maximumGrowth,
                            animationSpec = tween(220),
                        ) { value, _ -> growth.floatValue = value }
                        return Velocity(0f, available.y)
                    }
                }
            }
            val contentHeight = openingHeight + with(density) { growth.floatValue.coerceIn(0f, maximumGrowth).toDp() }
            // The modal stays bottom-anchored as its content grows; its own drag and dismissal remain intact.
            Box(Modifier.fillMaxWidth().heightIn(max = contentHeight).nestedScroll(extendOnScroll)) {
                AgendaSheet(uiState, actions, onRequestCalendar, listState)
            }
        }
    }
}
