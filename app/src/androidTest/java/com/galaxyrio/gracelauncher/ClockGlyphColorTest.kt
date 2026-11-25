package com.galaxyrio.gracelauncher

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.ui.home.ClockGlyphDrawing
import com.galaxyrio.gracelauncher.ui.home.drawClockGlyphs
import com.galaxyrio.gracelauncher.ui.home.measureClockGlyphs
import kotlin.math.ceil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockGlyphColorTest {
    @Test fun verticalSecondsKeepTheirSlotsAndUseIndependentSizes() {
        val result = measureClockGlyphs("09:15:37", Typeface.DEFAULT, 80f, 0f, 8f,
            stacked = true, showColon = true, centerLines = false, maxWidth = 1000f, maxHeight = 1000f,
            minuteScale = .5f, secondScale = 1.5f)
        assertEquals((0..5).toList(), result.glyphs.map { it.digitSlot })
        assertEquals(listOf(1f, 1f, .5f, .5f, 1.5f, 1.5f), result.glyphs.map { it.fontScale })
        val rows = result.glyphs.chunked(2)
        rows.zipWithNext().forEach { (above, below) ->
            assertEquals(8f, below.minOf { it.y + it.bounds.top } - above.maxOf { it.y + it.bounds.bottom }, .01f)
        }
    }

    @Test fun equalCellsKeepHorizontalWidthStableAndCenterDifferentVerticalSizes() {
        fun equal(text: String, stacked: Boolean, center: Boolean) = measureClockGlyphs(text, Typeface.SERIF, 80f, 0f, 8f,
            stacked = stacked, showColon = false, centerLines = false, maxWidth = 1000f, maxHeight = 1000f,
            equalWidthDigits = true, centerDigits = center, minuteScale = .5f, secondScale = 1.5f)
        assertEquals(equal("11:11:11", false, false).bounds.width(), equal("88:88:88", false, false).bounds.width(), .01f)
        val rows = equal("19:51:37", true, true).glyphs.chunked(2)
        for (column in 0..1) {
            val centers = rows.map { row -> row[column].let { it.x + it.bounds.centerX() } }
            assertEquals(centers.first(), centers[1], .01f)
            assertEquals(centers.first(), centers[2], .01f)
        }
    }

    @Test fun digitSlotsRemainStableWithoutLeadingZeroAndWithLocalizedDigits() {
        assertEquals(listOf(0, 1, null, 2, 3), drawing("09:05").glyphs.map { it.digitSlot })
        assertEquals(listOf(1, null, 2, 3), drawing("٩:٠٥").glyphs.map { it.digitSlot })
        assertEquals(listOf(1, 2, 3), drawing("9:05", stacked = true).glyphs.map { it.digitSlot })
        val weekday = drawing("الأربعاء")
        assertEquals("Joining weekday scripts keep their complete shaped run", 1, weekday.glyphs.size)
        assertTrue(weekday.glyphs.all { it.digitSlot == null })
    }

    @Test fun recoloringChangesOnlyGlyphFillAndPreservesSeparatedInkCoverage() {
        val drawing = drawing("9:05")
        val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Magenta)
        val original = pixels(drawing, null)
        val recolored = pixels(drawing, colors)
        assertArrayEquals(original.map { it ushr 24 }.toIntArray(), recolored.map { it ushr 24 }.toIntArray())
        val opaqueColors = recolored.filter { it ushr 24 == 255 }.toSet()
        assertTrue(opaqueColors.containsAll(listOf(Color.Green, Color.Blue, Color.Magenta, Color.Black).map { it.toArgb() }))
        assertFalse("Omitted hour tens must not shift the remaining color slots", Color.Red.toArgb() in opaqueColors)
        assertEquals(setOf(Color.Black.toArgb()), original.filter { it ushr 24 == 255 }.toSet())
    }

    private fun drawing(text: String, stacked: Boolean = false) = measureClockGlyphs(
        text, Typeface.DEFAULT, fontSizePx = 80f, digitGapPx = -3f, groupGapPx = 8f,
        stacked = stacked, showColon = true, centerLines = false, maxWidth = 1000f, maxHeight = 1000f,
    )

    private fun pixels(drawing: ClockGlyphDrawing, colors: List<Color>?): IntArray {
        val width = ceil(drawing.bounds.width()).toInt().coerceAtLeast(1)
        val height = ceil(drawing.bounds.height()).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(width.toFloat(), height.toFloat())) {
            drawClockGlyphs(drawing, Color.Black, Shadow.None, separate = true, digitColors = colors)
        }
        return IntArray(width * height).also {
            bitmap.getPixels(it, 0, width, 0, 0, width, height)
            bitmap.recycle()
        }
    }
}
