package com.galaxyrio.gracelauncher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCategoryPersistenceTest {
    @Test fun appListLayoutRoundTripsInsideExistingAppearanceJson() {
        val appearance = ListAppearance(showRecentlyInstalled = false, appList = AppListLayout(
            order = listOf(AppListBlock.GraceLauncher.key, "category:pinned", AppListBlock.AllApps.key, AppListBlock.BottomFolders.key),
            showGraceLauncher = false, showGraceSettings = false))
        assertEquals(appearance, ListAppearance.decode(appearance.encode()))
        assertEquals(AppListLayout(), ListAppearance.decode("{}").appList)
    }

    @Test fun bothRepositoryMutatorsPreserveProtectedCategoriesAndSynchronizePlacement() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LauncherDatabase::class.java).build()
        try {
            val repository = LauncherSettingsRepository(database)
            val custom = AppCategory("custom", placement = CategoryPlacement.AppList)
            val music = AppCategory.defaults().first { it.id == MusicCategoryId }.copy(name = "Audio", itemKeys = listOf("app:a"))
            val notification = AppCategory.defaults().first { it.id == NotificationCategoryId }
                .copy(name = "Alerts", appLimit = 6, placement = CategoryPlacement.AppList)
            val categories = AppCategory.defaults().map { when (it.id) {
                MusicCategoryId -> music
                NotificationCategoryId -> notification
                else -> it
            } } + custom
            repository.updateSettings(LauncherSettings(dimWallpaper = true, appCategories = categories,
                homeLayout = HomeLayout(homeOrder = listOf("widget:1", "category:favorites", "category:music")),
                listAppearance = ListAppearance(appList = AppListLayout(order = listOf("category:pinned", "category:notification",
                    AppListBlock.AllApps.key, AppListBlock.BottomFolders.key, AppListBlock.GraceLauncher.key, custom.key)))))
            repository.mutateSettings { settings -> settings.copy(appCategories = settings.appCategories!!.filterNot {
                it.id == MusicCategoryId
            }.map { if (it.id == FavoritesCategoryId) it.copy(placement = CategoryPlacement.AppList) else it }) }
            repository.mutateAppCategories(emptyList()) { current -> current.filterNot {
                it.id == NotificationCategoryId || it.id == custom.id
            }.map { if (it.id == PinnedCategoryId) it.copy(placement = CategoryPlacement.Home) else it } }
            val saved = repository.snapshots.first().settings
            assertEquals(music, saved.appCategories!!.first { it.id == MusicCategoryId })
            assertEquals(notification, saved.appCategories!!.first { it.id == NotificationCategoryId })
            assertTrue(saved.dimWallpaper)
            assertEquals(listOf("widget:1", "category:music", "category:pinned"), saved.homeLayout.homeOrder)
            assertEquals(listOf("category:notification", AppListBlock.AllApps.key, AppListBlock.BottomFolders.key,
                AppListBlock.GraceLauncher.key, "category:favorites"), saved.listAppearance.appList.order)
            assertEquals(CategoryIcon.Circle, saved.appCategories!!.first { it.id == FavoritesCategoryId }.icon)
        } finally { database.close() }
    }

    @Test fun extendedCategoriesRoundTripWithoutLosingManualDraftsOrBuiltInChanges() {
        val categories = AppCategory.defaults(listOf("app:a")).map { category ->
            if (category.id == MusicCategoryId) category.copy(name = "Audio", placement = CategoryPlacement.None,
                icon = CategoryIcon.Bedtime, inclusion = CategoryInclusion.Dynamic,
                dynamicRule = CategoryDynamicRule.FrequentlyOpened, itemKeys = listOf("folder:manual"), appLimit = 12)
            else if (category.id == NotificationCategoryId) category.copy(placement = CategoryPlacement.AppList)
            else category
        } + AppCategory("custom", "News", listOf("shortcut:manual"), CategoryCondition.Time,
            startMinute = 1320, endMinute = 420, placement = CategoryPlacement.AppList,
            inclusion = CategoryInclusion.Dynamic, dynamicRule = CategoryDynamicRule.Notifications,
            notificationLevels = setOf(CategoryNotificationLevel.High), appLimit = 6, icon = CategoryIcon.Info)
        assertEquals(categories, AppCategory.decode(AppCategory.encode(categories)))
        val none = categories.map { it.copy(notificationLevels = emptySet()) }
        assertEquals(none, AppCategory.decode(AppCategory.encode(none)))
    }

    @Test fun legacyHomeFlagsMigrateAndDeletedMusicIsRestoredWithoutReplacingFavorites() {
        val legacy = """[
            {"id":"favorites","name":"My favorites","items":["app:a"],"showOnHome":false},
            {"id":"custom","name":"Custom","items":["folder:x"],"showOnHome":true}
        ]"""
        val categories = requireNotNull(AppCategory.decode(legacy))
        assertEquals(listOf("favorites", "custom", "music", "pinned", "notification"), categories.map { it.id })
        assertEquals("My favorites", categories.first().name)
        assertEquals(CategoryPlacement.None, categories.first().placement)
        assertEquals(listOf("app:a"), categories.first().itemKeys)
        assertEquals(CategoryPlacement.Home, categories[1].placement)
        assertEquals(CategoryIcon.Circle, categories[1].icon)
        assertEquals(AppCategory.defaults().first { it.id == MusicCategoryId }, categories[2])
        assertEquals(CategoryPlacement.AppList, categories[3].placement)
        assertEquals(CategoryPlacement.None, categories[4].placement)
        assertEquals(CategoryInclusion.Dynamic, categories[4].inclusion)
        assertEquals(categories, AppCategory.decode(AppCategory.encode(categories)))
    }

    @Test fun unknownFieldsAndMalformedEntriesDoNotDiscardValidCategories() {
        val json = """[null,{},3,
            {"id":"custom","name":"Custom","placement":"AppList","showOnHome":true,
             "inclusion":"unknown","dynamicRule":"unknown","icon":"unknown","appLimit":999,
             "notificationLevels":["High","unknown"],"items":["app:a",null,8,"category:bad"]}
        ]"""
        val custom = requireNotNull(AppCategory.decode(json)).first()
        assertEquals("custom", custom.id)
        assertEquals(CategoryPlacement.AppList, custom.placement)
        assertEquals(CategoryInclusion.Manual, custom.inclusion)
        assertEquals(CategoryDynamicRule.Notifications, custom.dynamicRule)
        assertEquals(CategoryIcon.Circle, custom.icon)
        assertEquals(30, custom.appLimit)
        assertEquals(setOf(CategoryNotificationLevel.High), custom.notificationLevels)
        assertEquals(listOf("app:a"), custom.itemKeys)
        assertNull(AppCategory.decode(null))
        assertNull(AppCategory.decode("not json"))
        assertEquals(AppCategory.defaults(), AppCategory.decode("[]"))
    }
}
