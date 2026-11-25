package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.resolvePopupGesture
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.FolderRow
import com.galaxyrio.gracelauncher.ui.components.LocalPopupGestureSettings
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PopupNotificationGesturesTest {
    @get:Rule val compose = createComposeRule()
    private val first = LauncherApp(ComponentName("fixture.first", "Activity"), "First", null)
    private val second = LauncherApp(ComponentName("fixture.second", "Activity"), "Second", null)
    private val folder = LauncherFolder("notifications", "Folder", listOf(first.key, second.key), FolderPlacement.Favorites)
    private var density = 1f
    private var slop = 0f
    private val gestures = mutableListOf<Pair<PopupGesture, String?>>()
    private val popupApps = mutableListOf<String>()
    private var folderReveals = 0

    private fun showFolder(canDismiss: Boolean = true) {
        val notifications = listOf(first, second).mapIndexed { index, app ->
            AppNotification("notice$index", app.packageName, "Test", "A notification body", 10L - index, 1, canDismiss, true)
        }
        val settings = ShortcutsFoldersSettings(multipleAppNotificationPreviews = true,
            swipeLeft = PopupGestureActions(selected = setOf(PopupGestureAction.DismissFirstNotification, PopupGestureAction.OpenFirstItem)),
            doubleTap = PopupGestureActions(selected = setOf(PopupGestureAction.DismissFirstNotification)))
        val state = LauncherUiState(apps = listOf(first, second), folders = listOf(folder),
            notifications = notifications.groupBy(AppNotification::packageName), settings = LauncherSettings(shortcutsFolders = settings))
        compose.setContent {
            density = LocalDensity.current.density
            slop = LocalViewConfiguration.current.touchSlop
            GraceLauncherTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalPopupGestureSettings provides settings) {
                    Box(Modifier.fillMaxSize().padding(top = 48.dp)) {
                        FolderRow(folder, {}, null, notifications = notifications,
                            onDrag = { _, expanded -> if (expanded) folderReveals++ },
                            gestures = AppRowGestures(
                                resolveGesture = { owner, gesture, notification ->
                                    state.resolvePopupGesture(owner, gesture, notification) { ShortcutResult(ShortcutStatus.Ready) }
                                },
                                onGesture = { _, gesture, _, notification -> gestures += gesture to notification?.key },
                                onNotificationDrag = { _, notification, _, expanded -> if (expanded) popupApps += notification.packageName },
                            ))
                    }
                }
            }
        }
    }

    @Test fun secondNotificationMovesIndependentlyAndOnlyReceivesItsOwnSwipe() {
        showFolder()
        val row = compose.onNodeWithTag("folder:${folder.id}:surface", useUnmergedTree = true)
        val label = compose.onNodeWithTag("folder:${folder.id}:label", useUnmergedTree = true)
        val body = compose.onNodeWithTag("notification_preview:notice1:body", useUnmergedTree = true)
        val rowStart = row.fetchSemanticsNode().boundsInRoot
        val labelStart = label.fetchSemanticsNode().boundsInRoot
        val bodyStart = body.fetchSemanticsNode().boundsInRoot
        val notification = compose.onNodeWithTag("notification_gesture:notice1", useUnmergedTree = true)
        notification.performTouchInput { down(center); moveBy(Offset(-slop - 60f * density, 0f)) }
        assertEquals(rowStart, row.fetchSemanticsNode().boundsInRoot)
        assertEquals(labelStart, label.fetchSemanticsNode().boundsInRoot)
        assertTrue(body.fetchSemanticsNode().boundsInRoot.left < bodyStart.left)
        notification.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(PopupGesture.SwipeLeft to "notice1"), gestures) }
    }

    @Test fun notificationOpensItsOwnAppWhileHeaderStillOpensFolder() {
        showFolder()
        compose.onNodeWithTag("notification_gesture:notice1", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(slop + 24f * density, 0f), 250)
        }
        compose.runOnIdle { assertEquals(listOf(second.packageName), popupApps); assertEquals(0, folderReveals) }
        compose.onNodeWithTag("folder:${folder.id}:label", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(slop + 24f * density, 0f), 250)
        }
        compose.runOnIdle { assertEquals(1, folderReveals) }
    }

    @Test fun unavailableNotificationActionDoesNotFallBackToItsParentFoldersGesture() {
        showFolder(canDismiss = false)
        compose.onNodeWithTag("notification_gesture:notice1", useUnmergedTree = true).performTouchInput {
            swipe(center, center - Offset(slop + 60f * density, 0f), 250)
        }
        compose.runOnIdle { assertTrue(gestures.isEmpty()) }
        compose.onNodeWithTag("folder:${folder.id}:label", useUnmergedTree = true).performTouchInput {
            swipe(center, center - Offset(slop + 60f * density, 0f), 250)
        }
        compose.runOnIdle { assertEquals(listOf(PopupGesture.SwipeLeft to null), gestures) }
    }

    @Test fun doubleTapTargetsOnlyTheTappedNotification() {
        showFolder()
        compose.onNodeWithTag("notification_gesture:notice1", useUnmergedTree = true).performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(listOf(PopupGesture.DoubleTap to "notice1"), gestures) }
    }
}
