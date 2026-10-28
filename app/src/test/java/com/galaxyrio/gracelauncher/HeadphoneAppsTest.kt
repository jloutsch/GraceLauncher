package com.galaxyrio.gracelauncher

import android.media.AudioDeviceInfo
import com.galaxyrio.gracelauncher.data.media.HeadphoneAppsSettings
import com.galaxyrio.gracelauncher.data.media.isHeadphoneOutput
import org.junit.Assert.*
import org.junit.Test

class HeadphoneAppsTest {
    @Test fun onlyPersonalListeningOutputsCountAsHeadphones() {
        listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET).forEach { assertTrue(isHeadphoneOutput(it)) }
        listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HEARING_AID)
            .forEach { assertFalse(isHeadphoneOutput(it)) }
    }

    @Test fun toggleKeepsPickOrderAndRemovesExistingApps() {
        val picked = HeadphoneAppsSettings().toggle("b").toggle("a").toggle("c")
        assertEquals(listOf("b", "a", "c"), picked.appKeys)
        assertEquals(listOf("b", "c"), picked.toggle("a").appKeys)
        assertFalse(picked.enabled)
    }
}
