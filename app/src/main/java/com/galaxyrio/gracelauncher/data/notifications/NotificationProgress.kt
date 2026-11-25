package com.galaxyrio.gracelauncher.data.notifications

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build

data class NotificationProgressSegment(val length: Int, val color: Int = 0)
data class NotificationProgressPoint(val position: Int, val color: Int = 0)

data class NotificationProgress(
    val value: Int,
    val max: Int,
    val indeterminate: Boolean = false,
    val styled: Boolean = false,
    val styledByProgress: Boolean = true,
    val segments: List<NotificationProgressSegment> = emptyList(),
    val points: List<NotificationProgressPoint> = emptyList(),
    val startIcon: Icon? = null,
    val endIcon: Icon? = null,
    val trackerIcon: Icon? = null,
) {
    val fraction: Float get() = (value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
}

internal fun notificationProgress(notification: Notification, context: Context?): NotificationProgress? {
    val extras = notification.extras ?: return null
    if (Build.VERSION.SDK_INT >= 36 && context != null &&
        extras.getString(Notification.EXTRA_TEMPLATE) == Notification.ProgressStyle::class.java.name) {
        // Recover only this style, once per posted update. Never inflate a RemoteViews or mutate the source.
        val style = runCatching {
            Notification.Builder.recoverBuilder(context, notification.clone()).style as? Notification.ProgressStyle
        }.getOrNull()
        if (style != null) return NotificationProgress(
            style.progress, style.progressMax.coerceAtLeast(1), style.isProgressIndeterminate,
            styled = true, styledByProgress = style.isStyledByProgress,
            segments = style.progressSegments.take(128).map { NotificationProgressSegment(it.length, it.color) },
            points = style.progressPoints.take(128).map { NotificationProgressPoint(it.position, it.color) },
            startIcon = style.progressStartIcon, endIcon = style.progressEndIcon, trackerIcon = style.progressTrackerIcon,
        )
    }
    val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
    val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
    return if (max > 0 || indeterminate) NotificationProgress(
        extras.getInt(Notification.EXTRA_PROGRESS, 0).coerceIn(0, max.coerceAtLeast(1)),
        max.coerceAtLeast(1), indeterminate,
    ) else null
}
