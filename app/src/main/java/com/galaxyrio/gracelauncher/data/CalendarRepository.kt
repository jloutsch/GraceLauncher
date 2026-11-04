package com.galaxyrio.gracelauncher.data

import android.content.Context
import android.os.SystemClock
import android.provider.CalendarContract
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CalendarSnapshot(
    val daysAhead: Int,
    val events: List<ScheduleEvent>,
    val refreshedAtNanos: Long,
)

class CalendarRepository(private val context: Context) {
    // HOME and the separate Settings Activity share explicit refresh results.
    val snapshots = sharedSnapshots.asStateFlow()

    fun clearSnapshot() {
        snapshotGeneration.incrementAndGet()
        sharedSnapshots.value = null
    }

    suspend fun refresh(daysAhead: Int): CalendarSnapshot? {
        val days = daysAhead.coerceIn(CalendarPreferences.DaysAheadRange)
        val requestedAt = SystemClock.elapsedRealtimeNanos()
        val generation = snapshotGeneration.get()
        return readMutex.withLock {
            if (generation != snapshotGeneration.get()) return@withLock null
            // A concurrent caller can use the same read once it has completed.
            sharedSnapshots.value?.takeIf {
                it.daysAhead == days && it.refreshedAtNanos >= requestedAt
            }?.let { return@withLock it }
            val events = upcomingEvents(days)
            if (generation != snapshotGeneration.get()) return@withLock null
            CalendarSnapshot(days, events, SystemClock.elapsedRealtimeNanos()).also {
                sharedSnapshots.value = it
            }
        }
    }

    private suspend fun upcomingEvents(daysAhead: Int): List<ScheduleEvent> = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val horizon = now.plus(Duration.ofDays(daysAhead.toLong()))
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )

        CalendarContract.Instances.query(
            context.contentResolver,
            projection,
            // Include UTC all-day instances that may still be today in the user's zone.
            now.minus(Duration.ofDays(1)).toEpochMilli(),
            horizon.toEpochMilli(),
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val colorIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_COLOR)
            val displayColorIndex = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
            val calendarNameIndex = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

            buildList {
                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleIndex)?.trim().orEmpty()
                    if (title.isBlank()) continue
                    val calendarColor = if (cursor.isNull(colorIndex)) null else cursor.getInt(colorIndex)
                    add(
                        ScheduleEvent(
                            id = cursor.getLong(idIndex),
                            title = title,
                            startsAt = Instant.ofEpochMilli(cursor.getLong(beginIndex)),
                            endsAt = Instant.ofEpochMilli(cursor.getLong(endIndex)),
                            isAllDay = cursor.getInt(allDayIndex) != 0,
                            location = cursor.getString(locationIndex)?.takeIf(String::isNotBlank),
                            calendarColor = calendarColor,
                            displayColor = if (displayColorIndex >= 0 && !cursor.isNull(displayColorIndex))
                                cursor.getInt(displayColorIndex) else calendarColor,
                            calendarName = if (calendarNameIndex >= 0) cursor.getString(calendarNameIndex)?.trim()
                                ?.takeIf(String::isNotBlank) else null,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private companion object {
        val sharedSnapshots = MutableStateFlow<CalendarSnapshot?>(null)
        val snapshotGeneration = AtomicLong()
        val readMutex = Mutex()
    }
}
