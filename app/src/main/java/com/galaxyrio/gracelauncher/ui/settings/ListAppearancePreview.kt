@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import com.galaxyrio.gracelauncher.data.ListAppearance
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppListSectionHeader
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.FolderRow
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.LocalLauncherInputEnabled
import com.galaxyrio.gracelauncher.ui.components.LocalListAppearance
import com.galaxyrio.gracelauncher.ui.components.LocalPopupGestureSettings
import com.galaxyrio.gracelauncher.ui.components.endPadding
import com.galaxyrio.gracelauncher.ui.components.startPadding
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance

@Composable
internal fun ListAppearancePreview(uiState: LauncherUiState, list: ListAppearance, iconSize: Int, modifier: Modifier,
    popupState: ListAppearancePreviewState) {
    val appearance = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons, iconSize, uiState.settings.iconDisplay)
    val content = rememberListAppearancePreviewContent(uiState, popupState)
    val popupSettings = uiState.settings.shortcutsFolders
    val notifications = popupSettings.previewNotifications(content.notifications)
    CompositionLocalProvider(
        LocalLauncherAppearance provides appearance,
        LocalListAppearance provides list,
        LocalLauncherInputEnabled provides false,
        LocalPopupGestureSettings provides popupSettings,
    ) {
        // Keep desktop widths and text wrapping; scroll tall samples without scaling their geometry.
        Column(
            modifier = modifier.fillMaxWidth().testTag("list_appearance_preview")
                .clipToBounds().verticalScroll(rememberScrollState())
                .padding(start = list.startPadding, end = list.endPadding),
        ) {
            AppListSectionHeader("A", Modifier.testTag("list_preview_section"))
            LauncherAppRow(content.ordinaryApp, {}, {}, Modifier.testTag("list_preview_first_app"),
                showLabel = list.showNames(favorites = false))
            CompositionLocalProvider(LocalLauncherInputEnabled provides true) {
                LauncherAppRow(content.notificationApp, {}, {}, Modifier.testTag("list_preview_notification_app"),
                    onSwipeRight = { bounds -> popupState.openNotificationApp(content.notificationApp, bounds) },
                    gestures = AppRowGestures(
                        onLaunchAt = popupState::openNotificationApp,
                        onDrag = popupState::dragNotificationApp,
                        onDragEnd = popupState::finishNotificationAppDrag,
                    ),
                    showLabel = list.showNames(favorites = false),
                    notifications = popupSettings.previewNotifications(content.appNotifications))
                FolderRow(content.folder, popupState::openFolder, null, Modifier.testTag("list_preview_folder"),
                    onDrag = popupState::dragFolder, onDragEnd = popupState::finishFolderDrag,
                    showLabel = list.showNames(favorites = false), notifications = notifications)
            }
        }
    }
}
