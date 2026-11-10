package com.galaxyrio.gracelauncher.platform

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// CalendarContract does not expose a public constant for this provider MIME type.
private const val CALENDAR_EVENT_MIME_TYPE = "vnd.android.cursor.item/event"

private fun calendarEventIntent(id: Long): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(
    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), CALENDAR_EVENT_MIME_TYPE,
)

/** Discover apps in this profile; calendar provider IDs are not shared across profiles. */
@Suppress("DEPRECATION")
internal suspend fun calendarAppPackages(context: Context): Set<String> = withContext(Dispatchers.IO) {
    val manager = context.packageManager
    val eventHandlers = manager.queryIntentActivities(calendarEventIntent(0L), PackageManager.MATCH_DEFAULT_ONLY)
    val calendarHandlers = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR), 0)
    val dateHandlers = manager.queryIntentActivities(Intent(Intent.ACTION_VIEW,
        CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath("0").build()), PackageManager.MATCH_DEFAULT_ONLY)
    (eventHandlers + calendarHandlers + dateHandlers).mapNotNull { result ->
        result.activityInfo?.takeIf { it.exported && it.enabled && it.applicationInfo.enabled }?.packageName
    }.toSet()
}

/** Preserve Android's default resolver unless the user explicitly selected a calendar app. */
internal fun openCalendarEvent(context: Context, event: ScheduleEvent, appPackage: String?): Boolean {
    val intent = calendarEventIntent(event.id)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startsAt.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endsAt.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.isAllDay)
        .apply { if (appPackage != null) setPackage(appPackage) }
    if (runCatching { context.startActivity(intent) }.isSuccess) return true
    if (appPackage == null) return false
    // Some calendar apps expose a home screen without accepting provider event links.
    return runCatching {
        val home = context.packageManager.getLaunchIntentForPackage(appPackage) ?: return@runCatching false
        context.startActivity(home)
        true
    }.getOrDefault(false)
}
