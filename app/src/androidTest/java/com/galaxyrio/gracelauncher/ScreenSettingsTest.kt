package com.galaxyrio.gracelauncher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreenSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun labGroupsIndependentScreenControlsInOnePage() {
        var state by mutableStateOf(LauncherUiState())
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(updateSettings = { update ->
                    state = state.copy(settings = update(state.settings))
                }), onBack = {}, initialPage = "Advanced")
            }
        }
        compose.onNodeWithTag("allow_screen_rotation").assertDoesNotExist()
        compose.onNodeWithTag("lab_open_screens").performClick()
        compose.onNodeWithTag("settings_screens").assertIsDisplayed()
        compose.onNodeWithTag("allow_screen_rotation").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("remember_screen_position").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle {
            assertFalse(state.settings.allowScreenRotation)
            assertTrue(state.settings.rememberScreenPosition)
        }
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("lab_open_screens").performClick()
        compose.onNodeWithTag("allow_screen_rotation").assertIsOff()
        compose.onNodeWithTag("remember_screen_position").assertIsOn()
    }
}
