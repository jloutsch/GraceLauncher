package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.HomepageEditorScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HomepageCategoriesTest {
    @get:Rule val compose = createComposeRule()
    private val app = LauncherApp(ComponentName("test.app", "Activity"), "App", null)
    private val folder = LauncherFolder("folder", "Folder", listOf(app.key), FolderPlacement.Favorites)
    private val stack = LauncherFolder("stack", "Stack", listOf(app.key), FolderPlacement.Favorites, isAppStack = true)
    private var state by mutableStateOf(LauncherUiState())

    private fun show() {
        state = LauncherUiState(apps = listOf(app), isLoadingApps = false, folders = listOf(folder, stack),
            settings = LauncherSettings(calendarAgenda = false, mediaPlayer = false,
                homeLayout = HomeLayout(baselineEnabled = false),
                appCategories = AppCategory.defaults(listOf(app.key)).map { if (it.id == MusicCategoryId) it.copy(placement = com.galaxyrio.gracelauncher.data.CategoryPlacement.None) else it }))
        val actions = LauncherActions(updateSettings = { state = state.copy(settings = it(state.settings)) },
            updateAppCategory = { id, change -> state = state.copy(settings = state.settings.copy(appCategories = state.appCategories.map {
                if (it.id == id) change(it) else it
            })) })
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                HomepageEditorScreen(state, actions, onToggle = {}, onReorder = { keys ->
                    state = state.copy(settings = state.settings.copy(homeLayout = state.settings.homeLayout.copy(homeOrder = keys)))
                }, onDone = {})
            }
        }
    }

    @Test fun homepageHasGroupsInsteadOfDirectAppsAndOnlyCheckboxesRemoveItems() {
        show()
        compose.onNodeWithTag("favorites_all_apps").assertDoesNotExist()
        compose.onNodeWithTag("favorites_search").assertDoesNotExist()
        compose.onNodeWithTag("favorites_add_shortcut").assertDoesNotExist()
        compose.onNodeWithTag("favorite:${app.key}").assertDoesNotExist()
        compose.onNodeWithTag("favorite:grace:clock-calendar").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(state.settings.homeLayout.clockCalendarEnabled) }
        compose.onNodeWithTag("favorite:grace:clock-calendar").onChildren().filter(isToggleable()).onFirst().performClick()
        compose.runOnIdle {
            assertFalse(state.settings.homeLayout.clockCalendarEnabled)
            assertTrue("Hiding the widget preserves its clock setting", state.settings.clockEnabled)
        }
        compose.onNodeWithTag("favorite:category:favorites").performScrollTo()
            .onChildren().filter(isToggleable()).onFirst().performClick()
        compose.runOnIdle {
            assertFalse(state.appCategories.first().showOnHome)
            assertEquals(listOf(app.key), state.appCategories.first().itemKeys)
        }
        listOf("favorites_grace_widgets", "popup_add_widget", "favorites_add_category", "favorites_add_folder", "favorites_add_stack").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsEnabled()
        }
    }

    @Test fun categoryRowAndEditButtonBothOpenTheEditorWithoutUnselectingIt() {
        show()
        listOf("favorite:category:favorites", "favorite_edit:category:favorites").forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.onNodeWithTag("category_editor_screen").assertIsDisplayed()
            compose.onNodeWithTag("favorite:${app.key}").assertExists()
            compose.runOnIdle { assertTrue(state.appCategories.first().showOnHome) }
            compose.onNodeWithTag("category_editor_done").performClick()
        }
    }

    @Test fun foldersAndStacksOpenTheirExistingEditorsFromBothTargets() {
        show()
        listOf(folder, stack).forEach { group ->
            listOf("favorite:${group.key}", "favorite_edit:${group.key}").forEach { tag ->
                compose.onNodeWithTag(tag).performScrollTo().performClick()
                compose.onNodeWithTag("popup_editor_screen").assertIsDisplayed()
                compose.onNodeWithTag("folder_name").assertExists()
                compose.runOnIdle { assertTrue(state.folders.first { it.id == group.id }.placement.inFavorites) }
                compose.onNodeWithTag("popup_editor_done").performClick()
            }
        }
    }
}
