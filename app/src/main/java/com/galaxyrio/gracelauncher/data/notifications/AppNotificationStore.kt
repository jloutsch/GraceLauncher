package com.galaxyrio.gracelauncher.data.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.KeyguardManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Process
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import com.galaxyrio.gracelauncher.data.CategoryNotificationLevel
import com.galaxyrio.gracelauncher.data.media.mediaPlayerLaunchOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ephemeral display data. Notification text, icons and intents are never persisted or logged. */
data class AppNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val revision: Long,
    val canDismiss: Boolean,
    val canOpen: Boolean,
    val icon: Icon? = null,
    val silent: Boolean = false,
    val originalTitle: String = title,
    val originalText: String = text,
    val actions: List<AppNotificationAction> = emptyList(),
    val suggestedReplies: List<NotificationSuggestedReply> = emptyList(),
    val progress: NotificationProgress? = null,
)

/** Both projections are prepared once when the notification arrives; toggling needs no system query. */
internal fun AppNotification.forDisplay(reduceRepeatedSenders: Boolean): AppNotification =
    if (reduceRepeatedSenders) this else copy(title = originalTitle, text = originalText)

internal fun formatNotificationMessages(messages: List<Pair<String, String>>, reduceRepeatedSenders: Boolean): String {
    var previous = ""
    return messages.filter { it.second.isNotBlank() }.joinToString("\n") { (sender, text) ->
        (if (sender.isEmpty() || (reduceRepeatedSenders && sender == previous)) text else "$sender: $text")
            .also { previous = sender }
    }
}

/** True when the body already names the sender (MessagingStyle prefixes "Sender: "), so the title repeats it. */
internal fun isTitleRedundant(title: String, text: String): Boolean =
    title.isNotBlank() && (text == title || text.startsWith("$title: "))

/** For "Channel: Sender" titles, keep the channel and drop the sender the body already shows. */
internal fun stripSenderFromTitle(title: String, text: String): String {
    val sender = title.substringAfterLast(": ", "")
    return if (sender.isNotBlank() && text.startsWith("$sender: ")) title.removeSuffix(": $sender") else title
}

/** Notification presence is profile-specific and does not retain any notification content. */
data class NotificationAppId(val packageName: String, val user: UserHandle)

internal data class AppNotificationRanking(val visible: Boolean = true, val silent: Boolean = false,
    val importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    val smartActions: List<Notification.Action> = emptyList(),
    val smartReplies: List<String> = emptyList()) {
    val categoryLevel: CategoryNotificationLevel get() = when {
        importance in NotificationManager.IMPORTANCE_MIN..NotificationManager.IMPORTANCE_LOW -> CategoryNotificationLevel.Silent
        importance >= NotificationManager.IMPORTANCE_HIGH -> CategoryNotificationLevel.High
        else -> CategoryNotificationLevel.Normal
    }
}

@MainThread
internal class AppNotificationStore {
    private data class Entry(
        val display: AppNotification,
        val groupKey: String,
        val summary: Boolean,
        val contentIntent: PendingIntent?,
        val autoCancel: Boolean,
        val actions: List<Notification.Action>,
        val allowSystemActions: Boolean,
        val interactions: NotificationInteractions = NotificationInteractions(emptyList(), emptyList()),
    )
    private val entries = linkedMapOf<String, Entry>()
    private val categoryEntries = linkedMapOf<String, NotificationAppId>()
    private var cancel: ((String) -> Boolean)? = null
    private var classification: (String) -> AppNotificationRanking = { AppNotificationRanking() }
    private var revision = 0L
    private var context: Context? = null
    private val _state = MutableStateFlow<Map<String, List<AppNotification>>>(emptyMap())
    val state = _state.asStateFlow()
    private val _categoryState = MutableStateFlow<Map<NotificationAppId, Set<CategoryNotificationLevel>>>(emptyMap())
    val categoryState = _categoryState.asStateFlow()

    fun connected(notifications: List<StatusBarNotification>,
        ranking: (String) -> AppNotificationRanking = { AppNotificationRanking() },
        context: Context? = null, cancelNotification: (String) -> Boolean) {
        entries.clear(); categoryEntries.clear()
        this.context = context?.applicationContext
        cancel = cancelNotification
        classification = ranking
        notifications.forEach { sbn ->
            categoryEntries[sbn.key] = NotificationAppId(sbn.packageName, sbn.user)
            parse(sbn)?.let { entries[sbn.key] = it }
        }
        publish()
    }

    fun posted(sbn: StatusBarNotification, ranking: (String) -> AppNotificationRanking = classification) {
        if (cancel == null) return
        classification = ranking
        categoryEntries[sbn.key] = NotificationAppId(sbn.packageName, sbn.user)
        val entry = parse(sbn)
        if (entry == null) entries.remove(sbn.key) else entries[sbn.key] = entry
        publish()
    }

    fun removed(key: String) {
        val removedDisplay = entries.remove(key) != null
        val removedCategory = categoryEntries.remove(key) != null
        if (removedDisplay || removedCategory) publish()
    }

    fun ranking(ranking: (String) -> AppNotificationRanking) { classification = ranking; publish() }

    fun disconnected() {
        cancel = null; classification = { AppNotificationRanking() }
        context = null
        entries.clear(); categoryEntries.clear()
        publish()
    }

    fun dismiss(key: String, revision: Long): Boolean {
        val entry = entries[key]?.takeIf { it.display.revision == revision && it.display.canDismiss && classification(key).visible } ?: return false
        // Wait for onNotificationRemoved; the system owns dismissal, including deleteIntent.
        // In particular, do not cancel an app-wide group when swiping just one child.
        return cancel?.invoke(entry.display.key) == true
    }

    /** Clears every dismissable notification of the app, including group summaries that the list hides. */
    fun dismissAll(packageName: String): Boolean {
        val keys = entries.values.filter {
            it.display.packageName == packageName && it.display.canDismiss && classification(it.display.key).visible
        }.map { it.display.key }
        // Cancel every key even if one fails; any success counts as handled.
        return keys.map { cancel?.invoke(it) == true }.any { it }
    }

    fun open(context: Context, key: String, revision: Long): Boolean {
        val entry = entries[key]?.takeIf { it.display.revision == revision && classification(key).visible } ?: return false
        val intent = entry.contentIntent ?: return false
        return runCatching {
            intent.send(context, 0, null, null, null, null, mediaPlayerLaunchOptions().toBundle())
            if (entry.autoCancel && entry.display.canDismiss) dismiss(key, revision)
        }.isSuccess
    }

    fun performAction(context: Context, key: String, revision: Long, actionId: String,
        reply: String? = null, fromChoice: Boolean = false): NotificationActionResult {
        val entry = entries[key]?.takeIf { it.display.revision == revision && classification(key).visible }
            ?: return NotificationActionResult.Unavailable
        val binding = entry.interactions.bindings.firstOrNull { it.display.id == actionId }
            ?: return NotificationActionResult.Unavailable
        val input = binding.display.reply
        if (input != null && reply == null || input == null && reply != null) return NotificationActionResult.Unavailable
        if (reply != null) {
            if (reply.isBlank() || reply.length > 4096) return NotificationActionResult.Unavailable
            val choice = entry.display.suggestedReplies.any { it.actionId == actionId && it.text == reply }
            if ((!input!!.allowFreeForm || fromChoice) && !choice) return NotificationActionResult.Unavailable
        }
        if ((binding.display.requiresAuthentication || reply != null) &&
            context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true) {
            return NotificationActionResult.UnlockRequired
        }
        return runCatching {
            val fillIn = reply?.let { text ->
                Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND).also { intent ->
                    RemoteInput.addResultsToIntent(binding.inputs.toTypedArray(), intent,
                        Bundle().apply { putCharSequence(input!!.resultKey, text) })
                    RemoteInput.setResultsSource(intent,
                        if (fromChoice) RemoteInput.SOURCE_CHOICE else RemoteInput.SOURCE_FREE_FORM_INPUT)
                }
            }
            binding.intent.send(context, 0, fillIn, null, null, null, mediaPlayerLaunchOptions().toBundle())
            // The posting app decides when to update/remove its notification after an action.
            NotificationActionResult.Sent
        }.getOrDefault(NotificationActionResult.Unavailable)
    }

    private fun publish() {
        // Both projections use the same ranking snapshot. Category membership deliberately
        // includes media/blank/profile notifications that are unsuitable as text previews.
        val rankings = categoryEntries.keys.associateWith(classification)
        val categories = linkedMapOf<NotificationAppId, MutableSet<CategoryNotificationLevel>>()
        categoryEntries.forEach { (key, app) ->
            val ranking = rankings.getValue(key)
            if (ranking.visible) categories.getOrPut(app) { linkedSetOf() }.add(ranking.categoryLevel)
        }
        val eligible = entries.values.mapNotNull { entry ->
            val ranking = rankings.getValue(entry.display.key)
            if (!ranking.visible) null else {
                val interactions = notificationInteractions(entry.actions, ranking, entry.allowSystemActions, entry.interactions)
                val updated = entry.copy(interactions = interactions, display = entry.display.copy(
                    silent = ranking.silent,
                    revision = if (entry.interactions.sameTargets(interactions)) entry.display.revision else ++revision,
                    actions = interactions.bindings.map { it.display }, suggestedReplies = interactions.suggestions,
                ))
                entries[entry.display.key] = updated
                updated
            }
        }
        val groupsWithChildren = eligible.filterNot { it.summary }.map { it.display.packageName to it.groupKey }.toSet()
        _state.value = eligible.filterNot { it.summary && (it.display.packageName to it.groupKey) in groupsWithChildren }
            .map { it.display }.sortedByDescending { it.postedAt }.groupBy { it.packageName }
        _categoryState.value = categories.mapValues { (_, levels) -> levels.toSet() }
    }

    private fun parse(sbn: StatusBarNotification): Entry? = runCatching {
        // Text previews remain personal-profile-only. Never put work/private notification
        // content onto a same-package personal app row.
        if (sbn.user != Process.myUserHandle()) return null
        val notification = sbn.notification
        val extras = notification.extras ?: return null
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION) || notification.visibility == Notification.VISIBILITY_SECRET) return null
        val messaging = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val messages = messaging?.messages.orEmpty().takeLast(8)
        val messageText = messages.map { it.person?.name?.toString()?.trim().orEmpty() to it.text?.toString().orEmpty() }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val selfName = messaging?.user?.name?.toString()?.trim().orEmpty()
        fun senderOf(message: NotificationCompat.MessagingStyle.Message) = message.person?.name?.toString()?.trim().orEmpty()
        val otherSenders = messages.map(::senderOf).filter { it.isNotEmpty() && it != selfName }.toSet()
        val body = when {
            // Skip attachment-only messages (blank text) and repeat the sender only when it changes.
            // Fork: in a one-to-one chat the title already names the sender, so the lines leave it out.
            messages.isNotEmpty() -> formatNotificationMessages(messages.map { message ->
                val sender = senderOf(message)
                (if (titleNamesSender(sender, title, otherSenders)) "" else sender) to message.text?.toString().orEmpty()
            }, reduceRepeatedSenders = true)
            !extras.getCharSequence(Notification.EXTRA_BIG_TEXT).isNullOrBlank() -> extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
            !extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).isNullOrEmpty() -> extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.joinToString("\n")
            else -> extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        }.trim()
        val progress = notificationProgress(notification, context)
        if (title.isBlank() && body.isBlank() && progress == null) return null
        Entry(
            display = AppNotification(sbn.key, sbn.packageName,
                stripSenderFromTitle(title, body).take(256), body.take(8192),
                sbn.postTime, ++revision, sbn.isClearable, notification.contentIntent != null,
                notification.getLargeIcon(), originalTitle = title.take(256),
                originalText = (if (messages.isNotEmpty()) formatNotificationMessages(messageText, reduceRepeatedSenders = false)
                    else body).trim().take(8192), progress = progress),
            groupKey = sbn.groupKey,
            summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            contentIntent = notification.contentIntent,
            autoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
            actions = notification.actions.orEmpty().toList(),
            allowSystemActions = Build.VERSION.SDK_INT >= 29 && notification.allowSystemGeneratedContextualActions,
        )
    }.getOrNull()
}

internal val appNotifications = AppNotificationStore()

/**
 * A one-to-one chat's title is already the other person's name, so their lines show only the message.
 * An untitled group is titled with its latest sender, so names stay when anyone else (besides the user) wrote.
 */
internal fun messageLine(sender: String, text: String, title: String, otherSenders: Set<String>): String =
    if (sender.isEmpty() || titleNamesSender(sender, title, otherSenders)) text else "$sender: $text"

/** A one-to-one chat: its only other sender is the person the title names. */
internal fun titleNamesSender(sender: String, title: String, otherSenders: Set<String>): Boolean =
    sender == title && otherSenders == setOf(title)
