package com.galaxyrio.gracelauncher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fork: settings search (#36) on the main Settings screen. */
@RunWith(AndroidJUnit4::class)
class SettingsSearchUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show() = compose.setContent {
        GraceLauncherTheme(dynamicColor = false) {
            LauncherSettingsScreen(LauncherUiState(isDefaultHome = true), LauncherActions(), onBack = {})
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

    @Test fun noMatchSaysSoAndClearingTheQueryBringsBackTheCategories() {
        show()
        compose.onNodeWithTag("settings_search_query").performTextReplacement("zzqx")
        compose.onNodeWithText(context.getString(R.string.settings_search_none)).assertIsDisplayed()
        compose.onNodeWithTag("settings_search_query").performTextReplacement("")
        compose.onNodeWithTag("settings_category_advanced").assertIsDisplayed()
    }
}
