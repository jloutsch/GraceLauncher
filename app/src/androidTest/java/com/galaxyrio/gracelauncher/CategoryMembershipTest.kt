package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.os.Process
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.notifications.NotificationAppId
import com.galaxyrio.gracelauncher.platform.PrivateSpaceState
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryMembershipTest {
    private val workUser = UserHandle.getUserHandleForUid(1_000_000)
    private val cloneUser = UserHandle.getUserHandleForUid(1_100_000)
    private fun app(id: String, label: String = id, installedAt: Long = 1) =
        LauncherApp(ComponentName("fixture.$id", "Main"), label, null, firstInstallTime = installedAt)
    private fun category(rule: CategoryDynamicRule) =
        AppCategory("dynamic", inclusion = CategoryInclusion.Dynamic, dynamicRule = rule)
    private fun notificationId(app: LauncherApp) =
        NotificationAppId(app.packageName, app.user ?: Process.myUserHandle())

    @Test fun everyDynamicRuleExcludesExplicitAndFolderHiddenAppsAndNonAppEntries() {
        val visible = app("visible")
        val hidden = app("hidden", installedAt = 20)
        val folderHidden = app("folder-hidden", installedAt = 30)
        val settingsApp = app("settings", installedAt = 40).copy(isLauncherSettings = true)
        val shortcut = LauncherShortcut("open", visible.packageName, "Shortcut", null).asApp(visible)
        val folder = LauncherFolder("hiding", "Hiding folder", listOf(folderHidden.key),
            FolderPlacement.None, autoHideApps = true)
        val entries = listOf(visible, hidden, folderHidden, settingsApp, shortcut, folder.asApp())
        val state = LauncherUiState(apps = entries, isLoadingApps = false, folders = listOf(folder),
            hiddenAppKeys = setOf(hidden.key),
            settings = LauncherSettings(digitalWellbeing = DigitalWellbeingSettings(enabled = true)),
            usage = UsageSnapshot(hasAccess = true, openCounts = entries.associate { it.packageName to 10L }),
            categoryNotifications = entries.associate { notificationId(it) to setOf(CategoryNotificationLevel.High) })

        CategoryDynamicRule.entries.forEach { rule ->
            assertEquals(rule.name, listOf(visible.key), state.categoryItems(category(rule)))
        }
    }

    @Test fun notificationLevelsUseAnySelectedLevelAndKeepSamePackageProfilesSeparate() {
        val personal = app("shared", "Alpha personal")
        val work = personal.copy(label = "Beta work", user = workUser, userSerial = 10, isWorkProfile = true)
        val clone = personal.copy(label = "Gamma clone", user = cloneUser, userSerial = 11, isCloneProfile = true)
        val state = LauncherUiState(apps = listOf(clone, work, personal), isLoadingApps = false,
            categoryNotifications = mapOf(
                notificationId(personal) to setOf(CategoryNotificationLevel.Normal),
                notificationId(work) to setOf(CategoryNotificationLevel.High, CategoryNotificationLevel.Silent),
                notificationId(clone) to setOf(CategoryNotificationLevel.Silent),
            ))
        val rule = category(CategoryDynamicRule.Notifications)
        assertEquals(listOf(work.key), state.categoryItems(rule.copy(notificationLevels = setOf(CategoryNotificationLevel.High))))
        assertEquals(listOf(personal.key, work.key), state.categoryItems(rule.copy(
            notificationLevels = setOf(CategoryNotificationLevel.Normal, CategoryNotificationLevel.High))))
        assertEquals(listOf(work.key, clone.key), state.categoryItems(rule.copy(notificationLevels = setOf(CategoryNotificationLevel.Silent))))
        assertTrue(state.categoryItems(rule.copy(notificationLevels = emptySet())).isEmpty())

        val onlyWork = state.copy(categoryNotifications = mapOf(notificationId(work) to setOf(CategoryNotificationLevel.Normal)))
        assertEquals(listOf(work.key), onlyWork.categoryItems(rule))
    }

    @Test fun protectedPrivateAppsNeverEnterManualOrNotificationCategories() {
        val privateApp = app("private").copy(user = workUser, userSerial = 10, isPrivateSpace = true)
        val state = LauncherUiState(isLoadingApps = false, privateApps = listOf(privateApp),
            privateSpace = PrivateSpaceState(supported = true, user = workUser, serial = 10, locked = false, accessible = true),
            categoryNotifications = mapOf(notificationId(privateApp) to setOf(CategoryNotificationLevel.High)))
        val manual = AppCategory("manual", itemKeys = listOf(privateApp.key))
        val notifications = category(CategoryDynamicRule.Notifications)
        assertTrue(state.categoryItems(manual).isEmpty())
        assertTrue(state.categoryItems(notifications).isEmpty())

        val exposed = state.copy(settings = state.settings.copy(
            privateSpace = state.settings.privateSpace.copy(passwordProtected = false)))
        assertEquals(listOf(privateApp.key), exposed.categoryItems(manual))
        assertEquals(listOf(privateApp.key), exposed.categoryItems(notifications))
        assertTrue(exposed.copy(hiddenAppKeys = setOf(privateApp.key)).categoryItems(notifications).isEmpty())
    }

    @Test fun frequentAppsRequireDigitalWellbeingAndUsageAccessWithoutUsingDurationOrFrozenRanks() {
        val alpha = app("alpha", "Alpha")
        val beta = app("beta", "Beta")
        val betaActivity = beta.copy(componentName = ComponentName(beta.packageName, "Other"), label = "Beta other")
        val gamma = app("gamma", "Gamma")
        val zero = app("zero", "Zero")
        val workBeta = beta.copy(label = "Work beta", user = workUser, userSerial = 10, isWorkProfile = true)
        val rule = category(CategoryDynamicRule.FrequentlyOpened).copy(appLimit = 2)
        val state = LauncherUiState(apps = listOf(gamma, zero, betaActivity, workBeta, beta, alpha), isLoadingApps = false,
            settings = LauncherSettings(appCategories = listOf(rule), digitalWellbeing =
                DigitalWellbeingSettings(enabled = true, sortApps = false, refresh = UsageRefresh.Never)),
            usage = UsageSnapshot(hasAccess = true, day = mapOf(gamma.packageName to 999_999L),
                ranking = mapOf(gamma.packageName to 999L),
                openCounts = mapOf(beta.packageName to 50L, alpha.packageName to 20L, gamma.packageName to 20L, zero.packageName to 0L)))

        assertTrue(state.needsCategoryOpenCounts) // Placement None still needs an editor preview.
        assertEquals(listOf(beta.key, alpha.key), state.categoryItems(rule))
        assertEquals(listOf(beta.key, alpha.key, gamma.key), state.categoryItems(rule.copy(appLimit = 30)))
        assertTrue(state.copy(usage = state.usage.copy(hasAccess = false)).categoryItems(rule).isEmpty())
        assertTrue(state.copy(settings = state.settings.copy(digitalWellbeing =
            state.settings.digitalWellbeing.copy(enabled = false))).categoryItems(rule).isEmpty())
    }

    @Test fun manualMembershipRetainsOrderAndHiddenSelectionsAcrossItemKinds() {
        val alpha = app("alpha", "Alpha")
        val beta = app("beta", "Beta")
        val shortcut = LauncherShortcut("compose", alpha.packageName, "Compose", null).asApp(alpha)
        val folder = LauncherFolder("folder", "Folder", listOf(alpha.key), FolderPlacement.None)
        val stack = folder.copy(id = "stack", name = "Stack", isAppStack = true)
        val keys = listOf(beta.key, stack.key, "missing/Main", shortcut.key, folder.key, alpha.key)
        val manual = AppCategory("manual", itemKeys = keys, condition = CategoryCondition.Headphones)
        val state = LauncherUiState(apps = listOf(alpha, beta), shortcutApps = listOf(shortcut),
            folders = listOf(folder, stack), hiddenAppKeys = setOf(beta.key), isLoadingApps = false)

        assertEquals(keys - "missing/Main", state.categoryItems(manual))
        assertTrue(state.copy(isLoadingSettings = true).categoryItems(manual).isEmpty())
        assertTrue(state.copy(settingsLoadFailed = true).categoryItems(manual).isEmpty())
    }

    @Test fun recentInstallationsReuseNewestEightOrderingAndDeduplicatePerProfileInstallation() {
        val installed = (1..10).map { app("app$it", "App $it", installedAt = it.toLong()) }
        val newest = installed.last()
        val otherActivity = newest.copy(componentName = ComponentName(newest.packageName, "Other"), label = "Zulu activity")
        val workInstall = newest.copy(label = "Work installation", user = workUser, userSerial = 10, isWorkProfile = true)
        val state = LauncherUiState(apps = installed.reversed() + otherActivity + workInstall, isLoadingApps = false)
        val expected = listOf(newest.key, workInstall.key) + installed.dropLast(1).asReversed().take(6).map { it.key }

        assertEquals(expected, state.categoryItems(category(CategoryDynamicRule.RecentlyInstalled)))
        assertEquals(expected, state.recentlyInstalledApps.map { it.key })
        assertEquals(listOf(installed.first().key), state.copy(apps = listOf(installed.first()))
            .categoryItems(category(CategoryDynamicRule.RecentlyInstalled))) // No age cutoff or padding.
    }
}
