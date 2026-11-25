package com.galaxyrio.gracelauncher.data.weather

internal object WeatherRefreshPolicy {
    const val REFRESH_INTERVAL_MILLIS = 15 * 60_000L

    fun shouldRead(
        force: Boolean,
        sameRequest: Boolean,
        readInProgress: Boolean,
        weather: WeatherState,
        elapsedSinceLastRead: Long,
    ): Boolean {
        if (force || !sameRequest) return true
        if (readInProgress) return false
        // A failed background read with usable cached data must also respect the interval.
        return weather.snapshot == null || elapsedSinceLastRead >= REFRESH_INTERVAL_MILLIS
    }

    /** The caller supplies a cache only when the source, requested location and units match. */
    fun retainLastSnapshot(result: WeatherState, cached: WeatherState?): WeatherState {
        val snapshot = cached?.snapshot ?: return result
        if (result.snapshot != null ||
            (result.status != WeatherStatus.Error && result.status != WeatherStatus.NoWeather)
        ) return result

        // The first-location option can resolve to a different city after a provider update.
        if (result.selectedLocationId != null && result.selectedLocationId != snapshot.location.id) return result
        if (result.locations.isNotEmpty() && result.locations.none { it.id == snapshot.location.id }) return result

        // Keep the failure status and the snapshot's original timestamp; this is still old data.
        return result.copy(
            snapshot = snapshot,
            locations = result.locations.ifEmpty { cached.locations },
            selectedLocationId = snapshot.location.id,
        )
    }
}
