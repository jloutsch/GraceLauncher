package com.galaxyrio.gracelauncher.data.media

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Apps offered on the home screen when headphones connect, in the order the user picked them. */
data class HeadphoneAppsSettings(
    val enabled: Boolean = false,
    val appKeys: List<String> = emptyList(),
) {
    fun toggle(key: String): HeadphoneAppsSettings =
        copy(appKeys = if (key in appKeys) appKeys - key else appKeys + key)

    fun encode(): String = JSONObject().put("enabled", enabled).put("apps", JSONArray(appKeys.distinct())).toString()

    companion object {
        fun decode(value: String?): HeadphoneAppsSettings = runCatching {
            val json = JSONObject(value ?: "{}")
            val apps = json.optJSONArray("apps")
            HeadphoneAppsSettings(json.optBoolean("enabled", false),
                if (apps == null) emptyList() else (0 until apps.length())
                    .mapNotNull { (apps.opt(it) as? String)?.takeIf(String::isNotBlank) }.distinct())
        }.getOrDefault(HeadphoneAppsSettings())
    }
}

/** Personal listening outputs only: speakers, cars, TVs and hearing aids never trigger the list. */
internal fun isHeadphoneOutput(type: Int): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET -> true
    else -> false
}

/** [connections] counts new headphone connections; it never counts outputs present at registration. */
data class HeadphoneState(val connected: Boolean = false, val connections: Long = 0)

@MainThread
internal class HeadphoneMonitor(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(HeadphoneState())
    val state = _state.asStateFlow()
    // Registration replays every current output through onAudioDevicesAdded. Remember
    // those so restarting the launcher with headphones already in does not count them.
    private val known = mutableSetOf<Int>()
    private var registered = false
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            val fresh = added.filter { it.isSink && isHeadphoneOutput(it.type) && known.add(it.id) }
            _state.value = HeadphoneState(connected = connectedNow(),
                connections = _state.value.connections + if (fresh.isEmpty()) 0 else 1)
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            removed.forEach { known.remove(it.id) }
            _state.value = _state.value.copy(connected = connectedNow())
        }
    }

    fun start() {
        if (registered || audio == null) return
        val current = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrDefault(emptyArray())
            .filter { isHeadphoneOutput(it.type) }
        known += current.map { it.id }
        _state.value = _state.value.copy(connected = current.isNotEmpty())
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        registered = true
    }

    fun close() {
        if (!registered) return
        runCatching { audio?.unregisterAudioDeviceCallback(callback) }
        registered = false
        known.clear()
    }

    private fun connectedNow(): Boolean = runCatching {
        audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any { isHeadphoneOutput(it.type) }
    }.getOrDefault(false)
}
