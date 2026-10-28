package com.galaxyrio.gracelauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.backup.AutoBackupRunner
import com.galaxyrio.gracelauncher.data.backup.BackupController
import com.galaxyrio.gracelauncher.data.backup.BackupTask
import com.galaxyrio.gracelauncher.data.backup.WidgetRestoreRepository
import com.galaxyrio.gracelauncher.platform.LauncherAppTransitions
import com.galaxyrio.gracelauncher.ui.LauncherAppTheme
import com.galaxyrio.gracelauncher.ui.LauncherRoute
import com.galaxyrio.gracelauncher.ui.LauncherViewModel
import com.galaxyrio.gracelauncher.ui.components.LocalAppTransitions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val launcherViewModel: LauncherViewModel by viewModels()
    private var widgetEditRequest by mutableIntStateOf(0)
    private val appTransitions by lazy { LauncherAppTransitions(this, lifecycleScope) }
    private var widgetRestoreInFlight = false
    private var autoBackupInFlight = false
    // Placeholders whose setup failed with an error; retried by the next MainActivity instance, not on every resume.
    private val skippedRestores = mutableSetOf<Int>()
    // The result arrives just before the next onResume, once WidgetSetupActivity has resolved its placeholder.
    private val widgetRestore = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == WidgetSetupActivity.RESULT_RESTORE_SKIPPED)
            result.data?.extras?.takeIf { it.containsKey(WidgetSetupActivity.EXTRA_RESTORE_PLACEHOLDER) }
                ?.let { skippedRestores += it.getInt(WidgetSetupActivity.EXTRA_RESTORE_PLACEHOLDER) }
        widgetRestoreInFlight = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_EDIT_WIDGET, false)) { widgetEditRequest++; intent.removeExtra(EXTRA_EDIT_WIDGET) }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            CompositionLocalProvider(LocalAppTransitions provides appTransitions) {
                LauncherAppTheme(launcherViewModel) {
                    LauncherRoute(viewModel = launcherViewModel, widgetEditRequest = widgetEditRequest)
                }
            }
        }
        appTransitions.onHomeIntent(intent)
        lifecycleScope.launch {
            // Importing from the settings page inside home never pauses this activity, so onResume alone would not start restore.
            var previous: BackupTask? = null
            BackupController.state.collect { current ->
                if (previous is BackupTask.Imported && current == null) restoreNextWidget()
                previous = current
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launcherViewModel.refreshOnResume()
        launcherViewModel.refreshSchedule()
        restoreNextWidget()
        runAutoBackup()
    }

    /** Binds one imported widget per home resume until no placeholders remain. */
    private fun restoreNextWidget() {
        if (widgetRestoreInFlight) return
        widgetRestoreInFlight = true
        lifecycleScope.launch {
            val next = try {
                    WidgetRestoreRepository(LauncherDatabase.getInstance(this@MainActivity)).pending()
                        .firstOrNull { it.placeholderId !in skippedRestores }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e("GraceWidgetSetup", "restore queue read failed", error); null
                }
            if (next == null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { widgetRestoreInFlight = false; return@launch }
            widgetRestore.launch(Intent(this@MainActivity, WidgetSetupActivity::class.java)
                .putExtra(WidgetSetupActivity.EXTRA_RESTORE_PLACEHOLDER, next.placeholderId)
                .putExtra(WidgetSetupActivity.EXTRA_RESTORE_OWNER, next.ownerKey))
        }
    }

    private fun runAutoBackup() {
        if (autoBackupInFlight) return
        // Right after process start the app list is still loading; a backup now would carry no labels.
        val state = launcherViewModel.uiState.value
        if (state.isLoadingApps || state.appLoadFailed || state.apps.isEmpty()) return
        autoBackupInFlight = true
        val labels = state.apps.associate { it.key to it.label }
        lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { AutoBackupRunner(this@MainActivity).runIfDue(labels) } }
            finally { autoBackupInFlight = false }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_EDIT_WIDGET, false)) { widgetEditRequest++; intent.removeExtra(EXTRA_EDIT_WIDGET) }
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            launcherViewModel.requestReturnHome()
            appTransitions.onHomeIntent(intent)
        }
    }

    override fun onPause() {
        appTransitions.close()
        super.onPause()
    }

    override fun onDestroy() {
        appTransitions.close()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) appTransitions.close()
        return super.dispatchTouchEvent(event)
    }

    companion object { const val EXTRA_EDIT_WIDGET = "editHomeWidget" }
}
