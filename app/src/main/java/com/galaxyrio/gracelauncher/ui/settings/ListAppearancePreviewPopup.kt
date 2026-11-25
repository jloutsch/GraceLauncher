package com.galaxyrio.gracelauncher.ui.settings

import com.galaxyrio.gracelauncher.ui.theme.LocalFontShadowEnabled
import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.ListAppearance
import com.galaxyrio.gracelauncher.data.NotificationDisplay
import com.galaxyrio.gracelauncher.data.ShortcutResult
import com.galaxyrio.gracelauncher.data.ShortcutStatus
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.performPopupGesture
import com.galaxyrio.gracelauncher.ui.popupGestureTarget
import com.galaxyrio.gracelauncher.ui.resolvePopupGesture
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.LocalListAppearance
import com.galaxyrio.gracelauncher.ui.components.LocalPopupGestureSettings
import com.galaxyrio.gracelauncher.ui.overlays.FolderPopup
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutPopup
import com.galaxyrio.gracelauncher.ui.overlays.ShortcutRevealState
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.rememberLauncherAppearance
import kotlinx.coroutines.launch

internal class ListAppearancePreviewPanel(
    val anchor: Rect,
    val app: LauncherApp? = null,
    val reveal: ShortcutRevealState = ShortcutRevealState(),
    val parent: ListAppearancePreviewPanel? = null,
)

@Stable
internal class ListAppearancePreviewState {
    internal val postedAt = System.currentTimeMillis()
    internal var panel by mutableStateOf<ListAppearancePreviewPanel?>(null)
        private set
    internal var dismissedNotifications by mutableStateOf(emptySet<String>())
        private set

    internal fun dismissNotification(key: String, revision: Long): Boolean {
        if (!key.startsWith("list-preview:") || revision != 0L || key in dismissedNotifications) return false
        dismissedNotifications += key
        return true
    }

    fun openFolder(anchor: Rect) {
        panel = ListAppearancePreviewPanel(anchor)
    }

    fun dragFolder(anchor: Rect, expanded: Boolean) {
        val current = panel
        if (current != null && current.app == null) current.reveal.expanded = expanded
        else panel = ListAppearancePreviewPanel(anchor, reveal = ShortcutRevealState(expanded, dragging = true))
    }

    fun finishFolderDrag(commit: Boolean) {
        panel?.takeIf { it.app == null }?.reveal?.let {
            it.expanded = commit
            it.dragging = false
        }
    }

    fun openNotificationApp(app: LauncherApp, anchor: Rect) {
        panel = ListAppearancePreviewPanel(anchor, app = app)
    }

    fun dragNotificationApp(app: LauncherApp, anchor: Rect, expanded: Boolean) {
        val current = panel
        if (current != null && current.app?.key == app.key && current.parent == null) current.reveal.expanded = expanded
        else panel = ListAppearancePreviewPanel(anchor, app = app,
            reveal = ShortcutRevealState(expanded, dragging = true))
    }

    fun finishNotificationAppDrag(commit: Boolean) {
        panel?.takeIf { it.app != null && it.parent == null }?.reveal?.let {
            it.expanded = commit
            it.dragging = false
        }
    }

    internal fun openApp(app: LauncherApp, anchor: Rect, parent: ListAppearancePreviewPanel) {
        if (panel === parent) panel = ListAppearancePreviewPanel(anchor, app = app, parent = parent)
    }

    internal fun dismiss(current: ListAppearancePreviewPanel) {
        // Folder members invoke onDismiss after their click callback opens the next panel.
        if (panel === current) panel = current.parent
    }
}

@Composable
internal fun rememberListAppearancePreviewState(): ListAppearancePreviewState = remember { ListAppearancePreviewState() }

internal data class ListAppearancePreviewContent(
    val ordinaryApp: LauncherApp,
    val folder: LauncherFolder,
    val apps: List<LauncherApp>,
    val notifications: List<AppNotification>,
    val notificationApp: LauncherApp,
    val appNotifications: List<AppNotification>,
)

@Composable
internal fun rememberListAppearancePreviewContent(uiState: LauncherUiState, state: ListAppearancePreviewState): ListAppearancePreviewContent {
    val folderName = stringResource(R.string.list_preview_folder)
    val title = stringResource(R.string.list_preview_notification_title)
    val text = stringResource(R.string.list_preview_notification_text)
    val candidates = remember(uiState.apps, uiState.effectiveHiddenAppKeys) {
        uiState.apps.filter {
            it.folderId == null && it.shortcut == null && it.user == null && !it.isLauncherSettings &&
                !it.isPrivateSpace && !it.isWorkProfile && !it.isArchived && it.key !in uiState.effectiveHiddenAppKeys && it.icon != null
        }.distinctBy(LauncherApp::packageName)
    }
    val sources = if (candidates.isEmpty()) {
        // Use the launcher's actual icon while the installed-app artwork is still loading.
        val context = LocalContext.current
        val appName = stringResource(R.string.app_name)
        val icon = remember(context) { context.applicationInfo.loadIcon(context.packageManager).toBitmap(96, 96).asImageBitmap() }
        remember(context, appName, icon) {
            listOf(LauncherApp(ComponentName(context.packageName, "Preview"), appName, icon))
        }
    } else candidates
    val content = remember(state, sources, folderName, title, text) {
        // Preserve the real app's complete rendered artwork, monochrome data and display overrides.
        // Separate sample identities keep the two notification scenarios independent.
        fun sampleApp(index: Int, packageName: String) = sources[index % sources.size]
            .copy(componentName = ComponentName(packageName, "Sample"))
        val apps = listOf(
            sampleApp(2, "list.preview.messages"),
            sampleApp(3, "list.preview.calendar"),
        )
        val notificationApp = sampleApp(1, "list.preview.notifications")
        ListAppearancePreviewContent(
            sampleApp(0, "list.preview.app0"),
            LauncherFolder("list-preview", folderName, apps.map(LauncherApp::key), FolderPlacement.AppList),
            apps,
            listOf(
                AppNotification("list-preview:messages", apps[0].packageName, title, text,
                    state.postedAt, 0L, canDismiss = true, canOpen = true),
                AppNotification("list-preview:calendar", apps[1].packageName, title, text,
                    state.postedAt, 0L, canDismiss = true, canOpen = true),
            ),
            notificationApp,
            listOf(
                AppNotification("list-preview:notification-first", notificationApp.packageName, title, text,
                    state.postedAt, 0L, canDismiss = true, canOpen = true),
                AppNotification("list-preview:notification-second", notificationApp.packageName, title, text,
                    state.postedAt, 0L, canDismiss = true, canOpen = true),
            ),
        )
    }
    return content.copy(
        notifications = content.notifications.filterNot { it.key in state.dismissedNotifications },
        appNotifications = content.appNotifications.filterNot { it.key in state.dismissedNotifications },
    )
}

@Composable
internal fun rememberListPreviewUiState(uiState: LauncherUiState, list: ListAppearance, content: ListAppearancePreviewContent) =
    remember(uiState.settings, list, content) {
        LauncherUiState(
            apps = content.apps + content.notificationApp + content.ordinaryApp,
            folders = listOf(content.folder),
            notifications = (content.notifications + content.appNotifications).groupBy(AppNotification::packageName),
            hasShortcutAccess = true,
            settings = uiState.settings.copy(listAppearance = list,
                shortcutsFolders = uiState.settings.shortcutsFolders.copy(enabled = true, normalNotifications = NotificationDisplay.Normal)),
        )
    }

@Composable
private fun rememberListPreviewActions(state: ListAppearancePreviewState) = remember(state) {
    val ready = ShortcutResult(ShortcutStatus.Ready)
    LauncherActions(shortcuts = { ready }, cachedShortcuts = { ready },
        dismissNotification = state::dismissNotification,
        openNotification = { key, revision -> revision == 0L && key !in state.dismissedNotifications })
}

@Composable
internal fun rememberListPreviewGestures(sampleState: LauncherUiState, state: ListAppearancePreviewState): AppRowGestures {
    val latest by rememberUpdatedState(sampleState)
    val actions = rememberListPreviewActions(state)
    val scope = rememberCoroutineScope()
    fun open(app: LauncherApp, bounds: Rect): Boolean {
        if (app.folderId != null) state.openFolder(bounds) else state.openNotificationApp(app, bounds)
        return true
    }
    return AppRowGestures(
        onLaunchAt = state::openNotificationApp,
        resolveGesture = { owner, gesture, notification -> latest.resolvePopupGesture(owner, gesture, notification, actions.cachedShortcuts) },
        onGesture = { owner, gesture, bounds, notification -> scope.launch {
            performPopupGesture(owner, gesture, notification, state = { latest }, actions = { actions },
                canContinue = { state.panel == null }, openPopup = { open(it, bounds) }, launchItem = { open(it, bounds) })
        } },
        onDrag = state::dragNotificationApp,
        onNotificationDrag = { owner, notification, bounds, expanded ->
            latest.popupGestureTarget(owner, notification)?.let { state.dragNotificationApp(it.app, bounds, expanded) }
        },
        onDragEnd = state::finishNotificationAppDrag,
    )
}

/** Render at the settings root, outside the preview's clipping and scale layer. */
@Composable
internal fun ListAppearancePreviewPopup(
    uiState: LauncherUiState,
    list: ListAppearance,
    iconSize: Int,
    state: ListAppearancePreviewState,
) {
    val current = state.panel ?: return
    val content = rememberListAppearancePreviewContent(uiState, state)
    val appearance = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons, iconSize, uiState.settings.iconDisplay)
    // Only sample notifications are dismissed; no platform notification action is called.
    val sampleState = rememberListPreviewUiState(uiState, list, content)
    val actions = rememberListPreviewActions(state)
    BackHandler {
        current.reveal.dragging = false
        current.reveal.expanded = false
    }
    // This overlay is outside the inline preview's composition locals. Supply its
    // current notification settings here too, so the real popup layout stays in sync.
    CompositionLocalProvider(
        LocalFontShadowEnabled provides false,
        LocalLauncherAppearance provides appearance,
        LocalListAppearance provides list,
        LocalPopupGestureSettings provides sampleState.settings.shortcutsFolders,
    ) {
        val app = current.app
        if (app == null) FolderPopup(
            folder = content.folder, apps = content.apps, anchor = current.anchor, reveal = current.reveal,
            uiState = sampleState, actions = actions,
            showNames = list.showNames(favorites = false, insideFolder = true),
            onDismiss = { state.dismiss(current) },
            onLaunchApp = { item, bounds -> state.openApp(item, bounds, current) },
            onOpenAppPopup = { item, bounds -> state.openApp(item, bounds, current) },
            onEdit = null,
        ) else ShortcutPopup(
            app = sampleState.findItem(app.key) ?: app,
            anchor = current.anchor, reveal = current.reveal, hasAccess = true,
            actions = actions, uiState = sampleState,
            notifications = sampleState.ownNotificationsFor(app, expanded = true),
            onLaunchApp = {}, onLaunchItem = { _, _ -> }, onEdit = null,
            onDismiss = { state.dismiss(current) },
        )
    }
}
