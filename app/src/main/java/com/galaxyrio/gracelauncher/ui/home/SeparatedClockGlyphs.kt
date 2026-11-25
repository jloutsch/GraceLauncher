package com.galaxyrio.gracelauncher.ui.home

import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.galaxyrio.gracelauncher.ui.theme.FontShadowMaskCache
import com.galaxyrio.gracelauncher.ui.theme.ResolvedFontShadow
import java.text.BreakIterator
import kotlin.math.abs

// Full stroke width relative to font size. Increase this to widen the transparent outline.
// Half of the stroke lies outside the glyph; 0.08f replaces the previous 0.044f.
internal const val CLOCK_CUTOUT_STROKE_EM = 0.08f

internal data class PositionedClockGlyph(
    val text: String,
    val bounds: RectF,
    val x: Float,
    val y: Float = 0f,
    val letterSpacingEm: Float = 0f,
    /** Logical hour, minute and second tens/ones, independent of omitted leading zeros. */
    val digitSlot: Int? = null,
    val fontScale: Float = 1f,
)

internal data class ClockGlyphDrawing(
    val glyphs: List<PositionedClockGlyph>,
    val bounds: RectF,
    val typeface: Typeface,
    val fontSizePx: Float,
    val lastBaseline: Float,
)

private data class ClockGlyphRun(val glyphs: List<PositionedClockGlyph>, val ink: ClockInkBox)

/** Immutable glyph layout for native text; no Text-layout callbacks or state writes. */
internal fun measureClockGlyphs(
    text: String,
    typeface: Typeface,
    fontSizePx: Float,
    digitGapPx: Float,
    groupGapPx: Float,
    stacked: Boolean,
    showColon: Boolean,
    centerLines: Boolean,
    maxWidth: Float,
    maxHeight: Float,
    equalWidthDigits: Boolean = false,
    centerDigits: Boolean = false,
    minuteScale: Float = 1f,
    secondScale: Float = 1f,
): ClockGlyphDrawing {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        fontFeatureSettings = "tnum"
    }
    fun inkBounds(value: String): RectF {
        // Outlines are used only to measure fractional ink bounds, never to render text.
        // getTextBounds rounds to integers and would introduce jumps while resizing.
        val path = Path()
        paint.getTextPath(value, 0, value.length, 0f, 0f, path)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        return bounds
    }
    fun run(value: String, firstDigitSlot: Int? = null, cellWidth: Float? = null,
        leadingEmptyCells: Int = 0, minimumCells: Int = 0, fontScale: Float = 1f): ClockGlyphRun {
        val boundaries = BreakIterator.getCharacterInstance().apply { setText(value) }
        val clusters = buildList {
            var start = boundaries.first()
            var end = boundaries.next()
            while (end != BreakIterator.DONE) {
                val cluster = value.substring(start, end)
                add(cluster to inkBounds(cluster))
                start = end
                end = boundaries.next()
            }
        }
        val cellCount = maxOf(minimumCells, leadingEmptyCells + clusters.size)
        val positions = clockDigitPositions(
            if (cellWidth != null) List(cellCount) { cellWidth } else clusters.map { it.second.width() },
            // Equal-width columns may overlap, but a narrow font or fitted size
            // must not move the ones column to the left of the tens column.
            if (cellWidth != null) digitGapPx.coerceAtLeast(-cellWidth) else digitGapPx,
        )
        var digitSlot = firstDigitSlot ?: 0
        val glyphs = clusters.mapIndexed { index, (cluster, bounds) ->
            val cellInset = if (cellWidth != null && centerDigits) (cellWidth - bounds.width()) / 2 else 0f
            val cellIndex = index + if (cellWidth != null) leadingEmptyCells else 0
            PositionedClockGlyph(cluster, bounds, positions[cellIndex] + cellInset - bounds.left,
                digitSlot = if (firstDigitSlot != null && cluster.codePoints().anyMatch(Character::isDigit)) digitSlot++ else null,
                fontScale = fontScale)
        }
        return ClockGlyphRun(glyphs, ClockInkBox(
            width = if (cellWidth != null) (positions.maxOrNull() ?: 0f) + cellWidth
                else glyphs.maxOfOrNull { it.x + it.bounds.right } ?: 0f,
            top = clusters.minOfOrNull { it.second.top } ?: 0f,
            bottom = clusters.maxOfOrNull { it.second.bottom } ?: 0f,
        ))
    }
    fun layout(size: Float): ClockGlyphDrawing {
        paint.textSize = size
        val separator = text.indexOf(':')
        var lastBaseline = 0f
        var reservedWidth: Float? = null
        val glyphs = if (separator >= 0) {
            val groups = text.split(':').take(3)
            val scales = listOf(1f, minuteScale.coerceIn(.2f, 1.5f), secondScale.coerceIn(.2f, 1.5f)).take(groups.size)
            val hourText = groups[0]
            val hourDigits = hourText.codePoints().filter(Character::isDigit).count().toInt()
            val missingHourDigits = (2 - hourDigits).coerceAtLeast(0)
            val cellWidth = if (equalWidthDigits) {
                paint.textSize = size * scales.max()
                // Decimal digits are contiguous within each Unicode digit set. Use
                // the rendered clock's localized set rather than the system locale.
                val zeroes = groups.joinToString("").codePoints().filter(Character::isDigit)
                    .map { it - Character.digit(it, 10) }.distinct().toArray()
                    .let { if (it.isEmpty()) intArrayOf('0'.code) else it }
                zeroes.maxOf { zero ->
                    (0..9).maxOf { digit -> inkBounds(String(Character.toChars(zero + digit))).width() }
                }
            } else null
            val runs = groups.mapIndexed { index, group ->
                paint.textSize = size * scales[index]
                run(group, firstDigitSlot = if (index == 0) missingHourDigits else index * 2,
                    cellWidth = cellWidth, leadingEmptyCells = if (index == 0) missingHourDigits else 0,
                    minimumCells = 2, fontScale = scales[index])
            }
            paint.textSize = size
            val colon = if (showColon && !stacked) run(":") else null
            val widest = runs.maxOf { it.ink.width }
            buildList {
                var x = 0f
                var baseline = 0f
                runs.forEachIndexed { index, current ->
                    if (index > 0) {
                        val previous = runs[index - 1]
                        if (stacked) baseline += previous.ink.bottom + groupGapPx - current.ink.top
                        else {
                            x += previous.ink.width
                            if (colon != null) addAll(colon.glyphs.map { it.copy(x = it.x + x + groupGapPx / 2) })
                            x += groupGapPx + (colon?.ink?.width ?: 0f)
                        }
                    }
                    val groupX = if (stacked && centerLines) (widest - current.ink.width) / 2 else x
                    addAll(current.glyphs.map { it.copy(x = it.x + groupX, y = baseline) })
                    lastBaseline = baseline
                    if (cellWidth != null) reservedWidth = if (stacked) widest else x + current.ink.width
                }
            }
        } else {
            // Keep contextual shaping for scripts with joining letters. Latin abbreviations
            // remain individual glyphs, including Sacramento's transparent separation.
            val needsJoining = text.any { it.isLetter() && Character.UnicodeScript.of(it.code) in joiningScripts }
            if (needsJoining) {
                paint.letterSpacing = digitGapPx / size
                val bounds = inkBounds(text)
                paint.letterSpacing = 0f
                listOf(PositionedClockGlyph(text, bounds, -bounds.left, letterSpacingEm = digitGapPx / size))
            } else run(text).glyphs
        }
        val bounds = RectF(
            glyphs.minOfOrNull { it.x + it.bounds.left } ?: 0f,
            glyphs.minOfOrNull { it.y + it.bounds.top } ?: 0f,
            glyphs.maxOfOrNull { it.x + it.bounds.right } ?: 0f,
            glyphs.maxOfOrNull { it.y + it.bounds.bottom } ?: 0f,
        )
        reservedWidth?.let { width ->
            // Retain empty leading/trailing cell space instead of cropping it to
            // the current glyphs; column positions stay fixed as digits change.
            bounds.left = minOf(0f, bounds.left)
            bounds.right = maxOf(width, bounds.right)
        }
        return ClockGlyphDrawing(glyphs, bounds, typeface, size, lastBaseline)
    }

    fun ClockGlyphDrawing.fits() = bounds.width() <= maxWidth && bounds.height() <= maxHeight
    val requested = fontSizePx.coerceAtLeast(1f)
    val full = layout(requested)
    if (full.fits()) return full
    // Only resize glyphs when space is constrained. Never scale either spacing setting:
    // both edge-to-edge gaps remain fixed even when fitting the clock to a narrow preview.
    var low = 1f
    var high = requested
    var best = layout(low)
    repeat(10) {
        val candidate = layout((low + high) / 2)
        if (candidate.fits()) {
            best = candidate
            low = candidate.fontSizePx
        } else high = candidate.fontSizePx
    }
    return best
}

internal fun DrawScope.drawClockGlyphs(drawing: ClockGlyphDrawing, color: Color, shadow: Shadow, separate: Boolean,
    digitColors: List<Color>? = null, spread: Float = 0f, shadowMask: FontShadowMaskCache? = null) {
    val canvas = drawContext.canvas.nativeCanvas
    if (shadowMask != null && shadow != Shadow.None) {
        shadowMask.draw(canvas, drawing to separate, drawing.bounds.width(), drawing.bounds.height(),
            drawing.fontSizePx * CLOCK_CUTOUT_STROKE_EM, ResolvedFontShadow(shadow, spread)) { target, expansion ->
            drawClockGlyphsOnCanvas(target, drawing, Color.White, Shadow.None, separate, null, expansion)
        }
    }
    drawClockGlyphsOnCanvas(canvas, drawing, color, if (shadowMask == null) shadow else Shadow.None, separate, digitColors)
}

private fun drawClockGlyphsOnCanvas(canvas: Canvas, drawing: ClockGlyphDrawing, color: Color, shadow: Shadow,
    separate: Boolean, digitColors: List<Color>?, spread: Float = 0f) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = drawing.typeface
        textSize = drawing.fontSizePx
        fontFeatureSettings = "tnum"
        this.color = color.toArgb()
        if (spread > 0f) {
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = spread * 2f
            strokeJoin = Paint.Join.ROUND
        }
        if (shadow != Shadow.None) {
            setShadowLayer(shadow.blurRadius, shadow.offset.x, shadow.offset.y, shadow.color.toArgb())
        }
    }
    val cutout = Paint(fill).apply {
        clearShadowLayer()
        // Enlarge the same native glyph instead of switching to a heavier font face:
        // a different weight can change its shape and no longer surround the fill evenly.
        style = Paint.Style.FILL_AND_STROKE
        this.color = android.graphics.Color.BLACK
        strokeWidth = drawing.fontSizePx * CLOCK_CUTOUT_STROKE_EM
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    val overflow = cutout.strokeWidth * (drawing.glyphs.maxOfOrNull { it.fontScale } ?: 1f) + spread +
        shadow.blurRadius + maxOf(abs(shadow.offset.x), abs(shadow.offset.y)) + 2f
    // Only separated text needs an isolated layer, so CLEAR cannot erase the wallpaper.
    val layer = if (separate) {
        canvas.saveLayer(-overflow, -overflow, drawing.bounds.width() + overflow, drawing.bounds.height() + overflow, null)
    } else canvas.save()
    try {
        drawing.glyphs.forEach { glyph ->
            val x = glyph.x - drawing.bounds.left
            val y = glyph.y - drawing.bounds.top
            fill.color = (glyph.digitSlot?.let { digitColors?.getOrNull(it) } ?: color).toArgb()
            fill.textSize = drawing.fontSizePx * glyph.fontScale
            fill.letterSpacing = glyph.letterSpacingEm
            if (separate) {
                cutout.textSize = fill.textSize
                cutout.strokeWidth = fill.textSize * CLOCK_CUTOUT_STROKE_EM
                cutout.letterSpacing = glyph.letterSpacingEm
                canvas.drawText(glyph.text, x, y, cutout)
            }
            canvas.drawText(glyph.text, x, y, fill)
        }
    } finally {
        canvas.restoreToCount(layer)
    }
}

private val joiningScripts = setOf(
    Character.UnicodeScript.ARABIC, Character.UnicodeScript.SYRIAC,
    Character.UnicodeScript.DEVANAGARI, Character.UnicodeScript.BENGALI,
    Character.UnicodeScript.TAMIL, Character.UnicodeScript.TELUGU,
    Character.UnicodeScript.MALAYALAM, Character.UnicodeScript.KANNADA,
    Character.UnicodeScript.THAI, Character.UnicodeScript.MYANMAR,
)
