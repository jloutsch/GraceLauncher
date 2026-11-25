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
class DismissNotificationSwipeTest {
    private val a = LauncherApp(ComponentName("fixture.a", "Main"), "A", null)
    private val b = LauncherApp(ComponentName("fixture.b", "Main"), "B", null)
    private val folder = LauncherFolder("f", "Folder", listOf(a.key, b.key), FolderPlacement.Favorites)
    private fun note(app: LauncherApp, key: String) = AppNotification(key, app.packageName, "t", "x", 1L, 1, true, true)

    private fun state(vararg actions: PopupGestureAction) = LauncherUiState(
        apps = listOf(a, b), folders = listOf(folder),
        notifications = mapOf(a.packageName to listOf(note(a, "a1")), b.packageName to listOf(note(b, "b1"))),
        settings = LauncherSettings(shortcutsFolders = ShortcutsFoldersSettings(
            swipeLeft = PopupGestureActions(selected = actions.toSet(), confirmDismissAll = true))),
    )

    private suspend fun swipe(owner: LauncherApp, state: LauncherUiState, removed: MutableList<String>,
        confirm: suspend (List<AppNotification>) -> Boolean): Boolean =
        performPopupGesture(owner, PopupGesture.SwipeLeft, null, { state },
            { LauncherActions(dismissNotification = { key, _ -> removed.add(key) }) }, { true },
            openPopup = { error("Unexpected pop-up") }, launchItem = { error("Unexpected launch") }, confirmDismiss = confirm)

    @Test fun confirmationIsRequiredOnlyForMoreThanOneApp() {
        assertTrue(notificationDismissNeedsConfirmation(listOf(note(a, "a1"), note(b, "b1"))))
        assertFalse(notificationDismissNeedsConfirmation(listOf(note(a, "a1"), note(a, "a2"))))
        assertFalse(notificationDismissNeedsConfirmation(listOf(note(a, "a1"))))
        assertFalse(notificationDismissNeedsConfirmation(emptyList()))
    }

    @Test fun declinedFolderDismissClearsNothingAndDoesNotFallThrough() = runBlocking {
        val removed = mutableListOf<String>()
        var asked = 0
        val base = state(PopupGestureAction.DismissAllNotifications, PopupGestureAction.OpenPopup)
        val order = listOf(PopupGestureAction.DismissAllNotifications, PopupGestureAction.OpenPopup)
        val state = base.copy(settings = base.settings.copy(shortcutsFolders = base.settings.shortcutsFolders.copy(
            swipeLeft = PopupGestureActions(order = order, selected = order.toSet(), confirmDismissAll = true))))
        assertTrue(swipe(folder.asApp(), state, removed) { asked++; false })
        assertEquals(1, asked)
        assertTrue(removed.isEmpty())
    }

    @Test fun confirmedFolderDismissClearsEveryMemberApp() = runBlocking {
        val removed = mutableListOf<String>()
        assertTrue(swipe(folder.asApp(), state(PopupGestureAction.DismissAllNotifications), removed) { true })
        assertEquals(setOf("a1", "b1"), removed.toSet())
    }

    @Test fun singleAppDismissNeverAsks() = runBlocking {
        val removed = mutableListOf<String>()
        assertTrue(swipe(a, state(PopupGestureAction.DismissAllNotifications), removed) { error("Should not ask") })
        assertEquals(listOf("a1"), removed)
    }

    @Test fun twoNotificationsFromOneAppClearWithoutConfirmation() = runBlocking {
        val cleared = mutableListOf<String>()
        var asked = 0
        val two = state(PopupGestureAction.DismissAllNotifications).copy(
            notifications = mapOf(a.packageName to listOf(note(a, "a1"), note(a, "a2"))))
        assertTrue(performPopupGesture(a, PopupGesture.SwipeLeft, null, { two },
            { LauncherActions(dismissAllNotifications = { cleared.add(it) }) }, { true },
            openPopup = { error("Unexpected pop-up") }, launchItem = { error("Unexpected launch") },
            confirmDismiss = { asked++; true }))
        assertEquals(0, asked)
        assertEquals(listOf(a.packageName), cleared)
    }

    @Test fun swipingOneFolderPreviewStillDismissesEveryAppInTheFolder() = runBlocking {
        val removed = mutableListOf<String>()
        val s = state(PopupGestureAction.DismissAllNotifications)
        assertTrue(performPopupGesture(folder.asApp(), PopupGesture.SwipeLeft, note(a, "a1"), { s },
            { LauncherActions(dismissNotification = { key, _ -> removed.add(key) }) }, { true },
            openPopup = { error("Unexpected pop-up") }, launchItem = { error("Unexpected launch") },
            confirmDismiss = { true }))
        assertEquals(setOf("a1", "b1"), removed.toSet())
    }
}
