package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.isBackupFileEntry
import com.galaxyrio.gracelauncher.data.scrubBackupJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherBackupScrubTest {
    @Test fun clearsWidgetIdsAndTheirProviderLabelsAtAnyDepth() {
        val scrubbed = JSONObject(scrubBackupJson(
            """{"top":{"widgetId":7,"widgetProvider":"p","widgetLabel":"l","topOffsetDp":4},""" +
                """"members":[{"widget":{"widgetId":8,"widgetProvider":"q"}},{"key":"app"}]}"""))
        val top = scrubbed.getJSONObject("top")
        assertEquals(-1, top.getInt("widgetId"))
        assertFalse(top.has("widgetProvider"))
        assertFalse(top.has("widgetLabel"))
        assertEquals(4, top.getInt("topOffsetDp"))
        val widget = scrubbed.getJSONArray("members").getJSONObject(0).getJSONObject("widget")
        assertEquals(-1, widget.getInt("widgetId"))
        assertFalse(widget.has("widgetProvider"))
        assertEquals("app", scrubbed.getJSONArray("members").getJSONObject(1).getString("key"))
    }

    @Test fun keepsJsonArraysAndUnparseableTextUnchanged() {
        assertEquals("[1,2]", JSONArray(scrubBackupJson("[1,2]")).toString())
        assertEquals("not json {", scrubBackupJson("not json {"))
    }

    @Test fun acceptsOnlyPlainFileNamesInTheIconAndFontFolders() {
        assertTrue(isBackupFileEntry("files/item_icons/icon-1.png"))
        assertTrue(isBackupFileEntry("files/clock_fonts/Font_1.ttf"))
        assertFalse(isBackupFileEntry("files/item_icons/../../databases/x.db"))
        assertFalse(isBackupFileEntry("files/item_icons/.."))
        assertFalse(isBackupFileEntry("files/item_icons/a\\b"))
        assertFalse(isBackupFileEntry("files/other_dir/file.png"))
        assertFalse(isBackupFileEntry("files/item_icons/nested/file.png"))
        assertFalse(isBackupFileEntry("/absolute/item_icons/file.png"))
        assertFalse(isBackupFileEntry("files/item_icons/"))
    }
}
