package com.galaxyrio.gracelauncher.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppSelectionList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun MediaPlayerSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    onHeadphoneApps: () -> Unit = {}, onAppearance: () -> Unit = {}) {
    val context = LocalContext.current
    val settings = uiState.settings
    var requestAccess by rememberSaveable { mutableStateOf(false) }
    val musicPackages by produceState<Set<String>>(emptySet(), uiState.allApps) {
        value = withContext(Dispatchers.IO) {
            val intents = listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
                Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse("content://media/external/audio/media/1"), "audio/*"))
            intents.flatMap { intent ->
                @Suppress("DEPRECATION")
                runCatching { context.packageManager.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            }.mapNotNull { it.activityInfo?.packageName }.toSet()
        }
    }
    AppSelectionSettings(uiState, actions, onBack,
        title = stringResource(R.string.settings_media_player), tag = "media_player",
        appBarActions = { SettingsAppearanceAction(onAppearance) },
        selectedKey = settings.mediaAppKey, showDefault = true, defaultLabel = stringResource(R.string.settings_none),
        apps = uiState.allApps.filter { it.packageName in musicPackages || it.key == settings.mediaAppKey },
        onSelect = { key -> actions.updateSettings { it.copy(mediaAppKey = key) } },
        header = {
            item { SettingsFeatureBanner(stringResource(R.string.media_enable), settings.mediaPlayer, "media_enabled") { enabled ->
                actions.updateSettings { it.copy(mediaPlayer = enabled) }
                if (enabled && !uiState.media.hasAccess) requestAccess = true
            } }
            item { SettingsToggleItem(stringResource(R.string.media_keep_while_playing), stringResource(R.string.media_keep_while_playing_summary),
                settings.mediaKeepWhilePlaying, 0, 2, "media_keep_while_playing") { value ->
                actions.updateSettings { it.copy(mediaKeepWhilePlaying = value) }
            } }
            item { SettingsToggleItem(stringResource(R.string.media_always_visible), stringResource(R.string.media_always_visible_summary),
                settings.mediaAlwaysVisible, 1, 2, "media_always_visible") { value ->
                actions.updateSettings { it.copy(mediaAlwaysVisible = value) }
            } }
            item {
                val headphones = settings.headphoneApps
                Spacer(Modifier.height(12.dp))
                SettingsActionItem(stringResource(R.string.headphone_apps_title),
                    if (headphones.enabled) pluralStringResource(R.plurals.headphone_apps_count, headphones.appKeys.size, headphones.appKeys.size)
                    else stringResource(R.string.headphone_apps_off),
                    0, 1, "media_headphone_apps", onClick = onHeadphoneApps)
            }
            if (!uiState.media.hasAccess) item {
                Spacer(Modifier.height(12.dp))
                SettingsActionItem(stringResource(R.string.media_allow_controls), stringResource(R.string.media_access_required),
                    0, 1, "media_access") { requestAccess = true }
            }
            item { SettingsHeading(stringResource(R.string.media_fallback_app)) }
        },
    )
    if (requestAccess) AlertDialog(onDismissRequest = { requestAccess = false },
        title = { Text(stringResource(R.string.media_allow_controls)) },
        text = { Text(stringResource(R.string.media_access_explanation)) },
        confirmButton = { TextButton(onClick = { requestAccess = false; actions.requestMediaAccess() }) { Text(stringResource(R.string.media_open_settings)) } },
        dismissButton = { TextButton(onClick = { requestAccess = false }) { Text(stringResource(R.string.settings_cancel)) } })
}

@Composable
internal fun HeadphoneAppsSettingsScreen(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val choice = uiState.settings.headphoneApps
    val queryState = rememberTextFieldState()
    val ownPackage = LocalContext.current.packageName
    val apps = remember(uiState.allApps, ownPackage) { uiState.allApps.filter { it.packageName != ownPackage && it.shortcut == null } }
    val ready = !uiState.isLoadingApps && !uiState.appLoadFailed
    SettingsScaffold(stringResource(R.string.headphone_apps_title), "settings_headphone_apps_page", onBack) { padding ->
        AppSelectionList(
            apps = if (ready) apps else emptyList(), selectedKeys = choice.appKeys.toSet(), query = queryState,
            onSelect = { app -> actions.updateSettings { it.copy(headphoneApps = it.headphoneApps.toggle(app.key)) } },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            searchTag = "headphone_apps_query", itemTagPrefix = "headphone_app",
            enabled = LocalSettingsStorageState.current.canEdit && ready,
        ) {
            item { SettingsFeatureBanner(stringResource(R.string.headphone_apps_enable), choice.enabled, "headphone_apps_enabled") { enabled ->
                actions.updateSettings { it.copy(headphoneApps = it.headphoneApps.copy(enabled = enabled)) }
            } }
            item {
                Text(stringResource(R.string.headphone_apps_description), Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Text(pluralStringResource(R.plurals.settings_select_count, choice.appKeys.size, choice.appKeys.size),
                    Modifier.padding(horizontal = 8.dp, vertical = 16.dp).testTag("headphone_apps_count"),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
