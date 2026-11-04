package com.galaxyrio.gracelauncher.data.weather

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Push-only weather input. No Breezy installation or provider permission is required. */
internal class GadgetbridgeWeatherRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events = updates.asSharedFlow()
    private val cacheFile = AtomicFile(File(appContext.noBackupFilesDir, "gadgetbridge_weather.json"))
    private data class CachedWeather(val payload: String, val receivedAt: Instant)
    private var cacheLoaded = false
    private var cached: CachedWeather? = null

    suspend fun receive(intent: Intent): Unit = withContext(Dispatchers.IO) {
        if (intent.action != UPDATE_ACTION) return@withContext
        mutex.withLock {
            // Use persisted settings even when a manifest broadcast starts a fresh app process.
            if (!isSelected()) return@withLock
            val payload = GadgetbridgeWeatherParser.decodePayload(
                gzip = intent.getByteArrayExtra("WeatherGz"),
                legacy = intent.getStringExtra("WeatherJson"),
                secondary = intent.getStringExtra("WeatherSecondaryJson"),
            ) ?: return@withLock
            val receivedAt = Instant.now()
            if (GadgetbridgeWeatherParser.snapshots(payload, "c", receivedAt).isEmpty()) return@withLock
            currentCoroutineContext().ensureActive()
            if (!isSelected()) return@withLock
            val entry = CachedWeather(payload, receivedAt)
            val bytes = JSONObject().put("receivedAt", receivedAt.toEpochMilli())
                .put("weather", JSONArray(payload)).toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_CACHE_BYTES) { "Gadgetbridge cache exceeds size limit" }
            val output = cacheFile.startWrite()
            try {
                output.write(bytes)
                cacheFile.finishWrite(output)
            } catch (error: Exception) {
                cacheFile.failWrite(output)
                throw error
            }
            cached = entry
            cacheLoaded = true
            updates.tryEmit(Unit)
            Unit
        }
    }

    suspend fun load(preferences: WeatherConnectionPreferences): WeatherState = withContext(Dispatchers.IO) {
        try {
            val entry = mutex.withLock {
                if (!cacheLoaded) {
                    cached = readCache()
                    cacheLoaded = true
                }
                cached
            } ?: return@withContext WeatherState(WeatherStatus.NoWeather)
            val snapshots = GadgetbridgeWeatherParser.snapshots(entry.payload, preferences.temperatureUnit.resolvedUnit(), entry.receivedAt)
            val selected = snapshots.firstOrNull { it.location.id == preferences.gadgetbridgeLocationId } ?: snapshots.firstOrNull()
            WeatherState(
                status = if (selected == null) WeatherStatus.NoWeather else WeatherStatus.Ready,
                snapshot = selected,
                locations = snapshots.map(WeatherSnapshot::location),
                selectedLocationId = selected?.location?.id,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            WeatherState(WeatherStatus.Error)
        }
    }

    private suspend fun isSelected(): Boolean {
        val settings = LauncherDatabase.getInstance(appContext).settingsDao().readSettings() ?: return false
        return settings.weatherEnabled &&
            WeatherConnectionPreferences.decode(settings.weatherConnectionJson).mode == WeatherConnectionMode.Gadgetbridge
    }

    private fun readCache(): CachedWeather? = try {
        cacheFile.openRead().use { input ->
            require(input.channel.size() <= MAX_CACHE_BYTES) { "Gadgetbridge cache exceeds size limit" }
            val json = JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
            CachedWeather(json.getJSONArray("weather").toString(), Instant.ofEpochMilli(json.getLong("receivedAt")))
        }
    } catch (_: FileNotFoundException) {
        null
    }

    companion object {
        const val UPDATE_ACTION = "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
        private const val MAX_CACHE_BYTES = GadgetbridgeWeatherParser.MAX_PAYLOAD_BYTES + 1024
        @Volatile private var instance: GadgetbridgeWeatherRepository? = null

        fun get(context: Context): GadgetbridgeWeatherRepository = instance ?: synchronized(this) {
            instance ?: GadgetbridgeWeatherRepository(context).also { instance = it }
        }
    }
}
