package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.ShortcutResult
import com.galaxyrio.gracelauncher.data.ShortcutStatus
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppStack
import com.galaxyrio.gracelauncher.ui.overlays.PopupEditorScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WidgetStackUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = LauncherApp(ComponentName("test.app", "Main"), "Some app", null)
    private val widgetStack = LauncherFolder("ws", "Widgets", emptyList(), FolderPlacement.Favorites,
        isAppStack = true, isWidgetStack = true)
    private val appStack = LauncherFolder("as", "Apps", emptyList(), FolderPlacement.Favorites, isAppStack = true)
    private val state = LauncherUiState(apps = listOf(app), folders = listOf(widgetStack, appStack), isLoadingApps = false)

    @Test fun aWidgetStackIsShownAsAWidgetStackAndItsEmptyStateOpensTheEditor() {
        var edits = 0
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                AppStack(widgetStack, state, onLaunch = { _, _ -> }, onDetails = {}, onEdit = { edits++ })
            }
        }
        compose.onNodeWithTag("widget_stack:ws").assertIsDisplayed()
        compose.onNodeWithTag("app_stack:ws").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.widget_stack_edit)).performClick()
        compose.runOnIdle { assertEquals(1, edits) }
    }

    @Test fun anAppStackIsNotShownAsAWidgetStack() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                AppStack(appStack, state, onLaunch = { _, _ -> }, onDetails = {}, onEdit = {})
            }
        }
        compose.onNodeWithTag("app_stack:as").assertIsDisplayed()
        compose.onNodeWithTag("widget_stack:as").assertDoesNotExist()
    }

    @Test fun theWidgetStackEditorOffersWidgetsOnly() {
        showEditor(widgetStack)
        compose.onNodeWithText(context.getString(R.string.widget_stack_edit)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.widget_stack_empty)).assertIsDisplayed()
        compose.onNodeWithTag("popup_add_widget").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.label).assertDoesNotExist()
        compose.onNodeWithTag("popup_editor_add_shortcut").assertDoesNotExist()
    }

    @Test fun theAppStackEditorOffersAppsButNotWidgets() {
        showEditor(appStack)
        compose.onNodeWithText(context.getString(R.string.app_stack_edit)).assertIsDisplayed()
        compose.onNodeWithText(app.label).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("popup_add_widget").assertDoesNotExist()
    }

    private fun showEditor(folder: LauncherFolder) = compose.setContent {
        GraceLauncherTheme(dynamicColor = false) {
            PopupEditorScreen(folder.asApp(), state, LauncherActions(shortcuts = { ShortcutResult(ShortcutStatus.Ready) }), onBack = {})
        }
    }
}
