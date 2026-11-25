package com.galaxyrio.gracelauncher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsRootOpensBackupWithExportAndRestoreActions() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(LauncherUiState(isDefaultHome = true), LauncherActions(), onBack = {})
            }
        }
        compose.onNodeWithTag("settings_category_backup").performClick()
        compose.onNodeWithTag("backup_export").assertIsDisplayed()
        compose.onNodeWithTag("backup_restore").assertIsDisplayed()
    }
}
