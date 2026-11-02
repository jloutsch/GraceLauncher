package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LocalListAppearance
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HomeCategoryIndicatorTest {
    @get:Rule val compose = createComposeRule()
    private val shared = LauncherApp(ComponentName("fixture.shared", "Main"), "Shared", null)
    private val favorite = LauncherApp(ComponentName("fixture.favorite", "Main"), "Favorite", null)
    private val member = LauncherApp(ComponentName("fixture.member", "Main"), "Member", null)
    private val folder = LauncherFolder("category-folder", "Category folder", listOf(member.key), FolderPlacement.None)
    private val directFolder = LauncherFolder("direct-folder", "Direct folder", listOf(member.key), FolderPlacement.Favorites)
    private val stack = LauncherFolder("category-stack", "Category stack", listOf(member.key), FolderPlacement.None, isAppStack = true)
    private val first = AppCategory("first", itemKeys = listOf(shared.key, folder.key, stack.key),
        placement = CategoryPlacement.Home, icon = CategoryIcon.MusicNote)
    private val second = AppCategory("second", itemKeys = listOf(shared.key),
        placement = CategoryPlacement.Home, icon = CategoryIcon.Keep)
    private val favorites = AppCategory(FavoritesCategoryId, itemKeys = listOf(favorite.key), placement = CategoryPlacement.Home)
    private var state by mutableStateOf(LauncherUiState())
    private var density = 1f
    private val isCategoryIndicator = SemanticsMatcher("category indicator") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("category_indicator:") == true
    }

    private fun show() {
        state = LauncherUiState(apps = listOf(shared, favorite, member), isLoadingApps = false,
            folders = listOf(folder, directFolder, stack), settings = LauncherSettings(
                clockEnabled = false, calendarAgenda = false, weatherEnabled = false, mediaPlayer = false,
                appCategories = listOf(first, second, favorites),
                homeLayout = HomeLayout(clockCalendarEnabled = false, baselineEnabled = false,
                    homeOrder = listOf(first.key, second.key, favorites.key, directFolder.key))))
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                val currentDensity = LocalDensity.current.density
                SideEffect { density = currentDensity }
                CompositionLocalProvider(LocalListAppearance provides state.settings.listAppearance) {
                    HomeScreen(state, topSpace = 0.dp, onLaunchApp = {}, onAppDetails = {},
                        onAppShortcuts = { _, _ -> }, onDateClick = {}, onClockClick = {})
                }
            }
        }
    }

    private fun indicator(icon: CategoryIcon, row: String) = compose.onNode(
        hasTestTag("category_indicator:${icon.name}") and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true)

    @Test fun appAndFolderLabelsUseTheFirstVisibleCategoryAndDirectItemsHaveNoBadge() {
        show()
        indicator(CategoryIcon.MusicNote, "app:${shared.key}").assertIsDisplayed()
        indicator(CategoryIcon.MusicNote, "folder:${folder.id}").assertIsDisplayed()
        listOf("app:${favorite.key}", "folder:${directFolder.id}", "app_stack:${stack.id}").forEach { row ->
            compose.onAllNodes(isCategoryIndicator and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true)
                .assertCountEquals(0)
        }
        compose.runOnIdle {
            state = state.copy(settings = state.settings.copy(homeLayout = state.settings.homeLayout.copy(
                homeOrder = listOf(second.key, first.key, favorites.key, directFolder.key))))
        }
        indicator(CategoryIcon.Keep, "app:${shared.key}").assertIsDisplayed()
        indicator(CategoryIcon.MusicNote, "app:${shared.key}").assertDoesNotExist()
        indicator(CategoryIcon.MusicNote, "folder:${folder.id}").assertIsDisplayed()
    }

    @Test fun badgeIsSmallBesideTheLabelAndDisappearsWithHiddenNames() {
        show()
        val badge = indicator(CategoryIcon.MusicNote, "app:${shared.key}")
        badge.assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
        val label = compose.onNodeWithTag("app:${shared.key}:label", useUnmergedTree = true)
            .assertTextEquals(shared.label).fetchSemanticsNode().boundsInRoot
        val icon = badge.fetchSemanticsNode()
        assertEquals(4f * density, icon.boundsInRoot.left - label.right, 1f)
        assertFalse(icon.config.contains(SemanticsProperties.ContentDescription))

        compose.runOnIdle {
            state = state.copy(settings = state.settings.copy(listAppearance = state.settings.listAppearance.copy(
                names = AppNameVisibility.Neither)))
        }
        compose.onAllNodes(isCategoryIndicator, useUnmergedTree = true).assertCountEquals(0)
    }
}
