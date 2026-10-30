package com.galaxyrio.gracelauncher

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.media.HeadphoneAppsSettings
import com.galaxyrio.gracelauncher.data.media.HeadphoneState
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadphoneAppsStateTest {
    private val podcasts = LauncherApp(ComponentName("test.podcasts", "Podcasts.Activity"), "Podcasts", null)
    private val state = LauncherUiState(apps = listOf(podcasts), isLoadingSettings = false,
        settings = LauncherSettings(mediaPlayer = true, headphoneApps = HeadphoneAppsSettings(true, listOf(podcasts.key))),
        headphones = HeadphoneState(connected = true))

    @Test fun appsShowForAsLongAsHeadphonesAreConnected() {
        assertEquals(listOf(podcasts), state.headphoneApps)
        assertEquals(emptyList<LauncherApp>(), state.copy(headphones = HeadphoneState(connected = false)).headphoneApps)
        val off = state.settings.headphoneApps.copy(enabled = false)
        assertEquals(emptyList<LauncherApp>(), state.copy(settings = state.settings.copy(headphoneApps = off)).headphoneApps)
    }
}
