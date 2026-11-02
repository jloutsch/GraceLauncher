package com.galaxyrio.gracelauncher.ui.drawer

import com.galaxyrio.gracelauncher.data.LauncherAlphabet
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherAppOrder
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.AppCategory
import com.galaxyrio.gracelauncher.data.AppListBlock
import com.galaxyrio.gracelauncher.data.AppListLayout
import com.galaxyrio.gracelauncher.data.CategoryIcon
import com.galaxyrio.gracelauncher.data.CategoryPlacement

const val FolderSection = "◇"
const val GraceSection = "grace-launcher"

sealed interface DrawerItem {
    val key: String
    val section: String

    data class Header(override val section: String) : DrawerItem {
        override val key = "header:$section"
    }

    data class App(val app: LauncherApp, override val section: String = app.section) : DrawerItem {
        override val key = if (section.startsWith("category:")) "$section:app:${app.key}" else app.key
    }
    data class Folder(val folder: LauncherFolder, override val section: String = FolderSection) : DrawerItem {
        override val key = if (section.startsWith("category:")) "$section:folder:${folder.id}" else "folder:${folder.id}"
    }
    data class PrivateApp(val app: LauncherApp) : DrawerItem {
        override val key = app.key
        override val section = FolderSection
    }
    data object PrivateStatus : DrawerItem {
        override val key = "private-space-status"
        override val section = FolderSection
    }
    data object WorkStatus : DrawerItem {
        override val key = "work-profile-status"
        override val section = FolderSection
    }
}

class AppListModel(apps: List<LauncherApp>, folders: List<LauncherFolder> = emptyList(),
    privateFolder: LauncherFolder? = null, privateExpanded: Boolean = false, privateApps: List<LauncherApp> = emptyList(),
    recentlyInstalledFolder: LauncherFolder? = null,
    workFolder: LauncherFolder? = null, workExpanded: Boolean = false, workApps: List<LauncherApp> = emptyList(),
    usageRanking: Map<String, Long> = emptyMap(),
    appList: AppListLayout = AppListLayout(), categories: List<AppCategory> = emptyList(),
    categoryItems: Map<String, List<LauncherApp>> = emptyMap(), headphones: Boolean = false, minuteOfDay: Int = 0) {
    private val settingsApp = (apps.firstOrNull { it.isLauncherSettings && it.user == null }
        ?: apps.firstOrNull(LauncherApp::isLauncherSettings))
        .takeIf { appList.showGraceLauncher && appList.showGraceSettings }
    private val inlineFolders = folders.filter { it.placement.inAppList && !it.appListAtBottom }.associateBy(LauncherFolder::key)
    // Use exactly the same name/pinyin ordering and alphabet sections as apps.
    private val grouped = (apps.filterNot { it.key == settingsApp?.key } + inlineFolders.values.map(LauncherFolder::asApp))
        .sortedWith(LauncherAppOrder).groupBy(LauncherApp::section)
    private val appLetters = LauncherAlphabet.filter { it != "#" && it in grouped } +
        grouped.keys.filter { it !in LauncherAlphabet }.sorted() +
        if ("#" in grouped) listOf("#") else emptyList()
    private val drawerFolders = folders.filter { it.placement.inAppList && it.appListAtBottom }
    private val foldersByKey = (folders + listOfNotNull(privateFolder, workFolder, recentlyInstalledFolder))
        .associateBy(LauncherFolder::key)
    private val activeCategories: Map<String, AppCategory> = categories.asSequence()
        .filter { it.placement == CategoryPlacement.AppList && it.isVisible(headphones, minuteOfDay) }
        .map { if (it.icon == CategoryIcon.None) it.copy(icon = CategoryIcon.Circle) else it }
        .associateBy(AppCategory::key)
    val items: List<DrawerItem> = buildList {
        val categoryKeys = mutableSetOf<String>()
        appList.orderedKeys(categories).forEach { key ->
            when (key) {
                AppListBlock.AllApps.key -> appLetters.forEach { letter ->
                    add(DrawerItem.Header(letter))
                    val original = grouped.getValue(letter)
                    // Keep inline folders, profiles and shortcuts in their original slots.
                    fun eligible(app: LauncherApp) = app.user == null && app.folderId == null && app.shortcut == null
                    val sorted = original.filter(::eligible).sortedByDescending { usageRanking[it.packageName] ?: 0 }.iterator()
                    original.map { if (eligible(it)) sorted.next() else it }.forEach { app ->
                        val folder = inlineFolders[app.key]
                        add(if (folder == null) DrawerItem.App(app) else DrawerItem.Folder(folder, letter))
                    }
                }
                AppListBlock.BottomFolders.key -> {
                    if (drawerFolders.isNotEmpty() || privateFolder != null || workFolder != null) {
                        add(DrawerItem.Header(FolderSection))
                        drawerFolders.forEach { add(DrawerItem.Folder(it)) }
                        if (workFolder != null) {
                            add(DrawerItem.Folder(workFolder))
                            if (workExpanded) {
                                workApps.forEach { add(DrawerItem.App(it, FolderSection)) }
                                if (workApps.isEmpty()) add(DrawerItem.WorkStatus)
                            }
                        }
                        // Profile contents stay with this group when the group is reordered.
                        if (privateFolder != null) {
                            add(DrawerItem.Folder(privateFolder))
                            if (privateExpanded) {
                                privateApps.forEach { add(DrawerItem.PrivateApp(it)) }
                                if (privateApps.isEmpty()) add(DrawerItem.PrivateStatus)
                            }
                        }
                    }
                }
                AppListBlock.GraceLauncher.key -> {
                    if (appList.showGraceLauncher && (recentlyInstalledFolder != null || settingsApp != null)) {
                        add(DrawerItem.Header(GraceSection))
                        recentlyInstalledFolder?.let { add(DrawerItem.Folder(it, GraceSection)) }
                        settingsApp?.let { add(DrawerItem.App(it, GraceSection)) }
                    }
                }
                else -> activeCategories[key]?.let { category ->
                    // Categories deduplicate among themselves; All apps remains complete.
                    val members = categoryItems[category.key].orEmpty().filter { categoryKeys.add(it.key) }
                    // Empty results, including duplicates claimed by earlier categories,
                    // have no heading or alphabet target. Their configured order is retained.
                    if (members.isNotEmpty()) {
                        add(DrawerItem.Header(category.key))
                        members.forEach { app ->
                            val folder = foldersByKey[app.key]
                            add(if (folder == null) DrawerItem.App(app, category.key) else DrawerItem.Folder(folder, category.key))
                        }
                    }
                }
            }
        }
    }
    val categorySections: Map<String, AppCategory> = items.asSequence().filterIsInstance<DrawerItem.Header>()
        .mapNotNull { activeCategories[it.section] }.associateBy(AppCategory::key)
    // Keep the rail in sync with every section, including launcher utilities.
    val letters: List<String> = items.filterIsInstance<DrawerItem.Header>().map { it.section }
    private val sectionIndices = items.mapIndexedNotNull { index, item ->
        (item as? DrawerItem.Header)?.let { it.section to index }
    }.toMap()

    /** Every index refers to the same complete list, even while other groups are hidden. */
    fun indexOfSection(letter: String): Int =
        sectionIndices[letter] ?: 0
}
