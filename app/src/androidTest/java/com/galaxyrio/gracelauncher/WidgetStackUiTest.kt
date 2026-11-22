package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.galaxyrio.gracelauncher.ui.widgets.LocalWidgetHost
import com.galaxyrio.gracelauncher.ui.widgets.LocalWidgetSurfaceShown
import com.galaxyrio.gracelauncher.ui.widgets.rememberWidgetHost
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.data.WidgetScreenKey
import com.galaxyrio.gracelauncher.ui.home.WidgetScreen
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.data.ShortcutResult
import com.galaxyrio.gracelauncher.data.ShortcutStatus
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppStack
import com.galaxyrio.gracelauncher.ui.overlays.PopupEditorScreen
import com.galaxyrio.gracelauncher.ui.settings.FolderEditorSettings
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    @Test fun theWidgetStackEditorOffersWidgetsAppsAndShortcuts() {
        showEditor(widgetStack)
        compose.onNodeWithText(context.getString(R.string.widget_stack_edit)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.widget_stack_empty)).assertIsDisplayed()
        compose.onNodeWithTag("popup_add_widget").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("popup_editor_add_shortcut").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.label).performScrollTo().assertIsDisplayed()
    }

    // Fork: apps in a widget stack get a page of their own, in the editor's order.
    @Test fun appsInAWidgetStackGetTheirOwnPageAfterTheWidgetsAndLaunch() {
        val items = listOf(PopupItem("widget:7", HomeLayout(widgetId = 7, widgetProvider = "w/w.Provider")), PopupItem(app.key))
        val launched = mutableListOf<String>()
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                AppStack(widgetStack, state.copy(popups = mapOf(widgetStack.key to items)),
                    onLaunch = { item, _ -> launched += item.key }, onDetails = {}, onEdit = {})
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.app_stack_position, 1, 2)).assertExists()
        compose.onNodeWithContentDescription(app.label).assertDoesNotExist()
        compose.onNodeWithTag("widget_stack:ws").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription(context.getString(R.string.app_stack_position, 2, 2)).assertExists()
        // The wrapping pager can keep extra copies of the app page composed while it settles; tap one inside the stack.
        val stack = compose.onNodeWithTag("widget_stack:ws").fetchSemanticsNode().boundsInRoot
        val icons = compose.onAllNodesWithContentDescription(app.label)
        (0 until icons.fetchSemanticsNodes().size).map { icons[it] }
            .first { node -> node.fetchSemanticsNode().boundsInRoot.let { it.left >= stack.left && it.right <= stack.right } }
            .performClick()
        compose.runOnIdle { assertEquals(listOf(app.key), launched) }
    }

    @Test fun theAppStackEditorOffersAppsButNotWidgets() {
        showEditor(appStack)
        compose.onNodeWithText(context.getString(R.string.app_stack_edit)).assertIsDisplayed()
        compose.onNodeWithText(app.label).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("popup_add_widget").assertDoesNotExist()
    }

    @Test fun stackHeightButtonsStepOneDpAndAutomaticIsOneStepBelowTheMinimum() {
        var items = listOf(PopupItem("widget:7", HomeLayout(widgetId = 7, widgetHeightDp = 150)))
        var stackState by mutableStateOf(state.copy(popups = mapOf(widgetStack.key to items)))
        val actions = LauncherActions(shortcuts = { ShortcutResult(ShortcutStatus.Ready) },
            updatePopup = { _, _, transform -> items = transform(items); stackState = stackState.copy(popups = mapOf(widgetStack.key to items)) })
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) { FolderEditorSettings(widgetStack.id, stackState, actions, onBack = {}) }
        }
        fun height() = compose.runOnIdle { items.single().widget!!.widgetHeightDp }
        compose.onNodeWithTag("widget_stack_height_increase").performScrollTo().performClick()
        assertEquals(151, height())
        compose.onNodeWithTag("widget_stack_height_decrease").performClick()
        compose.onNodeWithTag("widget_stack_height_decrease").performClick()
        assertEquals(149, height())

        compose.runOnIdle { items = listOf(items.single().let { it.copy(widget = it.widget!!.copy(widgetHeightDp = 48)) })
            stackState = stackState.copy(popups = mapOf(widgetStack.key to items)) }
        compose.onNodeWithTag("widget_stack_height_decrease").performClick()
        assertEquals("One step below the minimum is Automatic", 0, height())
        compose.onNodeWithTag("widget_stack_height_decrease").assertIsNotEnabled()
        compose.onNodeWithTag("widget_stack_height_increase").performClick()
        assertEquals(48, height())
    }

    // Fork (#39): a stack shown in two places hosts each widget in one copy only, never in a hidden one.
    @Test fun twoCopiesOfAStackHostItsWidgetOnceAndNeverInAHiddenCopy() {
        val items = listOf(PopupItem("widget:7", HomeLayout(widgetId = 7, widgetProvider = "w/w.Provider")))
        val stacked = state.copy(popups = mapOf(widgetStack.key to items))
        var secondShown by mutableStateOf(true)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalWidgetHost provides rememberWidgetHost()) {
                    Column {
                        Box(Modifier.testTag("first")) { AppStack(widgetStack, stacked, onLaunch = { _, _ -> }, onDetails = {}, onEdit = {}) }
                        CompositionLocalProvider(LocalWidgetSurfaceShown provides secondShown) {
                            Box(Modifier.testTag("second")) { AppStack(widgetStack, stacked, onLaunch = { _, _ -> }, onDetails = {}, onEdit = {}) }
                        }
                    }
                }
            }
        }
        // An unbound widget ID shows the "widget unavailable" card, so the card marks the hosting copy.
        val card = hasText(context.getString(R.string.widget_unavailable))
        compose.waitUntil(5_000) { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(card).assertCountEquals(1)
        compose.runOnIdle { secondShown = false }
        compose.waitUntil(5_000) { compose.onAllNodes(card and hasAnyAncestor(hasTestTag("first"))).fetchSemanticsNodes().size == 1 }
        compose.onAllNodes(card).assertCountEquals(1)
        compose.onAllNodes(card and hasAnyAncestor(hasTestTag("second"))).assertCountEquals(0)
    }

    // Fork: on the Widget Screen a long-press moves and resizes, so a selected stack offers its editor.
    @Test fun selectingAWidgetStackOnTheWidgetScreenOffersItsEditor() {
        val state = state.copy(popups = mapOf(WidgetScreenKey to listOf(PopupItem(widgetStack.key)),
            widgetStack.key to listOf(PopupItem(app.key))))
        val edited = mutableListOf<String>()
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                WidgetScreen(state, LauncherActions(), 0.dp, 0.dp, editing = false, enabled = true,
                    onEditingChange = {}, onEdit = {}, onLaunchApp = {}, onAppDetails = {}, onOpenFolder = { _, _ -> },
                    onEditStack = { edited += it.id })
            }
        }
        compose.onNodeWithTag("widget_stack_edit_button").assertDoesNotExist()
        compose.onNodeWithTag("widget_screen_item:${widgetStack.key}").performTouchInput { longClick(durationMillis = 1600L) }
        compose.onNodeWithTag("widget_stack_edit_button").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(widgetStack.id), edited) }
        compose.onNodeWithTag("widget_stack_edit_button").assertDoesNotExist()
    }

    private fun showEditor(folder: LauncherFolder) = compose.setContent {
        GraceLauncherTheme(dynamicColor = false) {
            PopupEditorScreen(folder.asApp(), state, LauncherActions(shortcuts = { ShortcutResult(ShortcutStatus.Ready) }), onBack = {})
        }
    }
}
