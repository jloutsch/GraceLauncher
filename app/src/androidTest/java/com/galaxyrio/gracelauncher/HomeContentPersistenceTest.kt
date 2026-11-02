package com.galaxyrio.gracelauncher

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeContentPersistenceTest {
    @Test fun categoriesMigrateOnceAndEditsKeepOtherHomeData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LauncherDatabase::class.java).build()
        try {
            val repository = LauncherSettingsRepository(db)
            val original = LauncherSettings(homeLayout = HomeLayout(widgetId = 41, topOffsetDp = 38f,
                homeOrder = listOf("b", "widget:41", "folder:x", "a", "shortcut:x")), dimWallpaper = true)
            repository.updateSettings(original)
            repository.saveFolder(LauncherFolder("x", "Existing folder", listOf("a"), FolderPlacement.Both))
            repository.migrateAppCategories(listOf("a", "b", "shortcut:x", "missing", "folder:x"))
            val migrated = repository.snapshots.first()
            assertEquals(listOf("b", "a", "shortcut:x", "missing"), migrated.settings.appCategories!!.first().itemKeys)
            assertEquals(FolderPlacement.Both, migrated.folders.single().placement)
            assertEquals(41, migrated.settings.homeLayout.widgetId)
            assertEquals(38f, migrated.settings.homeLayout.topOffsetDp)
            assertTrue(migrated.settings.dimWallpaper)
            repository.migrateAppCategories(listOf("different"))
            assertEquals(migrated, repository.snapshots.first())
            repository.mutateAppCategories(emptyList()) { categories -> categories.map {
                if (it.id != FavoritesCategoryId) it else it.copy(name = "Custom name", itemKeys = listOf("folder:x", "shortcut:x"),
                    condition = CategoryCondition.Time, startMinute = 1320, endMinute = 420, placement = CategoryPlacement.None)
            } }
            val saved = repository.snapshots.first().settings.appCategories!!.first()
            assertEquals(saved, AppCategory.decode(AppCategory.encode(listOf(saved)))!!.first())
            assertEquals(listOf("folder:x", "shortcut:x"), saved.itemKeys)
            assertEquals(1320, saved.startMinute)
            assertFalse(saved.showOnHome)
            repository.deleteFolder("x")
            assertEquals(listOf("shortcut:x"), repository.snapshots.first().settings.appCategories!!.first().itemKeys)
        } finally { db.close() }
    }

    @Test fun legacyWidgetMigratesOnceWithoutRebindingOrLosingOtherWidgets() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LauncherDatabase::class.java).build()
        try {
            val settings = LauncherSettingsRepository(db)
            val items = LauncherItemsRepository(db)
            val original = HomeLayout(widgetId = 41, widgetProvider = "example/.Provider", widgetLabel = "Existing",
                widgetHeightDp = 240, topOffsetDp = 38f, baselineOffsetDp = 85f, clockCalendarEnabled = false)
            settings.updateSettings(LauncherSettings(homeLayout = original))
            items.updatePopup(HomeContentKey, emptyList()) { it + PopupItem("widget:42", HomeLayout(widgetId = 42)) }
            settings.migrateLegacyHomeWidget()
            val saved = items.snapshots.first().popups.getValue(HomeContentKey)
            assertEquals(listOf(41, 42), saved.map { it.widget?.widgetId })
            assertEquals(original.widgetProvider, saved.first().widget?.widgetProvider)
            assertEquals(240, saved.first().widget?.widgetHeightDp)
            val home = settings.snapshots.first().settings.homeLayout
            assertFalse(home.hasWidget)
            assertEquals(38f, home.topOffsetDp)
            assertEquals(85f, home.baselineOffsetDp)
            assertFalse(home.clockCalendarEnabled)
            assertTrue("widget:41" in home.homeOrder.orEmpty())
            assertEquals(listOf(41), items.updatePopup(HomeContentKey, emptyList()) { it.filterNot { item -> item.key == "widget:41" } })
            assertTrue(items.updatePopup(HomeContentKey, emptyList()) { it }.isEmpty())
        } finally { db.close() }
    }

    @Test fun homeMetadataRoundTripsWithoutChangingWidgetPresentation() {
        val layout = HomeLayout(homeOrder = listOf(GraceWidget.ClockCalendar.key, "grace:baseline", "widget:8"),
            baselineEnabled = false, clockCalendarEnabled = false, baselineOffsetDp = 90f, popupContentScale = 120, popupFullWidth = true)
        assertEquals(layout, HomeLayout.decode(layout.encode()))
        assertTrue(HomeLayout.decode("{}").baselineEnabled)
        assertTrue(HomeLayout.decode("{}").clockCalendarEnabled)
        assertNull(HomeLayout.decode("{}").homeOrder)
    }

    @Test fun oldStoredClockCalendarOrderCollapsesWithoutRestoringHiddenWidgets() {
        val old = """{"homeOrder":["app:a","grace:calendar","widget:8","grace:clock","grace:clock-calendar"],"clockCalendarEnabled":false,"topOffsetDp":38}"""
        val migrated = HomeLayout.decode(old)
        assertEquals(listOf("app:a", GraceWidget.ClockCalendar.key, "widget:8"), migrated.homeOrder)
        assertFalse(migrated.clockCalendarEnabled)
        assertEquals(38f, migrated.topOffsetDp)
        assertEquals(migrated, HomeLayout.decode(migrated.encode()))
    }
}
