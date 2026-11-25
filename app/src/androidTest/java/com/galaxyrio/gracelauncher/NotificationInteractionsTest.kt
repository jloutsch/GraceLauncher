package com.galaxyrio.gracelauncher

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.ShortcutsFoldersSettings
import com.galaxyrio.gracelauncher.data.notifications.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic notifications and private broadcasts only; no user's notifications are posted or sent. */
@RunWith(AndroidJUnit4::class)
class NotificationInteractionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun builder() = Notification.Builder(context, "fixture").setSmallIcon(R.drawable.ms_info)
        .setContentTitle("Ada").setContentText("See you soon")
    private fun intent(id: Int) = PendingIntent.getBroadcast(context, id,
        Intent("${context.packageName}.NOTIFICATION_FIXTURE").setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    private fun reply(intent: PendingIntent, choices: Array<String> = emptyArray(), generated: Boolean = true) =
        Notification.Action.Builder(null as Icon?, "Reply", intent)
            .addRemoteInput(RemoteInput.Builder("message").setLabel("Reply to Ada").setChoices(choices).build())
            .setAllowGeneratedReplies(generated).build()
    @Suppress("DEPRECATION")
    private fun sbn(notification: Notification) = StatusBarNotification(context.packageName, context.packageName,
        42, null, Process.myUid(), 0, 0, notification, Process.myUserHandle(), 1234)
    private fun AppNotificationStore.current() = state.value.values.flatten().single()

    @Test fun replyUsesOriginalPendingIntentAndCorrectRemoteInputResultKey() {
        val latch = CountDownLatch(1)
        var text: String? = null
        var source = -1
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence("message")?.toString()
                source = RemoteInput.getResultsSource(intent)
                latch.countDown()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter("${context.packageName}.NOTIFICATION_FIXTURE"), ContextCompat.RECEIVER_NOT_EXPORTED)
        val pending = intent(1)
        val store = AppNotificationStore()
        try {
            main {
                store.connected(listOf(sbn(builder().addAction(reply(pending)).build())), context = context) { false }
                val notification = store.current()
                assertEquals(NotificationActionResult.Sent, store.performAction(context, notification.key, notification.revision,
                    notification.actions.single().id, "On my way\nSee you soon"))
            }
            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertEquals("On my way\nSee you soon", text)
            assertEquals(RemoteInput.SOURCE_FREE_FORM_INPUT, source)
        } finally { context.unregisterReceiver(receiver); pending.cancel(); main { store.disconnected() } }
    }

    @Test fun suggestionsPreferAppChoicesAndRejectUnadvertisedChoices() = main {
        val pending = intent(2)
        try {
            val store = AppNotificationStore()
            store.connected(listOf(sbn(builder().addAction(reply(pending, arrayOf("Yes", "No"))).build())),
                ranking = { AppNotificationRanking(smartReplies = listOf("System suggestion")) }) { false }
            val n = store.current()
            assertEquals(listOf("Yes", "No"), n.suggestedReplies.map { it.text })
            assertEquals(NotificationActionResult.Unavailable, store.performAction(context, n.key, n.revision,
                n.actions.single().id, "Invented choice", fromChoice = true))
            assertEquals(NotificationActionResult.Unavailable, store.performAction(context, n.key, n.revision, n.actions.single().id))
            store.disconnected()
        } finally { pending.cancel() }
    }

    @Test fun rankingUpdatesExposeSuggestionsAndInvalidateRemovedActionsWithoutReposting() = main {
        val pending = intent(3)
        val smart = intent(4)
        try {
            val store = AppNotificationStore()
            val notification = builder().addAction(reply(pending)).build()
            store.connected(listOf(sbn(notification))) { false }
            val before = store.current()
            val generated = Notification.Action.Builder(null as Icon?, "Open map", smart).build()
            val ranking = AppNotificationRanking(smartReplies = listOf("On my way"), smartActions = listOf(generated))
            store.ranking { ranking }
            val after = store.current()
            assertEquals("On my way", after.suggestedReplies.single().text)
            assertEquals(2, after.actions.size)
            assertNotEquals(before.revision, after.revision)
            store.ranking { ranking.copy() }
            assertEquals(after.revision, store.current().revision)
            store.ranking { AppNotificationRanking() }
            assertEquals(NotificationActionResult.Unavailable, store.performAction(context, after.key, after.revision,
                after.actions.last().id))
            store.disconnected()
        } finally { pending.cancel(); smart.cancel() }
    }

    @Test fun generatedReplyOptOutAndCancelledOrUpdatedTargetsAreRespected() = main {
        val pending = intent(5)
        try {
            val store = AppNotificationStore()
            store.connected(listOf(sbn(builder().addAction(reply(pending, generated = false)).build())),
                ranking = { AppNotificationRanking(smartReplies = listOf("Yes")) }) { false }
            val original = store.current()
            assertTrue(original.suggestedReplies.isEmpty())
            store.posted(sbn(builder().addAction(reply(pending)).setContentText("New message").build()))
            assertEquals(NotificationActionResult.Unavailable, store.performAction(context, original.key, original.revision,
                original.actions.single().id, "Old reply"))
            pending.cancel()
            val current = store.current()
            assertEquals(NotificationActionResult.Unavailable, store.performAction(context, current.key, current.revision,
                current.actions.single().id, "Current reply"))
            store.disconnected()
        } finally { pending.cancel() }
    }

    @Test fun extractsOrdinaryDeterminateAndIndeterminateProgress() {
        val fixed = notificationProgress(builder().setProgress(100, 64, false).build(), context)!!
        assertEquals(.64f, fixed.fraction, .0001f)
        assertFalse(fixed.indeterminate)
        val waiting = notificationProgress(builder().setProgress(0, 0, true).build(), context)!!
        assertTrue(waiting.indeterminate)
        assertNull(notificationProgress(builder().build(), context))
    }

    @Test fun extractsProgressStyleSegmentsPointsAndTrackerWithoutChangingNotification() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle().setProgress(65).setStyledByProgress(false)
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(60).setColor(0xff228866.toInt()),
                    Notification.ProgressStyle.Segment(40).setColor(0xffffaa44.toInt())))
                .addProgressPoint(Notification.ProgressStyle.Point(80).setColor(0xffee5522.toInt()))
                .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ms_send))
            val notification = builder().setStyle(style).build()
            val progress = notificationProgress(notification, context)!!
            assertTrue(progress.styled)
            assertFalse(progress.styledByProgress)
            assertEquals(100, progress.max)
            assertEquals(65, progress.value)
            assertEquals(listOf(60, 40), progress.segments.map { it.length })
            assertEquals(80, progress.points.single().position)
            assertNotNull(progress.trackerIcon)
            assertEquals("Ada", notification.extras.getString(Notification.EXTRA_TITLE))
        }
    }

    @Test fun newSettingsRoundTripIndependentlyAndOlderSettingsGetDefaults() {
        val defaults = ShortcutsFoldersSettings.decode("{}")
        assertTrue(defaults.notificationActions && defaults.notificationDirectReply && defaults.notificationSuggestedReplies && defaults.notificationProgress)
        assertFalse(defaults.widerNotificationDetails)
        val edited = defaults.copy(notificationActions = false, notificationDirectReply = false,
            notificationSuggestedReplies = false, notificationProgress = false, widerNotificationDetails = true)
        assertEquals(edited, ShortcutsFoldersSettings.decode(edited.encode()))
        assertTrue(defaults.copy(notificationActions = false).notificationDirectReply)
    }
}
