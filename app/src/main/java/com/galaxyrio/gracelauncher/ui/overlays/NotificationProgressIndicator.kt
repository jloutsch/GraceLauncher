package com.galaxyrio.gracelauncher.ui.overlays

import android.graphics.drawable.Icon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.notifications.NotificationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberNotificationImage(icon: Icon?): ImageBitmap? {
    if (icon == null) return null
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, icon, context) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { icon.loadDrawable(context)?.toBitmap(120, 120)?.asImageBitmap() }.getOrNull()
        }
    }.value
}

@Composable
internal fun NotificationProgressIndicator(progress: NotificationProgress, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.notification_progress_label)
    val accessible = modifier.testTag("notification_progress").semantics {
        contentDescription = label
        progressBarRangeInfo = if (progress.indeterminate) ProgressBarRangeInfo.Indeterminate
            else ProgressBarRangeInfo(progress.fraction, 0f..1f)
    }
    if (progress.indeterminate) {
        LinearProgressIndicator(accessible.fillMaxWidth().padding(vertical = 10.dp))
    } else if (!progress.styled) {
        LinearProgressIndicator(progress = { progress.fraction }, modifier = accessible.fillMaxWidth().padding(vertical = 10.dp))
    } else {
        val start = rememberNotificationImage(progress.startIcon)
        val end = rememberNotificationImage(progress.endIcon)
        val tracker = rememberNotificationImage(progress.trackerIcon)
        val tint = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Row(accessible.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (start != null) Image(start, null, Modifier.size(24.dp), contentScale = ContentScale.Crop)
            BoxWithConstraints(Modifier.weight(1f).height(28.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val inset = if (tracker != null) 12.dp.toPx().coerceAtMost(size.width / 2f) else 4.dp.toPx()
                    val length = (size.width - inset * 2).coerceAtLeast(0f)
                    fun x(fraction: Float) = inset + length * if (rtl) 1f - fraction else fraction
                    val y = size.height / 2f
                    val height = 6.dp.toPx()
                    drawRoundRect(track, Offset(inset, y - height / 2), Size(length, height), CornerRadius(height / 2))
                    fun color(value: Int) = if (value == 0) tint else Color(value)
                    fun section(from: Float, to: Float, value: Color) {
                        if (to <= from) return
                        val left = minOf(x(from), x(to))
                        val gap = 1.dp.toPx()
                        drawRoundRect(value, Offset(left + gap, y - height / 2),
                            Size((length * (to - from) - gap * 2).coerceAtLeast(0f), height), CornerRadius(height / 2))
                    }
                    var at = 0f
                    val segments = progress.segments.ifEmpty { listOf(com.galaxyrio.gracelauncher.data.notifications.NotificationProgressSegment(progress.max)) }
                    segments.forEach { segment ->
                        val next = (at + segment.length.coerceAtLeast(0).toFloat() / progress.max.coerceAtLeast(1)).coerceIn(at, 1f)
                        val paint = color(segment.color)
                        if (progress.styledByProgress) {
                            section(at, minOf(next, progress.fraction), paint)
                            section(maxOf(at, progress.fraction), next, paint.copy(alpha = paint.alpha * .28f))
                        } else section(at, next, paint)
                        at = next
                    }
                    progress.points.forEach { point ->
                        val fraction = (point.position.toFloat() / progress.max.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val paint = color(point.color)
                        drawCircle(if (progress.styledByProgress && fraction > progress.fraction) paint.copy(alpha = .4f) else paint,
                            5.dp.toPx(), Offset(x(fraction), y))
                    }
                    if (tracker == null) drawCircle(tint, 5.dp.toPx(), Offset(x(progress.fraction), y))
                }
                if (tracker != null) Image(tracker, null,
                    Modifier.align(Alignment.CenterStart).offset(x = (maxWidth - 24.dp).coerceAtLeast(0.dp) * progress.fraction)
                        .size(24.dp), contentScale = ContentScale.Fit)
            }
            if (end != null) Image(end, null, Modifier.size(24.dp), contentScale = ContentScale.Crop)
        }
    }
}
