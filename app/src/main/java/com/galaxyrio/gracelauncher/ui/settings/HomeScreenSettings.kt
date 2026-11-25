package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState

@Composable
internal fun HomeScreenSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit, onEdit: () -> Unit) {
    val home = uiState.settings.homeLayout
    SettingsScaffold(stringResource(R.string.settings_home_screen), "home_screen_settings", onBack) { padding ->
        SettingsList(padding) {
            item {
                SettingsFeatureBanner(stringResource(R.string.home_screen_enable), home.enabled, "home_screen_enabled") { enabled ->
                    actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(enabled = enabled)) }
                }
            }
            item {
                SettingsActionItem(stringResource(R.string.edit_homepage), null, 0, 3, "home_screen_edit", onClick = onEdit)
            }
            item {
                SettingsToggleItem(stringResource(R.string.home_disable_scrolling), stringResource(R.string.home_disable_scrolling_summary),
                    home.disableScrolling, 1, 3, "home_disable_scrolling") { disabled ->
                    actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(disableScrolling = disabled)) }
                }
            }
            item {
                SettingsToggleItem(stringResource(R.string.home_disable_stretch), null,
                    home.disableStretch, 2, 3, "home_disable_stretch") { disabled ->
                    actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(disableStretch = disabled)) }
                }
            }
        }
    }
}
