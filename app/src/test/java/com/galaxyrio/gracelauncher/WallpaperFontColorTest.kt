package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.theme.wallpaperIsDark
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperFontColorTest {
    @Test fun platformTextContrastHintTakesPriorityOverPrimaryColor() {
        assertFalse(wallpaperIsDark(supportsDarkText = true, primaryLuminance = 0.1f))
        assertTrue(wallpaperIsDark(supportsDarkText = false, primaryLuminance = 0.95f))
    }

    @Test fun olderAndroidRequiresClearlyLightMetadataAndMissingMetadataDefaultsDark() {
        assertFalse(wallpaperIsDark(supportsDarkText = null, primaryLuminance = 0.9f))
        assertTrue(wallpaperIsDark(supportsDarkText = null, primaryLuminance = 0.5f))
        assertTrue(wallpaperIsDark(supportsDarkText = null, primaryLuminance = null))
        assertTrue(wallpaperIsDark(supportsDarkText = null, primaryLuminance = Float.NaN))
    }
}
