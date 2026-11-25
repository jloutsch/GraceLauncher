package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.notifications.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LocalPopupGestureSettings
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutPopup
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutRevealState
import com.galaxyrio.gracelauncher.ui.settings.ShortcutsFoldersSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real pop-up composables, with in-memory fixtures and inert callbacks. */
@RunWith(AndroidJUnit4::class)
class NotificationPopupFeaturesTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var settings by mutableStateOf(ShortcutsFoldersSettings())
    private val sent = mutableListOf<Triple<String, String?, Boolean>>()
    private var opened = 0
    private var dismissed = 0
    private val avatar = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xffd7a47a.toInt(); canvas.drawColor(paint.color)
        paint.color = 0xff715047.toInt(); canvas.drawCircle(60f, 48f, 27f, paint)
        paint.color = 0xffffddba.toInt(); canvas.drawCircle(60f, 53f, 20f, paint)
        paint.color = 0xff486c6b.toInt(); canvas.drawOval(23f, 77f, 97f, 144f, paint)
        paint.color = 0xff715047.toInt(); canvas.drawArc(34f, 21f, 87f, 62f, 180f, 180f, true, paint)
    }
    private val app = LauncherApp(ComponentName("fixture.chat", "Messages"), "Messages", avatar.asImageBitmap())
    private val reply = AppNotificationAction("reply", "Reply", NotificationReplyInput("message", "Type a reply…", true, emptyList(), false))
    private val message = AppNotification("chat", app.packageName, "Ada", "Sounds good. See you at 6!\nI'll wait by the station.",
        System.currentTimeMillis() - 120_000, 1, true, true, Icon.createWithBitmap(avatar),
        actions = listOf(AppNotificationAction("read", "Mark as read"), reply),
        suggestedReplies = listOf(NotificationSuggestedReply("reply", "Great!"), NotificationSuggestedReply("reply", "On my way")))

    private fun show(notifications: List<AppNotification> = listOf(message), fixtureApp: LauncherApp = app) {
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false, seedColor = Color(0xff88542c)) {
                CompositionLocalProvider(LocalPopupGestureSettings provides settings) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xff254846), Color(0xff75958b))))) {
                        val density = LocalDensity.current
                        val anchor = with(density) { Rect(24.dp.toPx(), 240.dp.toPx(), 72.dp.toPx(), 288.dp.toPx()) }
                        ShortcutPopup(fixtureApp, anchor, true,
                            LauncherActions(
                                shortcuts = { ShortcutResult(ShortcutStatus.Ready) },
                                cachedShortcuts = { ShortcutResult(ShortcutStatus.Ready) },
                                openNotification = { _, _ -> opened++; true },
                                dismissNotification = { _, _ -> dismissed++; true },
                                performNotificationAction = { _, _, action, text, choice ->
                                    sent += Triple(action, text, choice); NotificationActionResult.Sent
                                },
                            ), onLaunchApp = {}, onDismiss = {}, reveal = remember { ShortcutRevealState(animationsEnabled = false) },
                            notifications = notifications,
                            uiState = LauncherUiState(apps = listOf(fixtureApp), settings = LauncherSettings(shortcutsFolders = settings)))
                    }
                }
            }
        }
    }

    @Test fun alignedBodyActionsSuggestionsAndReplyWithKeyboard() {
        show()
        val title = compose.onNodeWithTag("notification_header:chat", true).fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("notification_body:chat", true).fetchSemanticsNode().boundsInRoot
        assertEquals(title.left, body.left, 1f)
        compose.onNodeWithTag("notification_action:chat:read").assertIsDisplayed()
        compose.onNodeWithTag("notification_suggestion:chat:Great!").assertIsDisplayed()
        capture("01-actions-and-suggestions.png")
        compose.onNodeWithTag("notification_action:chat:reply").performClick()
        compose.onNodeWithTag("notification_reply:chat").performTextInput("See you soon!")
        compose.onNodeWithTag("notification_send:chat").assertIsDisplayed().assertIsEnabled()
        capture("02-direct-reply.png")
        compose.onNodeWithTag("notification_send:chat").performClick()
        compose.runOnIdle {
            assertEquals(listOf(Triple("reply", "See you soon!", false)), sent)
            assertEquals(0, opened); assertEquals(0, dismissed)
        }
        compose.onNodeWithTag("notification_feedback:chat").assertIsDisplayed()
    }

    @Test fun eachSwitchOnlyHidesItsOwnFeatureAndWiderDetailsRestoresOriginalAlignment() {
        show(listOf(message.copy(progress = NotificationProgress(64, 100))))
        compose.runOnIdle { settings = settings.copy(notificationActions = false) }
        compose.onNodeWithTag("notification_action:chat:read").assertDoesNotExist()
        compose.onNodeWithTag("notification_action:chat:reply").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(notificationDirectReply = false) }
        compose.onNodeWithTag("notification_action:chat:reply").assertDoesNotExist()
        compose.onNodeWithTag("notification_suggestion:chat:Great!").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Triple("reply", "Great!", true), sent.single()) }
        compose.runOnIdle { settings = settings.copy(notificationSuggestedReplies = false) }
        compose.onNodeWithTag("notification_suggestion:chat:Great!").assertDoesNotExist()
        compose.onNodeWithTag("notification_progress").assertIsDisplayed()
        compose.runOnIdle { settings = settings.copy(notificationProgress = false, widerNotificationDetails = true) }
        compose.onNodeWithTag("notification_progress").assertDoesNotExist()
        val title = compose.onNodeWithTag("notification_header:chat", true).fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("notification_body:chat", true).fetchSemanticsNode().boundsInRoot
        assertTrue(body.left < title.left)
        capture("05-wider-details.png")
    }

    @Test fun progressStyleAndOrdinaryProgressAreShownInsidePopup() {
        val navigation = message.copy(key = "route", title = "Arrive in 4 min · 1.2 km",
            text = "Turn right onto Park Avenue.\nYour destination is on the left.", suggestedReplies = emptyList(),
            actions = listOf(AppNotificationAction("exit", "Exit navigation")),
            progress = NotificationProgress(62, 100, styled = true,
                segments = listOf(NotificationProgressSegment(45, 0xff397a68.toInt()),
                    NotificationProgressSegment(25, 0xffc58b31.toInt()), NotificationProgressSegment(30, 0xff397a68.toInt())),
                points = listOf(NotificationProgressPoint(45), NotificationProgressPoint(95)),
                trackerIcon = Icon.createWithResource(context, R.drawable.ms_send)))
        val download = message.copy(key = "download", title = "Downloading offline map", text = "64 MB of 100 MB",
            suggestedReplies = emptyList(), actions = listOf(AppNotificationAction("pause", "Pause"), AppNotificationAction("cancel", "Cancel")),
            progress = NotificationProgress(64, 100))
        show(listOf(navigation, download))
        compose.onAllNodesWithTag("notification_progress").assertCountEquals(2)
        capture("03-progress-notifications.png")
    }

    @Test fun emphasizedButtonsAndIndeterminateProgress() {
        // Compose's automatic test clock cancels infinite animations; drive this scene explicitly.
        compose.mainClock.autoAdvance = false
        val timer = message.copy(key = "timer", title = "Timer", text = "12 minutes remaining", suggestedReplies = emptyList(),
            actions = listOf(AppNotificationAction("add", "+ 5 min", emphasized = true), AppNotificationAction("stop", "Stop", emphasized = true)))
        val sync = message.copy(key = "sync", title = "Preparing backup", text = "Collecting your files…", suggestedReplies = emptyList(),
            actions = listOf(AppNotificationAction("cancel", "Cancel")), progress = NotificationProgress(0, 1, indeterminate = true))
        show(listOf(timer, sync))
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("notification_action:timer:add").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Triple("add", null, false), sent.single()); assertEquals(0, opened) }
        compose.mainClock.advanceTimeBy(500)
        capture("04-actions-and-indeterminate-progress.png")
    }

    @Test fun independentFeatureSettingsFollowNormalNotificationControl() {
        var state by mutableStateOf(LauncherUiState(settings = LauncherSettings(shortcutsFolders = settings)))
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false, seedColor = Color(0xff88542c)) {
                ShortcutsFoldersSettingsScreen(state, LauncherActions(updateSettings = { update -> state = state.copy(settings = update(state.settings)) }), {})
            }
        }
        compose.onNodeWithTag("popup_notification_progress").performScrollTo().assertIsDisplayed()
        capture("06-notification-settings.png")
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("notificationScreenshots") != "true") return
        compose.waitForIdle()
        val directory = requireNotNull(context.getExternalFilesDir("notification-previews")).also { it.mkdirs() }
        File(directory, name).outputStream().use {
            val node = if (name.startsWith("06")) compose.onRoot() else compose.onNodeWithTag("shortcut_popup")
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
