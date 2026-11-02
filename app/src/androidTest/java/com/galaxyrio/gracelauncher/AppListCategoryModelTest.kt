package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.os.Process
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.AppCategory
import com.galaxyrio.gracelauncher.data.AppListBlock
import com.galaxyrio.gracelauncher.data.AppListLayout
import com.galaxyrio.gracelauncher.data.CategoryCondition
import com.galaxyrio.gracelauncher.data.CategoryIcon
import com.galaxyrio.gracelauncher.data.CategoryInclusion
import com.galaxyrio.gracelauncher.data.CategoryNotificationLevel
import com.galaxyrio.gracelauncher.data.CategoryPlacement
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.ListAppearance
import com.galaxyrio.gracelauncher.data.PrivateSpaceFolderId
import com.galaxyrio.gracelauncher.data.RecentlyInstalledFolderId
import com.galaxyrio.gracelauncher.data.WorkProfileFolderId
import com.galaxyrio.gracelauncher.data.notifications.NotificationAppId
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.drawer.AppListModel
import com.galaxyrio.gracelauncher.ui.drawer.DrawerItem
import com.galaxyrio.gracelauncher.ui.drawer.FolderSection
import com.galaxyrio.gracelauncher.ui.drawer.GraceSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppListCategoryModelTest {
    private fun app(name: String) = LauncherApp(ComponentName("example.${name.lowercase()}", "Activity"), name, null)
    private fun category(id: String) = AppCategory(id, name = id, placement = CategoryPlacement.AppList)

    @Test fun categoryOrderOwnsDuplicatesWhileAllAppsKeepsEveryApp() {
        val alpha = app("Alpha")
        val beta = app("Beta")
        val gamma = app("Gamma")
        val first = category("first")
        val second = category("second")
        val model = AppListModel(listOf(gamma, beta, alpha), categories = listOf(first, second),
            appList = AppListLayout(order = listOf(AppListBlock.AllApps.key, second.key, first.key)),
            categoryItems = mapOf(first.key to listOf(alpha, beta), second.key to listOf(beta, gamma)))

        fun appsIn(section: String) = model.items.filterIsInstance<DrawerItem.App>()
            .filter { it.section == section }.map { it.app.key }
        assertEquals(listOf(alpha.key), appsIn("A"))
        assertEquals(listOf(beta.key), appsIn("B"))
        assertEquals(listOf(gamma.key), appsIn("G"))
        assertEquals(listOf(beta.key, gamma.key), appsIn(second.key))
        assertEquals(listOf(alpha.key), appsIn(first.key))
        assertEquals(listOf("A", "B", "G", second.key, first.key), model.letters)
        assertEquals(model.items.size, model.items.map { it.key }.distinct().size)
        model.letters.forEach { assertEquals(DrawerItem.Header(it), model.items[model.indexOfSection(it)]) }
    }

    @Test fun emptyAndFullyDeduplicatedCategoriesHaveNoTargetsAndInactiveOnesDoNotClaimItems() {
        val alpha = app("Alpha")
        val headphones = category("headphones").copy(condition = CategoryCondition.Headphones)
        val night = category("night").copy(condition = CategoryCondition.Time, startMinute = 22 * 60, endMinute = 6 * 60)
        val empty = category("empty")
        val members = mapOf(headphones.key to listOf(alpha), night.key to listOf(alpha))
        val layout = AppListLayout(order = listOf(headphones.key, night.key, empty.key, AppListBlock.AllApps.key))
        val disconnected = AppListModel(listOf(alpha), appList = layout, categories = listOf(headphones, night, empty),
            categoryItems = members, headphones = false, minuteOfDay = 23 * 60)

        assertFalse(headphones.key in disconnected.letters)
        assertEquals(listOf(alpha), disconnected.items.filterIsInstance<DrawerItem.App>()
            .filter { it.section == night.key }.map { it.app })
        assertFalse(empty.key in disconnected.letters)
        assertFalse(empty.key in disconnected.categorySections)

        val connected = AppListModel(listOf(alpha), appList = layout, categories = listOf(headphones, night, empty),
            categoryItems = members, headphones = true, minuteOfDay = 60)
        assertFalse(night.key in connected.letters)
        assertFalse(night.key in connected.categorySections)
        assertTrue(connected.items.none { it.section == night.key })
        val noon = AppListModel(listOf(alpha), appList = layout, categories = listOf(headphones, night, empty),
            categoryItems = members, minuteOfDay = 12 * 60)
        assertFalse(night.key in noon.letters)
        assertFalse(empty.key in noon.letters)
        assertTrue(empty.key in layout.orderedKeys(listOf(headphones, night, empty)))
        val populated = AppListModel(listOf(alpha), appList = layout, categories = listOf(empty.copy(icon = CategoryIcon.None)),
            categoryItems = mapOf(empty.key to listOf(alpha)))
        assertTrue(empty.key in populated.letters)
        assertEquals(CategoryIcon.Circle, populated.categorySections.getValue(empty.key).icon)
    }

    @Test fun categoryFoldersAndStacksRemainFolderRowsWithDistinctKeys() {
        val folder = LauncherFolder("folder", "Folder", emptyList(), FolderPlacement.AppList)
        val stack = LauncherFolder("stack", "Stack", emptyList(), FolderPlacement.AppList, isAppStack = true)
        val group = category("groups")
        val model = AppListModel(emptyList(), listOf(folder, stack), categories = listOf(group),
            categoryItems = mapOf(group.key to listOf(folder.asApp(), stack.asApp())))

        assertEquals(listOf(folder, stack), model.items.filterIsInstance<DrawerItem.Folder>()
            .filter { it.section == group.key }.map { it.folder })
        assertEquals(listOf(folder, stack), model.items.filterIsInstance<DrawerItem.Folder>()
            .filter { it.section == FolderSection }.map { it.folder })
        assertEquals(model.items.size, model.items.map { it.key }.distinct().size)
    }

    @Test fun manualAndNotificationCategoriesRestoreHeadersAndTargetsAtTheirSavedPositions() {
        val alpha = app("Alpha")
        val beta = app("Beta")
        val manual = category("manual")
        val notifications = category("notifications").copy(inclusion = CategoryInclusion.Dynamic)
        val savedOrder = listOf(notifications.key, AppListBlock.AllApps.key, manual.key, AppListBlock.BottomFolders.key)
        val layout = AppListLayout(order = savedOrder, showGraceLauncher = false)
        val initial = LauncherUiState(apps = listOf(alpha, beta), isLoadingApps = false,
            settings = LauncherSettings(appCategories = listOf(manual, notifications),
                listAppearance = ListAppearance(appList = layout)))
        fun model(state: LauncherUiState) = AppListModel(state.appListApps,
            appList = state.settings.listAppearance.appList, categories = state.appCategories,
            categoryItems = state.appCategories.associate { category ->
                category.key to state.categoryItems(category).mapNotNull(state::findItem)
            })

        assertEquals(listOf("A", "B"), model(initial).letters)
        val selected = initial.copy(settings = initial.settings.copy(appCategories = listOf(
            manual.copy(itemKeys = listOf(beta.key)), notifications)))
        assertEquals(listOf("A", "B", manual.key), model(selected).letters)
        val populated = selected.copy(categoryNotifications = mapOf(
            NotificationAppId(alpha.packageName, Process.myUserHandle()) to setOf(CategoryNotificationLevel.Normal)))
        val visible = model(populated)
        assertEquals(listOf(notifications.key, "A", "B", manual.key), visible.letters)
        assertEquals(setOf(notifications.key, manual.key), visible.categorySections.keys)
        visible.letters.forEach { section ->
            assertEquals(DrawerItem.Header(section), visible.items[visible.indexOfSection(section)])
        }

        val cleared = populated.copy(settings = initial.settings, categoryNotifications = emptyMap())
        assertEquals(listOf("A", "B"), model(cleared).letters)
        val restored = model(cleared.copy(settings = populated.settings,
            categoryNotifications = populated.categoryNotifications))
        assertEquals(visible.items, restored.items)
        assertEquals(visible.letters, restored.letters)
        assertEquals(savedOrder, cleared.settings.listAppearance.appList.order)
        assertEquals(savedOrder, cleared.settings.listAppearance.appList.orderedKeys(cleared.appCategories))
    }

    @Test fun hidingGraceGroupOrItsSettingsRowReturnsSettingsToAllApps() {
        val settings = app("Grace settings").copy(isLauncherSettings = true)
        val recent = LauncherFolder(RecentlyInstalledFolderId, "Recent", emptyList(), FolderPlacement.AppList)
        val normal = AppListModel(listOf(settings), recentlyInstalledFolder = recent)
        assertEquals(GraceSection, normal.items.filterIsInstance<DrawerItem.App>().single().section)

        val hidden = AppListModel(listOf(settings), recentlyInstalledFolder = recent,
            appList = AppListLayout(showGraceLauncher = false))
        assertFalse(GraceSection in hidden.letters)
        assertEquals("G", hidden.items.filterIsInstance<DrawerItem.App>().single().section)
        assertTrue(hidden.items.none { it is DrawerItem.Folder && it.folder.id == RecentlyInstalledFolderId })

        val recentOnly = AppListModel(listOf(settings), recentlyInstalledFolder = recent,
            appList = AppListLayout(showGraceSettings = false))
        assertEquals("G", recentOnly.items.filterIsInstance<DrawerItem.App>().single().section)
        assertEquals(listOf(DrawerItem.Header(GraceSection), DrawerItem.Folder(recent, GraceSection)),
            recentOnly.items.filter { it.section == GraceSection })
    }

    @Test fun graceFooterPrefersPersonalSettingsWithoutRemovingProfileCopies() {
        val personal = app("Grace settings").copy(isLauncherSettings = true)
        val work = personal.copy(user = UserHandle.getUserHandleForUid(1_000_000), userSerial = 10L, isWorkProfile = true)
        val clone = personal.copy(user = UserHandle.getUserHandleForUid(1_100_000), userSerial = 11L, isCloneProfile = true)
        val model = AppListModel(listOf(work, clone, personal))
        val rows = model.items.filterIsInstance<DrawerItem.App>()

        assertEquals(listOf(personal.key), rows.filter { it.section == GraceSection }.map { it.app.key })
        assertEquals(setOf(work.key, clone.key), rows.filter { it.section == "G" }.map { it.app.key }.toSet())
        assertEquals(setOf(personal.key, work.key, clone.key), rows.map { it.app.key }.toSet())
        assertEquals(model.items.size, model.items.map { it.key }.distinct().size)
    }

    @Test fun movingFoldersKeepsExpandedProfileContentsInsideThatBlock() {
        val alpha = app("Alpha")
        val workApp = app("Work app").copy(isWorkProfile = true)
        val work = LauncherFolder(WorkProfileFolderId, "Work", listOf(workApp.key), FolderPlacement.AppList)
        val privateSpace = LauncherFolder(PrivateSpaceFolderId, "Private", emptyList(), FolderPlacement.AppList)
        val model = AppListModel(listOf(alpha), privateFolder = privateSpace, privateExpanded = true,
            workFolder = work, workExpanded = true, workApps = listOf(workApp),
            appList = AppListLayout(order = listOf(AppListBlock.BottomFolders.key, AppListBlock.AllApps.key)))

        assertEquals(listOf(DrawerItem.Header(FolderSection), DrawerItem.Folder(work),
            DrawerItem.App(workApp, FolderSection), DrawerItem.Folder(privateSpace), DrawerItem.PrivateStatus),
            model.items.takeWhile { it.section == FolderSection })
        assertEquals(listOf(FolderSection, "A"), model.letters)
    }
}
