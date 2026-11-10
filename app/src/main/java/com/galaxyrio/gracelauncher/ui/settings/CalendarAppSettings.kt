package com.galaxyrio.gracelauncher.ui.settings

import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.platform.calendarAppPackages
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import kotlinx.coroutines.CancellationException

private fun LauncherApp.isCurrentProfileApp() = !isPrivateSpace && !isWorkProfile && !isCloneProfile &&
    (user == null || user == Process.myUserHandle()) && shortcut == null && folderId == null

@Composable
internal fun calendarAppSummary(uiState: LauncherUiState): String {
    val selected = uiState.settings.calendar.appPackage ?: return stringResource(R.string.calendar_system_default_app)
    return uiState.allApps.firstOrNull { it.packageName == selected && it.isCurrentProfileApp() }?.label
        ?: stringResource(R.string.clock_selected_unavailable)
}

@Composable
internal fun CalendarAppSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val allApps = uiState.allApps
    val selected = uiState.settings.calendar.appPackage
    var retry by remember { mutableIntStateOf(0) }
    val discovery by produceState<Result<Set<String>>?>(null, allApps, retry) {
        value = null
        value = try {
            Result.success(calendarAppPackages(context))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    val packages = discovery?.getOrNull().orEmpty()
    val apps = remember(allApps, packages, selected) {
        allApps.filter { it.isCurrentProfileApp() && (it.packageName in packages || it.packageName == selected) }
            .distinctBy { it.packageName }
    }
    AppSelectionSettings(
        uiState = uiState.copy(isLoadingApps = uiState.isLoadingApps || discovery == null,
            appLoadFailed = uiState.appLoadFailed || discovery?.isFailure == true),
        actions = actions.copy(refreshApps = { retry++; actions.refreshApps() }),
        onBack = onBack, title = stringResource(R.string.calendar_app), tag = "calendar_app",
        selectedKey = if (selected == null) null else apps.firstOrNull { it.packageName == selected }?.key ?: "$selected/",
        showDefault = true, defaultLabel = stringResource(R.string.calendar_system_default_app),
        description = stringResource(R.string.calendar_choose_app_description), apps = apps,
        onSelect = { key ->
            val app = apps.firstOrNull { it.key == key }
            if (key == null || app != null) actions.updateSettings { settings ->
                settings.copy(calendar = settings.calendar.copy(appPackage = app?.packageName))
            }
        },
    )
}
