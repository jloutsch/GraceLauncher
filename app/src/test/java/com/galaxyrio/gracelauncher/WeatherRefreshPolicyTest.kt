package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.weather.WeatherLocation
import com.galaxyrio.gracelauncher.data.weather.WeatherRefreshPolicy
import com.galaxyrio.gracelauncher.data.weather.WeatherSnapshot
import com.galaxyrio.gracelauncher.data.weather.WeatherState
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WeatherRefreshPolicyTest {
    private val location = WeatherLocation("first-city", "First city", ZoneId.of("UTC"))
    private val otherLocation = WeatherLocation("other-city", "Other city", ZoneId.of("UTC"))
    private val cached = WeatherState(
        status = WeatherStatus.Ready,
        snapshot = WeatherSnapshot(
            location = location,
            current = null,
            hourly = emptyList(),
            daily = emptyList(),
            updatedAt = Instant.parse("2026-11-15T08:00:00Z"),
            attribution = "Breezy Weather",
        ),
        locations = listOf(location),
        selectedLocationId = location.id,
    )

    @Test fun openingAgendaReusesCacheUntilTheExistingIntervalExpires() {
        val interval = WeatherRefreshPolicy.REFRESH_INTERVAL_MILLIS
        assertEquals(15 * 60_000L, interval)
        listOf(0L, 60_000L, interval - 1).forEach { elapsed ->
            assertFalse(shouldRead(elapsed = elapsed))
        }
        assertTrue(shouldRead(elapsed = interval))
    }

    @Test fun automaticEntriesDoNotRestartAnInFlightRead() {
        assertFalse(shouldRead(inProgress = true))
        assertFalse(shouldRead(weather = WeatherState(WeatherStatus.Loading), inProgress = true))
    }

    @Test fun manualRefreshAndChangedRequestsBypassTheInterval() {
        assertTrue(shouldRead(force = true, elapsed = 0))
        assertTrue(shouldRead(force = true, inProgress = true, elapsed = 0))
        assertTrue(shouldRead(sameRequest = false, inProgress = true, elapsed = 0))
    }

    @Test fun failedInitialReadWithoutCachedWeatherRemainsRetryable() {
        assertTrue(shouldRead(weather = WeatherState(WeatherStatus.Error), elapsed = 0))
    }

    @Test fun transientFailureKeepsTheLastSnapshotAndItsOriginalTimestamp() {
        listOf(WeatherStatus.Error, WeatherStatus.NoWeather).forEach { status ->
            val result = WeatherRefreshPolicy.retainLastSnapshot(
                WeatherState(status, locations = listOf(location), selectedLocationId = location.id), cached,
            )
            assertSame(cached.snapshot, result.snapshot)
            assertEquals(cached.snapshot!!.updatedAt, result.snapshot!!.updatedAt)
            assertEquals(status, result.status)
        }
    }

    @Test fun failureBeforeLocationLookupRetainsThePreviouslyResolvedFirstCity() {
        val result = WeatherRefreshPolicy.retainLastSnapshot(WeatherState(WeatherStatus.Error), cached)
        assertSame(cached.snapshot, result.snapshot)
        assertEquals(cached.locations, result.locations)
        assertEquals(location.id, result.selectedLocationId)
    }

    @Test fun repeatedFailuresKeepCachedWeatherAndContinueToRespectTheInterval() {
        var displayed = cached
        repeat(3) {
            displayed = WeatherRefreshPolicy.retainLastSnapshot(WeatherState(WeatherStatus.Error), displayed)
            assertSame(cached.snapshot, displayed.snapshot)
            assertFalse(shouldRead(weather = displayed, elapsed = 60_000L))
            assertTrue(shouldRead(weather = displayed))
        }
    }

    @Test fun successfulReadReplacesTheOldSnapshot() {
        val fresh = cached.copy(snapshot = cached.snapshot!!.copy(updatedAt = Instant.parse("2026-11-15T09:00:00Z")))
        assertSame(fresh, WeatherRefreshPolicy.retainLastSnapshot(fresh, cached))
    }

    @Test fun changedFirstLocationDoesNotShowAnotherCitysCachedWeather() {
        val result = WeatherState(
            WeatherStatus.NoWeather,
            locations = listOf(otherLocation, location),
            selectedLocationId = otherLocation.id,
        )
        assertSame(result, WeatherRefreshPolicy.retainLastSnapshot(result, cached))
    }

    @Test fun removedLocationDoesNotKeepItsOldWeather() {
        val result = WeatherState(WeatherStatus.Error, locations = listOf(otherLocation))
        assertSame(result, WeatherRefreshPolicy.retainLastSnapshot(result, cached))
    }

    @Test fun accessAndConfigurationFailuresDoNotReuseCachedWeather() {
        listOf(
            WeatherStatus.Disabled, WeatherStatus.PermissionRequired, WeatherStatus.NotInstalled,
            WeatherStatus.UnsupportedVersion, WeatherStatus.NoLocations,
        ).forEach { status ->
            val result = WeatherState(status)
            assertSame(result, WeatherRefreshPolicy.retainLastSnapshot(result, cached))
        }
    }

    @Test fun failureWithoutCacheForTheCurrentRequestStaysEmpty() {
        val result = WeatherState(WeatherStatus.Error)
        assertSame(result, WeatherRefreshPolicy.retainLastSnapshot(result, null))
    }

    private fun shouldRead(
        weather: WeatherState = cached,
        force: Boolean = false,
        sameRequest: Boolean = true,
        inProgress: Boolean = false,
        elapsed: Long = WeatherRefreshPolicy.REFRESH_INTERVAL_MILLIS,
    ) = WeatherRefreshPolicy.shouldRead(force, sameRequest, inProgress, weather, elapsed)
}
