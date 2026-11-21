package com.galaxyrio.gracelauncher

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AutoBackupTriggerTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun clearBackupStatus() {
        context.getSharedPreferences("backup_status", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun clearAutoBackupHook() { MainActivity.autoBackup = null }

    /** MainActivity is singleTask, so clear any existing task to force a cold start. */
    private fun launch() = ActivityScenario.launch<MainActivity>(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))

    @Test fun coldStartRunsAutoBackupAfterTheAppListLoads() {
        val calls = Collections.synchronizedList(mutableListOf<LauncherUiState>())
        val triggered = CountDownLatch(1)
        MainActivity.autoBackup = { state ->
            calls += state
            triggered.countDown()
        }
        launch().use {
            assertTrue("auto-backup was not triggered after the app list loaded",
                triggered.await(30, TimeUnit.SECONDS))
        }
        val state = calls.first()
        assertFalse("auto-backup ran while the app list was still loading", state.isLoadingApps)
        assertFalse(state.appLoadFailed)
        assertTrue("auto-backup ran before any app labels were available", state.apps.isNotEmpty())
    }

    @Test fun returningToTheLauncherRunsAutoBackupAgainWithTheLoadedApps() {
        val calls = Collections.synchronizedList(mutableListOf<LauncherUiState>())
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        MainActivity.autoBackup = { state ->
            calls += state
            if (calls.size == 1) first.countDown() else second.countDown()
        }
        launch().use { scenario ->
            assertTrue("auto-backup was not triggered on the first resume",
                first.await(30, TimeUnit.SECONDS))
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue("auto-backup did not run again after resuming",
                second.await(30, TimeUnit.SECONDS))
        }
        assertTrue(calls.size >= 2)
        calls.forEach { state ->
            assertFalse(state.isLoadingApps)
            assertFalse(state.appLoadFailed)
            assertTrue(state.apps.isNotEmpty())
        }
    }
}
