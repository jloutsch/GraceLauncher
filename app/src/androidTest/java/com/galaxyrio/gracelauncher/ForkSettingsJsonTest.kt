package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.WeatherAppKeyField
import com.galaxyrio.gracelauncher.data.WidgetStackFoldersField
import com.galaxyrio.gracelauncher.data.stringField
import com.galaxyrio.gracelauncher.data.stringSetField
import com.galaxyrio.gracelauncher.data.toJsonArrayOrNull
import com.galaxyrio.gracelauncher.data.withField
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForkSettingsJsonTest {
    @Test fun forkFieldsRideAlongUpstreamJsonWithoutChangingIt() {
        val clock = """{"showWeather":false,"style":"digital"}"""
        val withApp = clock.withField(WeatherAppKeyField, "w/w.Weather")
        assertEquals("w/w.Weather", stringField(withApp, WeatherAppKeyField))
        assertEquals(false, JSONObject(withApp).getBoolean("showWeather"))
        assertEquals("digital", JSONObject(withApp).getString("style"))
        assertFalse(JSONObject(withApp.withField(WeatherAppKeyField, null)).has(WeatherAppKeyField))

        val folders = "{}".withField(WidgetStackFoldersField, setOf("b", "a").toJsonArrayOrNull())
        assertEquals(setOf("a", "b"), stringSetField(folders, WidgetStackFoldersField))
        assertNull(emptySet<String>().toJsonArrayOrNull())
    }

    @Test fun missingOrBrokenJsonReadsAsUnset() {
        assertNull(stringField(null, WeatherAppKeyField))
        assertNull(stringField("not json", WeatherAppKeyField))
        assertEquals(emptySet<String>(), stringSetField(null, WidgetStackFoldersField))
        assertEquals(emptySet<String>(), stringSetField("""{"widgetStackFolderIds":"x"}""", WidgetStackFoldersField))
        assertEquals(emptySet<String>(), LauncherSettings().widgetStackFolderIds)
    }
}
