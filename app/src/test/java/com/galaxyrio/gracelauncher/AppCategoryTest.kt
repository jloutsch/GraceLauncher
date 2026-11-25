package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek

class AppCategoryTest {
    @Test fun builtInsHaveProtectedIdentityAndIndependentDefaultPlacementAndMembership() {
        val defaults = AppCategory.defaults(listOf("app:a"))
        assertEquals(listOf(FavoritesCategoryId, MusicCategoryId, PinnedCategoryId, NotificationCategoryId), defaults.map { it.id })
        assertTrue(defaults.all { it.isBuiltIn })
        assertFalse(AppCategory("custom").isBuiltIn)
        assertEquals(listOf("app:a"), defaults.first().itemKeys)
        assertEquals(listOf(CategoryPlacement.Home, CategoryPlacement.Home, CategoryPlacement.AppList, CategoryPlacement.None), defaults.map { it.placement })
        assertEquals(listOf(CategoryIcon.None, CategoryIcon.MusicNote, CategoryIcon.Keep, CategoryIcon.NotificationsUnread), defaults.map { it.icon })
        assertEquals(CategoryCondition.Headphones, defaults[1].condition)
        assertEquals(CategoryCondition.Always, defaults[2].condition)
        assertEquals(CategoryInclusion.Manual, defaults[2].inclusion)
        assertEquals(CategoryInclusion.Dynamic, defaults[3].inclusion)
        assertEquals(CategoryDynamicRule.Notifications, defaults[3].dynamicRule)
        assertEquals(CategoryNotificationLevel.entries.toSet(), defaults[3].notificationLevels)
        assertTrue(defaults.all { it.daysOfWeek == DayOfWeek.entries.toSet() && !it.allDay })
    }

    @Test fun normalizationRestoresDeletedBuiltInsWithoutResettingExistingCustomizationOrOrder() {
        val favorite = AppCategory(FavoritesCategoryId, name = "Mine", itemKeys = listOf("app:a"), placement = CategoryPlacement.None)
        val custom = AppCategory("custom", name = "Custom", inclusion = CategoryInclusion.Dynamic,
            itemKeys = listOf("app:b", "folder:c", "shortcut:d", "app:b", "category:invalid", "widget:2"), appLimit = 100)
        val repaired = AppCategory.normalize(listOf(custom, favorite, favorite.copy(name = "Duplicate")))
        assertEquals(listOf("custom", FavoritesCategoryId, MusicCategoryId, PinnedCategoryId, NotificationCategoryId), repaired.map { it.id })
        assertEquals(favorite, repaired[1])
        assertEquals(listOf("app:b", "folder:c", "shortcut:d"), repaired.first().itemKeys)
        assertEquals(30, repaired.first().appLimit)
        assertEquals(1, custom.copy(appLimit = 0).normalized().appLimit)
        assertEquals(CategoryIcon.Circle, custom.icon)
        assertEquals(repaired, AppCategory.normalize(repaired))
        assertEquals(CategoryIcon.Circle, favorite.copy(placement = CategoryPlacement.AppList).normalized().icon)
        assertEquals(CategoryIcon.None, favorite.normalized().icon)
    }

    @Test fun conditionsUseExclusiveEndAndSupportMidnight() {
        val day = DayOfWeek.MONDAY
        assertTrue(AppCategory("a").isVisible(false, 0, day))
        val music = AppCategory("music", condition = CategoryCondition.Headphones)
        assertFalse(music.isVisible(false, 600, day))
        assertTrue(music.isVisible(true, 600, day))
        val daytime = AppCategory("day", condition = CategoryCondition.Time, startMinute = 480, endMinute = 1080)
        assertFalse(daytime.isVisible(false, 479, day))
        assertTrue(daytime.isVisible(false, 480, day))
        assertTrue(daytime.isVisible(false, 1079, day))
        assertFalse(daytime.isVisible(false, 1080, day))
        val overnight = daytime.copy(startMinute = 1320, endMinute = 420)
        assertTrue(overnight.isVisible(false, 1320, day))
        assertTrue(overnight.isVisible(false, 0, day))
        assertTrue(overnight.isVisible(false, 419, day))
        assertFalse(overnight.isVisible(false, 420, day))
        assertFalse(overnight.isVisible(false, 1319, day))
        assertTrue(daytime.copy(startMinute = 0, endMinute = 0).isVisible(false, 700, day))
    }

    @Test fun weekdaySelectionAppliesToTheCurrentDayWithoutOvernightSpillover() {
        val category = AppCategory("night", condition = CategoryCondition.Time, startMinute = 22 * 60,
            endMinute = 2 * 60, daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY))
        listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
            assertTrue(category.isVisible(false, 0, day))
            assertTrue(category.isVisible(false, 119, day))
            assertFalse(category.isVisible(false, 120, day))
            assertFalse(category.isVisible(false, 1319, day))
            assertTrue(category.isVisible(false, 1320, day))
            assertTrue(category.isVisible(false, 1439, day))
        }
        assertFalse(category.isVisible(false, 0, DayOfWeek.TUESDAY))
        assertFalse(category.isVisible(false, 23 * 60, DayOfWeek.SATURDAY))
        assertFalse(category.copy(daysOfWeek = setOf(DayOfWeek.SUNDAY)).isVisible(false, 0, DayOfWeek.MONDAY))
    }

    @Test fun allDayUsesSelectedWeekdaysAndPreservesTheSavedTimeRange() {
        val category = AppCategory("work", condition = CategoryCondition.Time, startMinute = 480,
            endMinute = 1080, daysOfWeek = setOf(DayOfWeek.FRIDAY))
        val allDay = category.copy(allDay = true)
        assertTrue(allDay.isVisible(false, 0, DayOfWeek.FRIDAY))
        assertTrue(allDay.isVisible(false, 1439, DayOfWeek.FRIDAY))
        assertFalse(allDay.isVisible(false, 600, DayOfWeek.THURSDAY))
        assertFalse(allDay.isVisible(false, 0, DayOfWeek.SATURDAY))
        assertEquals(category, allDay.copy(allDay = false))
        assertFalse(allDay.copy(allDay = false).isVisible(false, 0, DayOfWeek.FRIDAY))
        assertTrue(allDay.copy(allDay = false).isVisible(false, 480, DayOfWeek.FRIDAY))
        DayOfWeek.entries.forEach { day ->
            assertFalse(category.copy(daysOfWeek = emptySet()).isVisible(false, 600, day))
            assertFalse(allDay.copy(daysOfWeek = emptySet()).isVisible(false, 600, day))
        }
        assertTrue(allDay.copy(condition = CategoryCondition.Always).isVisible(false, 600, DayOfWeek.MONDAY))
        assertTrue(allDay.copy(condition = CategoryCondition.Headphones).isVisible(true, 600, DayOfWeek.MONDAY))
    }

    @Test fun migrationKeepsMembershipOrderWidgetsGroupsAndUnavailableFavorites() {
        val layout = HomeLayout(homeOrder = listOf("grace:clock", "b", "widget:7", "folder:x", "a", "shortcut:x"),
            topOffsetDp = 24f, baselineOffsetDp = 52f, editingLocked = true)
        val migrated = migrateHomeCategories(LauncherSettings(homeLayout = layout),
            listOf("a", "b", "shortcut:x", "missing", "folder:x", PrivateSpaceFolderKey))
        val categories = requireNotNull(migrated.appCategories)
        assertEquals(listOf("b", "a", "shortcut:x", "missing"), categories.first().itemKeys)
        assertEquals(listOf("grace:clock-calendar", "category:favorites", "widget:7", "folder:x", "category:music"), migrated.homeLayout.homeOrder)
        assertEquals(layout.topOffsetDp, migrated.homeLayout.topOffsetDp)
        assertEquals(layout.baselineOffsetDp, migrated.homeLayout.baselineOffsetDp)
        assertTrue(migrated.homeLayout.editingLocked)
        assertEquals(CategoryCondition.Always, categories.first().condition)
        assertEquals(CategoryCondition.Headphones, categories.first { it.id == MusicCategoryId }.condition)
        assertTrue(categories.filter { it.placement == CategoryPlacement.AppList }.none { it.key in migrated.homeLayout.homeOrder.orEmpty() })
        assertEquals(migrated, migrateHomeCategories(migrated, listOf("different")))
    }

    @Test fun removingEveryFavoriteDoesNotRestoreLegacyMembers() {
        val migrated = migrateHomeCategories(LauncherSettings(), listOf("a"))
        val empty = migrated.copy(appCategories = migrated.appCategories!!.map { it.copy(itemKeys = emptyList()) })
        assertTrue(migrateHomeCategories(empty, listOf("a", "b")).appCategories!!.first().itemKeys.isEmpty())
    }

    @Test fun migrationWithoutExplicitHomeOrderKeepsLeadingProfileAndFolderPositions() {
        val migrated = migrateHomeCategories(LauncherSettings(), listOf(WorkProfileFolderKey, "a", "folder:x", "b"))
        assertEquals(listOf(WorkProfileFolderKey, "category:favorites", "folder:x", "category:music"),
            migrated.homeLayout.homeOrder!!.filterNot { it.startsWith("grace:") })
        assertEquals(listOf("a", "b"), migrated.appCategories!!.first().itemKeys)
    }

    @Test fun activeCategoriesExpandInPlaceAndOnlyTheFirstOccurrenceIsShown() {
        val favorites = AppCategory(FavoritesCategoryId, itemKeys = listOf("a", "folder:x", "missing"))
        val music = AppCategory(MusicCategoryId, itemKeys = listOf("a", "b", "folder:x", "shortcut:x"), condition = CategoryCondition.Headphones)
        val widget = PopupItem("widget:7", HomeLayout(widgetId = 7))
        val entries = listOf(PopupItem(favorites.key), PopupItem(music.key), widget, PopupItem("folder:x"))
        val on = expandHomeCategories(entries, listOf(favorites, music), true, 600, DayOfWeek.MONDAY) { it != "missing" }
        assertEquals(listOf("a", "folder:x", "b", "shortcut:x", "widget:7"), on.items.map { it.key })
        assertEquals(favorites.key, on.owners["a"])
        assertEquals(music.key, on.owners["b"])
        assertEquals(widget, on.items.last())
        val off = expandHomeCategories(entries, listOf(favorites, music), false, 600, DayOfWeek.MONDAY) { it != "missing" }
        assertEquals(listOf("a", "folder:x", "widget:7"), off.items.map { it.key })
        val reversed = expandHomeCategories(listOf(PopupItem(music.key), PopupItem(favorites.key)), listOf(favorites, music), true, 600, DayOfWeek.MONDAY) { it != "missing" }
        assertEquals(listOf("a", "b", "folder:x", "shortcut:x"), reversed.items.map { it.key })
        assertEquals(music.key, reversed.owners["a"])
    }

    @Test fun anInactiveEarlierCategoryDoesNotSuppressLaterMembers() {
        val sleeping = AppCategory("sleeping", itemKeys = listOf("a"), condition = CategoryCondition.Time, startMinute = 1200, endMinute = 420)
        val always = AppCategory("always", itemKeys = listOf("a"))
        val result = expandHomeCategories(listOf(PopupItem(sleeping.key), PopupItem(always.key)), listOf(sleeping, always), false, 600, DayOfWeek.MONDAY) { true }
        assertEquals(listOf("a"), result.items.map { it.key })
        assertEquals(always.key, result.owners["a"])
    }

    @Test fun anUnselectedWeekdayReleasesDuplicateItemsToTheNextHomeCategory() {
        val scheduled = AppCategory("work", itemKeys = listOf("a"), condition = CategoryCondition.Time,
            daysOfWeek = setOf(DayOfWeek.FRIDAY), allDay = true)
        val always = AppCategory("always", itemKeys = listOf("a"))
        val entries = listOf(PopupItem(scheduled.key), PopupItem(always.key))
        val categories = listOf(scheduled, always)
        val friday = expandHomeCategories(entries, categories, false, 1439, DayOfWeek.FRIDAY) { true }
        val saturday = expandHomeCategories(entries, categories, false, 0, DayOfWeek.SATURDAY) { true }
        assertEquals(listOf("a"), friday.items.map { it.key })
        assertEquals(listOf("a"), saturday.items.map { it.key })
        assertEquals(scheduled.key, friday.owners["a"])
        assertEquals(always.key, saturday.owners["a"])
    }

    @Test fun dynamicMembersAreResolvedOnlyForActiveCategoriesAndKeepFirstOccurrenceOwnership() {
        val sleeping = AppCategory("sleeping", inclusion = CategoryInclusion.Dynamic, condition = CategoryCondition.Headphones)
        val manual = AppCategory("manual", itemKeys = listOf("app:a"))
        val dynamic = AppCategory("dynamic", inclusion = CategoryInclusion.Dynamic, itemKeys = listOf("saved-manual"))
        val evaluated = mutableListOf<String>()
        val result = expandHomeCategories(listOf(PopupItem(sleeping.key), PopupItem(manual.key), PopupItem(dynamic.key)),
            listOf(sleeping, manual, dynamic), false, 600, DayOfWeek.MONDAY,
            categoryItems = { category ->
                evaluated += category.id
                if (category.inclusion == CategoryInclusion.Dynamic) listOf("app:a", "app:b", "unavailable") else category.itemKeys
            }, available = { it != "unavailable" })
        assertEquals(listOf("manual", "dynamic"), evaluated)
        assertEquals(listOf("app:a", "app:b"), result.items.map { it.key })
        assertEquals(manual.key, result.owners["app:a"])
        assertEquals(dynamic.key, result.owners["app:b"])
        assertEquals(listOf("saved-manual"), dynamic.itemKeys)
    }
}
