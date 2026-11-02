package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test

class AppListLayoutTest {
    private val all = AppListBlock.AllApps.key
    private val folders = AppListBlock.BottomFolders.key
    private val grace = AppListBlock.GraceLauncher.key
    private val pinned = "category:$PinnedCategoryId"
    private val notification = "category:$NotificationCategoryId"

    @Test fun defaultBlocksIncludeEmptyCategoriesAndMandatorySections() {
        assertEquals(listOf(pinned, all, folders, grace), AppListLayout().orderedKeys(AppCategory.defaults()))
        assertEquals(listOf(all, folders, grace), AppListLayout().orderedKeys(emptyList()))
        assertEquals(listOf(pinned, all, folders),
            AppListLayout(showGraceLauncher = false).orderedKeys(AppCategory.defaults()))
        assertEquals(listOf(pinned, all, folders),
            AppListLayout(order = emptyList(), showGraceLauncher = false).orderedKeys(AppCategory.defaults()))
    }

    @Test fun manualOrderDropsUnavailableBlocksAndAppendsNewCategoriesOnce() {
        val extra = AppCategory("extra", placement = CategoryPlacement.AppList)
        val layout = AppListLayout(order = listOf(grace, "category:deleted", notification, all, notification,
            "category:$FavoritesCategoryId", pinned, folders))
        assertEquals(listOf(grace, all, pinned, folders, extra.key),
            layout.orderedKeys(AppCategory.defaults() + extra))
    }

    @Test fun placementMovesRemoveOldSurfaceSlotsAndAppendNewSlotsWithoutTouchingWidgetsOrFolders() {
        val original = LauncherSettings(appCategories = AppCategory.defaults().map {
            if (it.id == NotificationCategoryId) it.copy(placement = CategoryPlacement.AppList) else it
        },
            homeLayout = HomeLayout(homeOrder = listOf("widget:1", "category:favorites", "folder:x", "category:music")),
            listAppearance = ListAppearance(appList = AppListLayout(order = listOf(notification, all, pinned, folders, grace))))
        val moved = synchronizeCategoryPlacement(original, original.copy(appCategories = original.appCategories!!.map {
            when (it.id) {
                FavoritesCategoryId -> it.copy(placement = CategoryPlacement.AppList)
                NotificationCategoryId -> it.copy(placement = CategoryPlacement.Home)
                else -> it
            }
        }))
        assertEquals(listOf("widget:1", "folder:x", "category:music", notification), moved.homeLayout.homeOrder)
        assertEquals(listOf(all, pinned, folders, grace, "category:favorites"), moved.listAppearance.appList.order)
        assertEquals(CategoryIcon.Circle, moved.appCategories!!.first { it.id == FavoritesCategoryId }.icon)
        assertEquals(moved, synchronizeCategoryPlacement(moved, moved))
    }

    @Test fun deletingCustomCategoriesCleansBothOrdersAndBuiltInsKeepTheirExistingValues() {
        val custom = AppCategory("custom", placement = CategoryPlacement.AppList)
        val categories = AppCategory.defaults().map {
            if (it.id == MusicCategoryId) it.copy(name = "Audio", condition = CategoryCondition.Always,
                itemKeys = listOf("app:a"), icon = CategoryIcon.Bedtime, placement = CategoryPlacement.None) else it
        } + custom
        val original = LauncherSettings(appCategories = categories,
            homeLayout = HomeLayout(homeOrder = listOf("widget:1", custom.key, "folder:x", "category:music")),
            listAppearance = ListAppearance(appList = AppListLayout(order = listOf(custom.key, all, pinned, notification, folders, grace))))
        val changed = synchronizeCategoryPlacement(original, original.copy(appCategories = categories.filterNot {
            it.id == MusicCategoryId || it.id == custom.id
        }))
        assertEquals(categories.filterNot { it.id == custom.id }, changed.appCategories)
        assertEquals(listOf("widget:1", "folder:x"), changed.homeLayout.homeOrder)
        assertEquals(listOf(all, pinned, folders, grace), changed.listAppearance.appList.order)
    }

    @Test fun reassigningHiddenCategoryAppendsOnceAndFreshInitializationKeepsDefaultAppListOrder() {
        val hidden = LauncherSettings(appCategories = AppCategory.defaults().map {
            if (it.id == MusicCategoryId) it.copy(placement = CategoryPlacement.None) else it
        }, homeLayout = HomeLayout(homeOrder = listOf("category:favorites", "widget:1")))
        val shown = synchronizeCategoryPlacement(hidden, hidden.copy(appCategories = hidden.appCategories!!.map {
            if (it.id == MusicCategoryId) it.copy(placement = CategoryPlacement.Home) else it
        }))
        assertEquals(listOf("category:favorites", "widget:1", "category:music"), shown.homeLayout.homeOrder)
        val fresh = synchronizeCategoryPlacement(LauncherSettings(), LauncherSettings(appCategories = AppCategory.defaults()))
        assertEquals(listOf(pinned, all, folders, grace), fresh.listAppearance.appList.order)
    }

    @Test fun enablingRecentAppsRestoresGraceWithoutChangingItsSettingsToggleOrDisablingItOnRemoval() {
        val original = LauncherSettings(listAppearance = ListAppearance(showRecentlyInstalled = false,
            appList = AppListLayout(showGraceLauncher = false, showGraceSettings = false)))
        val enabled = synchronizeCategoryPlacement(original, original.copy(listAppearance = original.listAppearance.copy(showRecentlyInstalled = true)))
        assertTrue(enabled.listAppearance.appList.showGraceLauncher)
        assertFalse(enabled.listAppearance.appList.showGraceSettings)
        assertNull(enabled.appCategories)
        val removed = synchronizeCategoryPlacement(enabled, enabled.copy(listAppearance = enabled.listAppearance.copy(showRecentlyInstalled = false)))
        assertTrue(removed.listAppearance.appList.showGraceLauncher)
    }
}
