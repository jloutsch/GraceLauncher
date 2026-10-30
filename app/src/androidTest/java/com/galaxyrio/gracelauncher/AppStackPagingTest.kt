package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppStack
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStackPagingTest {
    @get:Rule val compose = createComposeRule()
    private val stack = LauncherFolder("s", "Stack", emptyList(), FolderPlacement.Favorites, isAppStack = true)
    private fun apps(count: Int) = (1..count).map { LauncherApp(ComponentName("test.app$it", "App$it.Activity"), "App $it", null) }

    private fun show(apps: List<LauncherApp>, onLaunch: (LauncherApp) -> Unit = {}) = compose.setContent {
        GraceLauncherTheme(dynamicColor = false) {
            // 280dp fits five 40dp icons per page.
            Surface(Modifier.width(280.dp)) {
                AppStack(stack, LauncherUiState(), onLaunch = { app, _ -> onLaunch(app) }, onDetails = {}, onEdit = {}, contents = apps)
            }
        }
    }

    @Test fun appsBeyondOneRowAreOnLaterPagesInsteadOfAnExpandArrow() {
        val launched = mutableListOf<String>()
        show(apps(7)) { launched += it.label }
        compose.onNodeWithContentDescription("App 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("App 6").assertDoesNotExist()
        compose.onNodeWithContentDescription("Page 1 of 2").assertExists()
        compose.onNodeWithContentDescription("Expand app stack").assertDoesNotExist()

        compose.onNodeWithTag("app_stack:s").performTouchInput { swipeLeft(startX = right * 0.9f, endX = right * 0.1f) }
        compose.onNodeWithContentDescription("Page 2 of 2").assertExists()
        compose.onNodeWithContentDescription("App 6").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("App 6"), launched) }
    }

    @Test fun oneRowOfAppsHasNoPageDots() {
        show(apps(3))
        compose.onNodeWithContentDescription("App 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 1 of 1").assertDoesNotExist()
    }
}
