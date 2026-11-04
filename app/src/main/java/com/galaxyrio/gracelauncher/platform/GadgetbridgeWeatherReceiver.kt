package com.galaxyrio.gracelauncher.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.galaxyrio.gracelauncher.data.weather.GadgetbridgeWeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Discoverable by Breezy Weather, WeatherMaster and other GenericWeather senders. */
class GadgetbridgeWeatherReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GadgetbridgeWeatherRepository.UPDATE_ACTION) return
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                withTimeout(8_000) { GadgetbridgeWeatherRepository.get(appContext).receive(intent) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The public broadcast is untrusted. Reject malformed input without replacing the cache.
                Log.w("GadgetbridgeWeather", "Unable to accept weather update")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
