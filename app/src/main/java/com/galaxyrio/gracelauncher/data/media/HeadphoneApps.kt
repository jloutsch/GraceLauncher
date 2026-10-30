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
    /** Show the apps as one horizontal App Stack instead of a list of app rows. */
    val asAppStack: Boolean = false,
) {
    fun toggle(key: String): HeadphoneAppsSettings =
        copy(appKeys = if (key in appKeys) appKeys - key else appKeys + key)

    fun encode(): String = JSONObject().put("enabled", enabled).put("apps", JSONArray(appKeys.distinct()))
        .put("asAppStack", asAppStack).toString()

    companion object {
        fun decode(value: String?): HeadphoneAppsSettings = runCatching {
            val json = JSONObject(value ?: "{}")
            val apps = json.optJSONArray("apps")
            HeadphoneAppsSettings(json.optBoolean("enabled", false),
                if (apps == null) emptyList() else (0 until apps.length())
                    .mapNotNull { (apps.opt(it) as? String)?.takeIf(String::isNotBlank) }.distinct(),
                json.optBoolean("asAppStack", false))
        }.getOrDefault(HeadphoneAppsSettings())
    }
}

/** Personal listening outputs; speakers, TVs and hearing aids never count. A car stereo that uses Bluetooth A2DP does. */
internal fun isHeadphoneOutput(type: Int): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET -> true
    else -> false
}

/** Read from the current outputs on every start and change, so a restart with headphones in still shows them. */
data class HeadphoneState(val connected: Boolean = false)

@MainThread
internal class HeadphoneMonitor(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(HeadphoneState())
    val state = _state.asStateFlow()
    private var registered = false
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { _state.value = HeadphoneState(connectedNow()) }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { _state.value = HeadphoneState(connectedNow()) }
    }

    fun start() {
        if (registered || audio == null) return
        _state.value = HeadphoneState(connectedNow())
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        registered = true
    }

    fun close() {
        if (!registered) return
        runCatching { audio?.unregisterAudioDeviceCallback(callback) }
        registered = false
    }

    private fun connectedNow(): Boolean = runCatching {
        audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any { isHeadphoneOutput(it.type) }
    }.getOrDefault(false)
}
