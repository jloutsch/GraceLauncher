package com.galaxyrio.gracelauncher.data.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Build

/** Display metadata only; the store owns and validates the corresponding PendingIntent. */
data class AppNotificationAction(
    val id: String,
    val title: String,
    val reply: NotificationReplyInput? = null,
    val opensActivity: Boolean = false,
    val requiresAuthentication: Boolean = false,
    val emphasized: Boolean = false,
)

data class NotificationReplyInput(
    val resultKey: String,
    val label: String,
    val allowFreeForm: Boolean,
    val choices: List<String>,
    val editChoicesBeforeSending: Boolean,
)

data class NotificationSuggestedReply(val actionId: String, val text: String, val editBeforeSending: Boolean = false)

enum class NotificationActionResult { Sent, Unavailable, UnlockRequired }

internal data class NotificationActionBinding(
    val display: AppNotificationAction,
    val intent: PendingIntent,
    val inputs: List<RemoteInput>,
) {
    // RemoteInput has identity equality; ranking callbacks can parcel an equivalent instance again.
    fun sameTarget(other: NotificationActionBinding): Boolean = display == other.display && intent == other.intent
}

internal data class NotificationInteractions(
    val bindings: List<NotificationActionBinding>,
    val suggestions: List<NotificationSuggestedReply>,
) {
    fun sameTargets(other: NotificationInteractions): Boolean = suggestions == other.suggestions &&
        bindings.size == other.bindings.size && bindings.zip(other.bindings).all { (a, b) -> a.sameTarget(b) }
}

internal fun notificationInteractions(
    actions: List<Notification.Action>,
    ranking: AppNotificationRanking,
    allowSystemActions: Boolean,
    previous: NotificationInteractions? = null,
): NotificationInteractions {
    fun bind(action: Notification.Action, id: String): NotificationActionBinding? {
        val intent = action.actionIntent ?: return null
        // PendingIntent.isActivity queries the system. Reuse its immutable type across ranking updates.
        val opensActivity = previous?.bindings?.firstOrNull { it.intent == intent }?.display?.opensActivity ?: intent.isActivity
        val title = action.title?.toString()?.take(256)?.takeIf { it.isNotBlank() } ?: return null
        val inputs = action.remoteInputs.orEmpty().toList()
        val input = inputs.firstOrNull { it.allowFreeFormInput }
            ?: inputs.firstOrNull { !it.choices.isNullOrEmpty() }
        // Do not execute an attachment-only action as if it were an ordinary button.
        if (input == null && !action.dataOnlyRemoteInputs.isNullOrEmpty()) return null
        return NotificationActionBinding(
            AppNotificationAction("$id:${intent.hashCode()}:${input?.resultKey.orEmpty()}", title, input?.let {
                NotificationReplyInput(it.resultKey, it.label?.toString().orEmpty().take(256),
                    it.allowFreeFormInput, it.choices.orEmpty().map(CharSequence::toString).filter(String::isNotBlank).distinct().take(8),
                    Build.VERSION.SDK_INT >= 29 && it.editChoicesBeforeSending == RemoteInput.EDIT_CHOICES_BEFORE_SENDING_ENABLED)
            }, opensActivity, Build.VERSION.SDK_INT >= 31 && action.isAuthenticationRequired,
                Build.VERSION.SDK_INT >= 37 && action.emphasisHint == Notification.Action.EMPHASIS_PRIMARY),
            intent, inputs,
        )
    }
    val bindings = actions.take(16).mapIndexedNotNull { index, action -> bind(action, "app:$index") }
    val appSuggestions = bindings.flatMap { binding ->
        binding.display.reply?.let { input ->
            input.choices.map { NotificationSuggestedReply(binding.display.id, it, input.editChoicesBeforeSending) }
        }.orEmpty()
    }.distinctBy { it.text }.take(8)
    val hasAppContextualActions = Build.VERSION.SDK_INT >= 29 && actions.any { it.isContextual }
    // Match SystemUI's precedence: app suggestions/contextual actions take priority over the assistant.
    val useAssistant = appSuggestions.isEmpty() && !hasAppContextualActions
    val generatedActions = if (useAssistant && allowSystemActions) ranking.smartActions.take(8)
        .mapIndexedNotNull { index, action -> bind(action, "smart:$index") }
        .filter { generated -> bindings.none { it.intent == generated.intent && it.display.title == generated.display.title } }
        else emptyList()
    val replyTarget = bindings.firstOrNull { binding ->
        binding.display.reply?.allowFreeForm == true &&
            actions.any { it.actionIntent == binding.intent && it.allowGeneratedReplies }
    }
    val suggestions = if (!useAssistant) appSuggestions else replyTarget?.let { target ->
        ranking.smartReplies.filter(String::isNotBlank).distinct().take(8).map {
            NotificationSuggestedReply(target.display.id, it, target.display.reply!!.editChoicesBeforeSending)
        }
    }.orEmpty()
    return NotificationInteractions(bindings + generatedActions, suggestions)
}
