package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PopupGestureActionsTest {
    private val first = LauncherApp(ComponentName("fixture.first", "Activity"), "First", null)
    private val second = LauncherApp(ComponentName("fixture.second", "Activity"), "Second", null)
    private val folder = LauncherFolder("both", "Both", listOf(first.key, second.key), FolderPlacement.Favorites)
    private val newest = AppNotification("newest", first.packageName, "Test", "Body", 30, 1, true, true)
    private val touched = newest.copy(key = "touched", packageName = second.packageName, postedAt = 20)
    private val older = touched.copy(key = "older", postedAt = 10, silent = true)
    private val ongoing = touched.copy(key = "ongoing", postedAt = 5, canDismiss = false)
    private val ready = ShortcutResult(ShortcutStatus.Ready)

    private fun state(vararg selected: PopupGestureAction) = LauncherUiState(
        apps = listOf(first, second), folders = listOf(folder),
        notifications = mapOf(first.packageName to listOf(newest), second.packageName to listOf(touched, older, ongoing)),
        settings = LauncherSettings(shortcutsFolders = ShortcutsFoldersSettings(
            swipeLeft = PopupGestureActions(selected = selected.toSet()))),
    )

    private suspend fun execute(state: LauncherUiState, notification: AppNotification?,
        actions: LauncherActions, open: (LauncherApp) -> Boolean = { error("Unexpected pop-up") },
        launch: (LauncherApp) -> Boolean = { error("Unexpected launch") }): Boolean =
        performPopupGesture(folder.asApp(), PopupGesture.SwipeLeft, notification, { state }, { actions }, { true }, open, launch)

    @Test fun singleNotificationDismissesThatItemAndFolderDismissesNewest() = runBlocking {
        val state = state(PopupGestureAction.DismissFirstNotification)
        val removed = mutableListOf<String>()
        val actions = LauncherActions(dismissNotification = { key, _ -> removed.add(key) })
        assertTrue(execute(state, touched, actions))
        assertEquals(listOf(touched.key), removed)
        removed.clear()
        assertTrue(execute(state, null, actions))
        assertEquals(listOf(newest.key), removed)
    }

    @Test fun dismissAllClearsEveryMemberAppEvenFromOnePreviewInAFolderOrStack() = runBlocking {
        val state = state(PopupGestureAction.DismissAllNotifications)
        val removed = mutableListOf<String>()
        val actions = LauncherActions(dismissNotification = { key, _ -> removed.add(key) })
        assertTrue(execute(state, touched, actions))
        assertEquals(setOf(newest.key, touched.key, older.key), removed.toSet())
        removed.clear()
        assertTrue(execute(state, null, actions))
        assertEquals(setOf(newest.key, touched.key, older.key), removed.toSet())
        val stack = folder.copy(isAppStack = true)
        val stackState = state.copy(folders = listOf(stack))
        val target = requireNotNull(stackState.popupGestureTarget(stack.asApp(), null))
        assertEquals(setOf(newest.key, touched.key, older.key, ongoing.key),
            stackState.allGestureNotifications(target).map { it.key }.toSet())
    }

    @Test fun stalePreviewNeverFallsThroughToAnotherNotificationOrItsFolder() = runBlocking {
        val state = state(PopupGestureAction.DismissFirstNotification, PopupGestureAction.OpenPopup)
        val changed = state.copy(notifications = state.notifications +
            (second.packageName to listOf(touched.copy(revision = 2), older)))
        assertFalse(execute(changed, touched, LauncherActions(dismissNotification = { _, _ -> error("Stale notification") })))
        assertNull(changed.resolvePopupGesture(folder.asApp(), PopupGesture.SwipeLeft, touched) { ready })
    }

    @Test fun dismissAllOnAnAppRowDoesNotClearOtherAppsInItsCustomPopup() = runBlocking {
        val state = state(PopupGestureAction.DismissAllNotifications).copy(popups = mapOf(first.key to listOf(PopupItem(second.key))))
        val removed = mutableListOf<String>()
        assertTrue(performPopupGesture(first, PopupGesture.SwipeLeft, null, { state }, {
            LauncherActions(dismissNotification = { key, _ -> removed.add(key) })
        }, { true }, openPopup = { error("Unexpected pop-up") }, launchItem = { error("Unexpected launch") }))
        assertEquals(listOf(newest.key), removed)
    }

    @Test fun unavailableOrFailedActionsFallThroughToTheTouchedAppsPopup() = runBlocking {
        val state = state(PopupGestureAction.DismissFirstNotification, PopupGestureAction.OpenFirstItem, PopupGestureAction.OpenPopup)
        val queried = mutableListOf<String>()
        val opened = mutableListOf<String>()
        assertTrue(execute(state, touched,
            LauncherActions(dismissNotification = { _, _ -> false }, shortcuts = { queried.add(it.key); ready }),
            open = { opened.add(it.key) }))
        assertEquals(listOf(second.key), queried)
        assertEquals(listOf(second.key), opened)
    }

    @Test fun firstItemFromTouchedPreviewUsesThatAppsPopupAndOpeningUsesThatExactNotification() = runBlocking {
        val itemState = state(PopupGestureAction.OpenFirstItem).copy(popups = mapOf(second.key to listOf(PopupItem(first.key))))
        val launched = mutableListOf<String>()
        assertTrue(execute(itemState, touched, LauncherActions(), launch = { launched.add(it.key) }))
        assertEquals(listOf(first.key), launched)
        val opened = mutableListOf<String>()
        assertTrue(execute(state(PopupGestureAction.OpenFirstNotification), touched,
            LauncherActions(openNotification = { key, _ -> opened.add(key) })))
        assertEquals(listOf(touched.key), opened)
    }

    @Test fun leavingDuringShortcutLookupPreventsDelayedLaunchAndFallbackPopup() = runBlocking {
        var active = true
        val state = state(PopupGestureAction.OpenFirstItem, PopupGestureAction.OpenPopup)
        val shortcut = LauncherShortcut("one", second.packageName, "Shortcut", null)
        assertFalse(performPopupGesture(folder.asApp(), PopupGesture.SwipeLeft, touched, { state }, {
            LauncherActions(shortcuts = { active = false; ShortcutResult(ShortcutStatus.Ready, listOf(shortcut)) })
        }, { active }, openPopup = { error("Navigation already changed") }, launchItem = { error("Late launch") }))
    }
}
