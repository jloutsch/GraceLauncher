package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AppStackInset
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.AppStack
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.LauncherLayout
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance

private const val HeadphoneStackId = "headphone-apps"

/** Shown under the media player while headphones are connected: one tap opens the chosen player. */
@Composable
internal fun HomeHeadphoneApps(
    apps: List<LauncherApp>,
    uiState: LauncherUiState,
    onLaunch: (LauncherApp) -> Unit,
    onDetails: (LauncherApp) -> Unit,
    onShortcuts: (LauncherApp, Rect) -> Unit,
    modifier: Modifier = Modifier,
    rowGestures: AppRowGestures = AppRowGestures(),
) {
    val appearance = LocalLauncherAppearance.current
    // These rows are not home items: keep launch, pop-up and swipe gestures, never reordering drags.
    val gestures = AppRowGestures(onPrepare = rowGestures.onPrepare, popupsEnabled = rowGestures.popupsEnabled,
        onLaunchAt = rowGestures.onLaunchAt,
        onOpenFirst = rowGestures.onOpenFirst)
    Column(modifier.fillMaxWidth().testTag("home_headphone_apps")) {
        Row(Modifier.fillMaxWidth().padding(start = LauncherLayout.ContentInset, end = LauncherLayout.ContentInset, top = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ms_headphones), null, Modifier.size(20.dp), tint = appearance.text)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.headphone_apps_connected), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, shadow = appearance.textShadow),
                color = appearance.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (uiState.settings.headphoneApps.asAppStack) {
            val stack = LauncherFolder(HeadphoneStackId, stringResource(R.string.headphone_apps_title),
                apps.map(LauncherApp::key), FolderPlacement.None, isAppStack = true)
            AppStack(stack, uiState, contents = apps,
                onLaunch = { app, bounds -> gestures.onLaunchAt?.invoke(app, bounds) ?: onLaunch(app) },
                onDetails = onDetails, onEdit = {},
                modifier = Modifier.padding(horizontal = LauncherLayout.ContentInset - AppStackInset.dp, vertical = 4.dp))
        } else apps.forEach { app ->
            LauncherAppRow(app, onClick = { onLaunch(app) }, onLongClick = { onDetails(app) },
                onSwipeRight = { onShortcuts(app, it) }, gestures = gestures,
                modifier = Modifier.testTag("home_headphone_app:${app.key}"),
                notifications = uiState.notificationPreviewsFor(app),
                showLabel = uiState.settings.listAppearance.showNames(favorites = true))
        }
    }
}
