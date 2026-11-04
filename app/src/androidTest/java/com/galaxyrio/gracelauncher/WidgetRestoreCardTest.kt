package com.galaxyrio.gracelauncher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Imported widgets wait for a tap on home instead of opening setup screens by themselves. */
class WidgetRestoreCardTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun showHome(pending: Int, onRestore: () -> Unit = {}) = compose.setContent {
        GraceLauncherTheme(dynamicColor = false) {
            HomeScreen(LauncherUiState(isLoadingApps = false, pendingWidgetRestores = pending), topSpace = 128.dp,
                onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onAppDetails = {}, onAppShortcuts = { _, _ -> },
                actions = LauncherActions(restoreWidgets = onRestore))
        }
    }

    @Test fun homeShowsHowManyWidgetsWaitAndEachTapSetsUpOne() {
        var taps = 0
        showHome(pending = 3) { taps++ }
        compose.onNodeWithTag("widget_restore_card").assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.widget_restore_count, 3, 3)).assertIsDisplayed()
        compose.onNodeWithTag("widget_restore_next").performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun homeHasNoCardWhenNothingWaits() {
        showHome(pending = 0)
        compose.onNodeWithTag("home_content").assertIsDisplayed()
        compose.onNodeWithTag("widget_restore_card").assertDoesNotExist()
    }
}
