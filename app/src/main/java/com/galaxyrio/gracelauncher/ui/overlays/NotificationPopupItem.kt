package com.galaxyrio.gracelauncher.ui.overlays

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.notifications.*
import com.galaxyrio.gracelauncher.ui.components.LocalPopupGestureSettings
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.SwipeDismissContainer
import com.galaxyrio.gracelauncher.ui.components.notificationAge
import com.galaxyrio.gracelauncher.ui.theme.FontShadowText as Text

@Composable
internal fun NotificationPopupItem(
    app: LauncherApp,
    notification: AppNotification,
    onOpen: () -> Unit,
    onDismiss: () -> Boolean,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    dismissResistance: Int = 75,
    dismissThresholdDp: Int = 72,
    onAction: (AppNotificationAction, String?, Boolean) -> NotificationActionResult = { _, _, _ -> NotificationActionResult.Unavailable },
) {
    val settings = LocalPopupGestureSettings.current
    val display = remember(notification, settings.reduceRepeatedSenders) { notification.forDisplay(settings.reduceRepeatedSenders) }
    val icon = rememberNotificationImage(notification.icon)
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var replyId by remember(notification.key) { mutableStateOf<String?>(null) }
    var editingSuggestion by remember(notification.key) { mutableStateOf(false) }
    var draft by remember(notification.key) { mutableStateOf("") }
    var feedback by remember(notification.key, notification.revision) { mutableStateOf<Int?>(null) }
    var replied by remember(notification.key, notification.revision) { mutableStateOf(false) }
    var unlocking by remember(notification.key) { mutableStateOf(false) }
    val replyTarget = notification.actions.firstOrNull { it.id == replyId && it.reply?.allowFreeForm == true }
    val interactive = gesturesEnabled && !unlocking
    fun closeReply() {
        replyId = null
        focusManager.clearFocus()
        keyboard?.hide()
    }
    LaunchedEffect(replyId, replyTarget, settings.notificationDirectReply, settings.notificationSuggestedReplies) {
        val allowed = if (editingSuggestion) settings.notificationSuggestedReplies else settings.notificationDirectReply
        if (replyId != null && (replyTarget == null || !allowed)) {
            if (replyTarget == null) draft = ""
            closeReply()
        }
    }
    BackHandler(replyTarget != null) { closeReply() }
    fun finish(result: NotificationActionResult, isReply: Boolean) {
        when (result) {
            NotificationActionResult.Sent -> {
                feedback = if (isReply) R.string.notification_reply_sent else null
                if (isReply) { replied = true; draft = ""; closeReply() }
            }
            NotificationActionResult.Unavailable -> feedback = R.string.notification_action_failed
            NotificationActionResult.UnlockRequired -> feedback = R.string.notification_unlock_required
        }
    }
    fun execute(action: AppNotificationAction, text: String? = null, fromChoice: Boolean = false) {
        if (!interactive || (text != null && (replied || text.isBlank()))) return
        val result = onAction(action, text, fromChoice)
        if (result != NotificationActionResult.UnlockRequired) finish(result, text != null) else {
            feedback = R.string.notification_unlock_required
            val activity = context.notificationActivity()
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            if (activity != null && keyguard != null) {
                unlocking = true
                keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        unlocking = false
                        // Reuse this exact revision/target; the store rejects a stale callback.
                        finish(onAction(action, text, fromChoice), text != null)
                    }
                    override fun onDismissCancelled() { unlocking = false }
                    override fun onDismissError() { unlocking = false }
                })
            }
        }
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)) {
            val wide = settings.widerNotificationDetails
            Column(Modifier.fillMaxWidth().clickable(enabled = notification.canOpen && interactive, onClick = onOpen)) {
                Row(verticalAlignment = if (wide) Alignment.CenterVertically else Alignment.Top) {
                    if (icon != null) Image(icon, null, Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else AppIcon(app, size = 36.dp)
                    Spacer(Modifier.width(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${header(app, display, settings.reduceRepeatedSenders)} · ${notificationAge(notification.postedAt)}",
                            style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("notification_header:${notification.key}"))
                        if (!wide && display.text.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            NotificationBody(display)
                        }
                    }
                }
                if (wide && display.text.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    NotificationBody(display)
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = if (wide) 0.dp else 58.dp)) {
                if (settings.notificationProgress) notification.progress?.let {
                    Spacer(Modifier.height(8.dp))
                    NotificationProgressIndicator(it)
                }
                if (settings.notificationSuggestedReplies && replyTarget == null && notification.suggestedReplies.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        notification.suggestedReplies.forEach { suggestion ->
                            val target = notification.actions.firstOrNull { it.id == suggestion.actionId }
                            if (target != null) OutlinedButton(
                                onClick = {
                                    if (suggestion.editBeforeSending && target.reply?.allowFreeForm == true) {
                                        draft = suggestion.text; editingSuggestion = true; replyId = target.id; feedback = null
                                    } else execute(target, suggestion.text, fromChoice = true)
                                }, enabled = interactive && !replied,
                                modifier = Modifier.testTag("notification_suggestion:${notification.key}:${suggestion.text}"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) { Text(suggestion.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                val actions = notification.actions.filter {
                    if (it.reply != null) settings.notificationDirectReply && it.reply.allowFreeForm
                    else settings.notificationActions
                }
                if (actions.isNotEmpty() && replyTarget == null) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        actions.forEach { action ->
                            val click = {
                                if (action.reply != null) { editingSuggestion = false; replyId = action.id; feedback = null }
                                else execute(action)
                            }
                            val tag = Modifier.testTag("notification_action:${notification.key}:${action.id}")
                            val enabled = interactive && !(replied && action.reply != null)
                            if (action.emphasized) Button(click, tag, enabled = enabled,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(action.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            } else TextButton(click, tag, enabled = enabled,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                                Text(action.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (replyTarget != null) {
                    Spacer(Modifier.height(8.dp))
                    NotificationReplyEditor(notification.key, replyTarget.reply!!.label, draft,
                        enabled = interactive && !replied,
                        onChange = { draft = it.take(4096) }, onSend = { execute(replyTarget, draft) }, onClose = { closeReply() })
                }
                feedback?.let { message ->
                    Text(stringResource(message), Modifier.padding(top = 8.dp).testTag("notification_feedback:${notification.key}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message == R.string.notification_reply_sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    // Keep text selection and cursor drags out of the card's horizontal dismiss recognizer.
    if (replyTarget != null) Box(modifier.fillMaxWidth().testTag("notification:${notification.key}")) { body() }
    else SwipeDismissContainer(notification.key to notification.revision, onDismiss,
        modifier.fillMaxWidth().testTag("notification:${notification.key}"),
        enabled = notification.canDismiss && gesturesEnabled,
        resistance = dismissResistance, thresholdDp = dismissThresholdDp, content = body)
}

@Composable
private fun NotificationBody(display: AppNotification) {
    Text(display.text, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("notification_body:${display.key}"))
}

@Composable
private fun NotificationReplyEditor(key: String, label: String, value: String, enabled: Boolean,
    onChange: (String) -> Unit, onSend: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val bringIntoView = remember { BringIntoViewRequester() }
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    LaunchedEffect(ime) { if (ime > 0) bringIntoView.bringIntoView() }
    Column(Modifier.bringIntoViewRequester(bringIntoView)) {
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth().focusRequester(focus).testTag("notification_reply:$key"),
            enabled = enabled, placeholder = { Text(label.ifBlank { stringResource(R.string.notification_reply_hint) }) },
            minLines = 2, maxLines = 4, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (enabled && value.isNotBlank()) onSend() }))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(painterResource(R.drawable.ms_close), stringResource(R.string.notification_reply_close)) }
            FilledIconButton(onSend, enabled = enabled && value.isNotBlank(), modifier = Modifier.testTag("notification_send:$key")) {
                Icon(painterResource(R.drawable.ms_send), stringResource(R.string.notification_reply_send))
            }
        }
    }
}

private tailrec fun Context.notificationActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.notificationActivity()
    else -> null
}

private fun header(app: LauncherApp, notification: AppNotification, reduceRepeatedSenders: Boolean): String =
    if (reduceRepeatedSenders && isTitleRedundant(notification.title, notification.text)) app.label
    else notification.title.ifBlank { app.label }
