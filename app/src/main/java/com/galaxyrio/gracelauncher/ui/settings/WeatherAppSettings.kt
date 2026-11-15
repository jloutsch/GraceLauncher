package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState

@Composable
internal fun weatherAppSummary(uiState: LauncherUiState): String {
    val key = uiState.settings.weatherConnection.appKey ?: return stringResource(R.string.weather_default_app)
    return uiState.allApps.firstOrNull { it.key == key }?.label ?: stringResource(R.string.clock_selected_unavailable)
}

@Composable
internal fun WeatherAppSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    AppSelectionSettings(
        uiState, actions, onBack, title = stringResource(R.string.weather_app), tag = "weather_app",
        selectedKey = uiState.settings.weatherConnection.appKey,
        showDefault = true, defaultLabel = stringResource(R.string.weather_default_app),
        description = stringResource(R.string.weather_choose_app_description),
        onSelect = { key ->
            actions.updateSettings { it.copy(weatherConnection = it.weatherConnection.copy(appKey = key)) }
        },
    )
}
