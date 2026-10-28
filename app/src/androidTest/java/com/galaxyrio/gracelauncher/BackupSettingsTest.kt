package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.backup.BackupArchive
import com.galaxyrio.gracelauncher.data.backup.BackupController
import com.galaxyrio.gracelauncher.data.backup.BackupProblem
import com.galaxyrio.gracelauncher.data.backup.BackupStatusStore
import com.galaxyrio.gracelauncher.data.backup.ImportResult
import com.galaxyrio.gracelauncher.data.backup.ImportSummary
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.BackupSettings
import com.galaxyrio.gracelauncher.ui.settings.ImportResultDialog
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("backup_status", Context.MODE_PRIVATE)
    private val store get() = BackupStatusStore(context)
    private var saved: Map<String, Any?> = emptyMap()
    private val state = LauncherUiState(apps = listOf(LauncherApp(ComponentName("test.calendar", "Calendar"), "Calendar", null)),
        isLoadingApps = false)

    @Before fun clearStatus() {
        saved = prefs.all.toMap()
        prefs.edit(commit = true) { clear() }
        BackupController.dismiss()
    }

    @After fun restoreStatus() {
        prefs.edit(commit = true) {
            clear()
            saved.forEach { (key, value) ->
                when (value) { is Long -> putLong(key, value); is Boolean -> putBoolean(key, value); is String -> putString(key, value) }
            }
        }
        BackupController.dismiss()
    }

    private class FakeRegistry(private val answer: (ActivityResultContract<*, *>) -> Any?) : ActivityResultRegistry() {
        val launched = mutableListOf<Pair<ActivityResultContract<*, *>, Any?>>()
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += contract to input
            dispatchResult(requestCode, answer(contract))
        }
    }

    /** The page is removed and recomposed so it re-reads the status store. */
    private fun showPage(registry: FakeRegistry = FakeRegistry { null }): MutableState<Boolean> {
        val visible = mutableStateOf(true)
        compose.setContent {
            val owner = remember { object : ActivityResultRegistryOwner { override val activityResultRegistry = registry } }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                GraceLauncherTheme(dynamicColor = false) { if (visible.value) BackupSettings(state, onBack = {}) }
            }
        }
        compose.waitForIdle()
        return visible
    }

    private fun MutableState<Boolean>.reload(change: () -> Unit) {
        compose.runOnIdle { value = false }
        change()
        compose.runOnIdle { value = true }
        compose.waitForIdle()
    }

    @Test fun backupPageReachableFromRoot() {
        compose.setContent { GraceLauncherTheme(dynamicColor = false) { LauncherSettingsScreen(state, LauncherActions(), onBack = {}) } }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_category_backup"))
        compose.onNodeWithTag("settings_category_backup").assertTextContains("Backup and restore").performClick()
        compose.onNodeWithTag("settings_backup").assertIsDisplayed()
        compose.onNodeWithText("Export backup").assertIsDisplayed()
        compose.onNodeWithText("Import backup").assertIsDisplayed()
    }

    @Test fun lastExportedShowsNeverThenDays() {
        val page = showPage()
        compose.onNodeWithTag("backup_last_exported").assertTextEquals("Last exported: Never")
        page.reload { store.lastExportAtMillis = System.currentTimeMillis() - 3 * 86_400_000L - 60_000L }
        compose.onNodeWithTag("backup_last_exported").assertTextEquals("Last exported: 3 days ago")
        page.reload { store.lastExportAtMillis = System.currentTimeMillis() - 86_400_000L - 60_000L }
        compose.onNodeWithTag("backup_last_exported").assertTextEquals("Last exported: 1 day ago")
    }

    @Test fun failureLineOnlyAfterFailure() {
        val page = showPage()
        compose.onNodeWithTag("backup_failure").assertDoesNotExist()
        val day = LocalDate.of(2026, 10, 20)
        val failedAt = day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val expected = "Automatic backup failed on ${DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(day)}: folder not accessible"
        page.reload { store.lastFailureAtMillis = failedAt; store.lastFailureReason = "folder not accessible" }
        compose.onNodeWithTag("backup_failure").assertTextEquals(expected)
        page.reload { store.lastExportAtMillis = failedAt - 86_400_000L }
        compose.onNodeWithTag("backup_failure").assertTextEquals(expected)
        page.reload { store.lastExportAtMillis = failedAt + 60_000L }
        compose.onNodeWithTag("backup_failure").assertDoesNotExist()
    }

    @Test fun summaryListsSkippedItems() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                ImportResultDialog(ImportResult.Success(ImportSummary(5, 50, 13, 6, true, listOf("Simple MBTA", "Transit Now"), listOf("GeauxWeather"))), onDismiss = {})
            }
        }
        compose.onNodeWithText("Restored 5 folders, 50 apps, 13 favorites, 6 widgets, all settings.").assertIsDisplayed()
        compose.onNodeWithText("Not installed yet (will appear once installed): Simple MBTA, Transit Now").assertIsDisplayed()
        compose.onNodeWithText("Widgets that need setup: 6").assertIsDisplayed()
        compose.onNodeWithText("They appear one at a time on the home screen").assertIsDisplayed()
        compose.onNodeWithText("Widgets left out (app not installed): GeauxWeather").assertIsDisplayed()
    }

    @Test fun summaryOmitsEmptyLinesAndUsesSingulars() {
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                ImportResultDialog(ImportResult.Success(ImportSummary(1, 1, 1, 0, false, emptyList(), emptyList())), onDismiss = {})
            }
        }
        compose.onNodeWithText("Restored 1 folder, 1 app, 1 favorite, 0 widgets.").assertIsDisplayed()
        compose.onNodeWithText("Not installed yet", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Widgets that need setup", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Widgets left out", substring = true).assertDoesNotExist()
    }

    @Test fun importProblemsUsePlainSentences() {
        var result by mutableStateOf<ImportResult>(ImportResult.Rejected(BackupProblem.NotABackup))
        compose.setContent { GraceLauncherTheme(dynamicColor = false) { ImportResultDialog(result, onDismiss = {}) } }
        compose.onNodeWithText("This file is not a Grace Launcher backup.").assertIsDisplayed()
        val newer = "This backup was made by a newer version of Grace Launcher. Update the app and try again."
        compose.runOnIdle { result = ImportResult.Rejected(BackupProblem.NewerFormat(2)) }
        compose.onNodeWithText(newer).assertIsDisplayed()
        compose.runOnIdle { result = ImportResult.Rejected(BackupProblem.NewerSchema(99)) }
        compose.onNodeWithText(newer).assertIsDisplayed()
        compose.runOnIdle { result = ImportResult.Failed(IOException("disk"), restored = true) }
        compose.onNodeWithText("Import failed. Your setup was not changed.").assertIsDisplayed()
        compose.runOnIdle { result = ImportResult.Failed(IOException("disk"), restored = false) }
        compose.onNodeWithText("Import failed and your previous setup could not be fully restored. Import an earlier backup to recover.").assertIsDisplayed()
    }

    @Test fun exportSavesTheBackupAndRecordsIt() {
        val target = File(context.cacheDir, "backup-settings-test.zip").apply { delete() }
        val registry = FakeRegistry { Uri.fromFile(target) }
        showPage(registry)
        compose.onNodeWithTag("backup_export").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Last exported: today").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("gracelauncher-${LocalDate.now()}.zip", registry.launched.single().second)
        assertTrue(registry.launched.single().first is ActivityResultContracts.CreateDocument)
        assertNotNull(ZipFile(target).use { it.getEntry(BackupArchive.MANIFEST) })
        assertNotNull(store.lastAutoHash)
        target.delete()
    }

    @Test fun automaticBackupStaysOffWhenTheFolderPickerIsCancelled() {
        val registry = FakeRegistry { null }
        showPage(registry)
        compose.onNodeWithTag("backup_auto").performClick()
        compose.runOnIdle {
            assertTrue(registry.launched.single().first is ActivityResultContracts.OpenDocumentTree)
            assertFalse(store.autoEnabled)
        }
        compose.onNodeWithTag("backup_auto").assertIsOff()
    }

    @Test fun automaticBackupWithAFolderTurnsOnWithoutAPicker() {
        val registry = FakeRegistry { null }
        val page = showPage(registry)
        page.reload { store.autoFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ABackups" }
        compose.onNodeWithTag("backup_folder").assertTextContains("Backups", substring = true)
        compose.onNodeWithTag("backup_auto").performClick()
        compose.runOnIdle { assertTrue(registry.launched.isEmpty()); assertTrue(store.autoEnabled) }
        compose.onNodeWithTag("backup_auto").assertIsOn()
        compose.onNodeWithTag("backup_auto").performClick()
        compose.runOnIdle { assertFalse(store.autoEnabled) }
        compose.onNodeWithTag("backup_auto").assertIsOff()
    }
}
