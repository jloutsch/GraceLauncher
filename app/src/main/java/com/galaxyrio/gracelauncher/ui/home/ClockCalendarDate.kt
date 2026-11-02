package com.galaxyrio.gracelauncher.ui.home

import android.text.format.DateFormat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import java.text.AttributedCharacterIterator
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/** Keep locale-specific ordering and punctuation; field attributes identify each independently colored part. */
internal fun clockCalendarDate(now: Instant, locale: Locale, showWeekday: Boolean, showDate: Boolean,
    weekdayColor: Color, monthColor: Color, dayColor: Color): AnnotatedString {
    val skeleton = when {
        showWeekday && showDate -> "MMMEd"
        showDate -> "MMMd"
        showWeekday -> "EEE"
        else -> return AnnotatedString("")
    }
    val formatter = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    val fields = formatter.formatToCharacterIterator(Date.from(now))
    val text = buildString {
        var character = fields.first()
        while (character != AttributedCharacterIterator.DONE) {
            append(character)
            character = fields.next()
        }
    }
    return buildAnnotatedString {
        append(text)
        var start = fields.beginIndex
        while (start < fields.endIndex) {
            fields.setIndex(start)
            val end = fields.runLimit
            val attributes = fields.attributes
            val color = when {
                java.text.DateFormat.Field.DAY_OF_WEEK in attributes -> weekdayColor
                java.text.DateFormat.Field.MONTH in attributes -> monthColor
                java.text.DateFormat.Field.DAY_OF_MONTH in attributes -> dayColor
                else -> null
            }
            if (color != null) addStyle(SpanStyle(color = color), start - fields.beginIndex, end - fields.beginIndex)
            start = end
        }
    }
}
