package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.NotificationDisplay
import com.galaxyrio.gracelauncher.data.ShortcutsFoldersSettings
import com.galaxyrio.gracelauncher.data.media.MediaSnapshot
import com.galaxyrio.gracelauncher.data.weather.WeatherConnectionMode
import com.galaxyrio.gracelauncher.data.weather.WeatherConnectionPreferences
import com.galaxyrio.gracelauncher.data.weather.WeatherState
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.settings.ImportAccess
import com.galaxyrio.gracelauncher.ui.settings.missingImportAccess
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fork #9: after an import, list each restored feature that still needs a permission Android won't restore. */
class ImportAccessTest {
    private val noPreviews = ShortcutsFoldersSettings(
        normalNotifications = NotificationDisplay.Hidden, silentNotifications = NotificationDisplay.Hidden)
    private val allGranted = LauncherUiState(
        settings = LauncherSettings(weatherEnabled = true),
        scheduleStatus = ScheduleStatus.Ready,
        weather = WeatherState(status = WeatherStatus.Ready),
        media = MediaSnapshot(hasAccess = true),
    )

    @Test fun nothingIsListedWhenEveryPermissionIsHeld() {
        assertEquals(emptyList<ImportAccess>(), missingImportAccess(allGranted))
    }

    @Test fun eachMissingPermissionIsListedForAFeatureThatIsOn() {
        val state = allGranted.copy(
            scheduleStatus = ScheduleStatus.PermissionRequired,
            weather = WeatherState(status = WeatherStatus.PermissionRequired),
            media = MediaSnapshot(hasAccess = false),
        )
        assertEquals(listOf(ImportAccess.Calendar, ImportAccess.Weather, ImportAccess.Notifications), missingImportAccess(state))
    }

    @Test fun aFeatureThatIsOffNeedsNoPermission() {
        val state = LauncherUiState(
            settings = LauncherSettings(calendarAgenda = false, weatherEnabled = false, mediaPlayer = false, shortcutsFolders = noPreviews),
            scheduleStatus = ScheduleStatus.PermissionRequired,
            weather = WeatherState(status = WeatherStatus.PermissionRequired),
            media = MediaSnapshot(hasAccess = false),
        )
        assertEquals(emptyList<ImportAccess>(), missingImportAccess(state))
    }

    @Test fun notificationPreviewsAloneNeedNotificationAccess() {
        val state = allGranted.copy(settings = LauncherSettings(mediaPlayer = false), media = MediaSnapshot(hasAccess = false))
        assertEquals(listOf(ImportAccess.Notifications), missingImportAccess(state))
    }

    @Test fun gadgetbridgeWeatherNeedsNoBreezyPermission() {
        val state = allGranted.copy(
            settings = LauncherSettings(weatherEnabled = true,
                weatherConnection = WeatherConnectionPreferences(mode = WeatherConnectionMode.Gadgetbridge)),
            weather = WeatherState(status = WeatherStatus.PermissionRequired),
        )
        assertEquals(emptyList<ImportAccess>(), missingImportAccess(state))
    }
}
