package com.galaxyrio.gracelauncher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fork: settings search (#36) on the main Settings screen. */
@RunWith(AndroidJUnit4::class)
class SettingsSearchUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var closes = 0
    private lateinit var dispatcher: OnBackPressedDispatcher

    // Back goes through the app's dispatcher: a test can press back before the keyboard finishes closing.
    private fun back() = compose.runOnIdle { dispatcher.onBackPressed() }

    private fun show() = compose.setContent {
        val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
        SideEffect { dispatcher = owner.onBackPressedDispatcher }
        GraceLauncherTheme(dynamicColor = false) {
            LauncherSettingsScreen(LauncherUiState(isDefaultHome = true), LauncherActions(), onBack = { closes++ })
        }
    }

    @Test fun aResultShowsItsPathAndOpensThePageThatHoldsIt() {
        show()
        compose.onNodeWithTag("settings_category_advanced").assertIsDisplayed()
        compose.onNodeWithTag("settings_search_query").performTextReplacement("screen rotation")
        compose.onNodeWithTag("settings_category_advanced").assertDoesNotExist()
        compose.onNodeWithTag("settings_search_result:Advanced")
            .assertTextContains(context.getString(R.string.settings_allow_screen_rotation))
            .assertTextContains(context.getString(R.string.settings_advanced))
            .performClick()
        compose.onNodeWithText(context.getString(R.string.settings_allow_screen_rotation)).assertIsDisplayed()
        compose.onNodeWithTag("settings_search_query").assertDoesNotExist()
    }

    @Test fun backFromAResultWalksUpThePathItShowed() {
        show()
        compose.onNodeWithTag("settings_search_query").performTextReplacement(context.getString(R.string.tasks_choose_lists))
        compose.onNodeWithTag("settings_search_result:TaskLists").performClick()
        back()
        compose.onNodeWithTag("tasks_enabled").assertIsDisplayed()
    }

    @Test fun aClockStyleResultOpensWithItsGroupsExpanded() {
        show()
        compose.onNodeWithTag("settings_search_query").performTextReplacement(context.getString(R.string.clock_show_seconds))
        compose.onNodeWithTag("settings_search_result:ClockStyle").performClick()
        compose.onNodeWithTag("clock_style_controls").performScrollToNode(hasTestTag("clock_show_seconds"))
        compose.onNodeWithTag("clock_show_seconds").assertIsDisplayed()
    }

    @Test fun backClearsATypedSearchBeforeClosingSettings() {
        show()
        compose.onNodeWithTag("settings_search_query").performTextReplacement("rotation")
        compose.onNodeWithTag("settings_category_advanced").assertDoesNotExist()
        back()
        compose.onNodeWithTag("settings_category_advanced").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, closes) }
    }

    @Test fun noMatchSaysSoAndClearingTheQueryBringsBackTheCategories() {
        show()
        compose.onNodeWithTag("settings_search_query").performTextReplacement("zzqx")
        compose.onNodeWithText(context.getString(R.string.settings_search_none)).assertIsDisplayed()
        compose.onNodeWithTag("settings_search_query").performTextReplacement("")
        compose.onNodeWithTag("settings_category_advanced").assertIsDisplayed()
    }
}
