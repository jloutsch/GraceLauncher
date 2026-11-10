package com.galaxyrio.gracelauncher.ui.weather

import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.CalendarPreferences
import com.galaxyrio.gracelauncher.data.agendaDays
import java.time.LocalDate
import java.time.ZoneId

/** Weather fills gaps between calendar dates without shortening the calendar's horizon. */
internal fun weatherAgendaDays(
    events: List<ScheduleEvent>,
    forecastDates: List<LocalDate>,
    today: LocalDate,
    forecastDayCount: Int,
    zone: ZoneId = ZoneId.systemDefault(),
    calendarDayCount: Int = 14,
    deprioritizeAllDay: Boolean = true,
): Map<LocalDate, List<ScheduleEvent>> {
    // The provider reads a rolling now + N days window, whose closing date can contain events.
    // agendaDays adds only dates occupied by those retrieved events (plus today).
    val days = agendaDays(events, today, zone,
        dayCount = calendarDayCount.coerceIn(CalendarPreferences.DaysAheadRange).toLong() + 1,
        deprioritizeAllDay = deprioritizeAllDay).toSortedMap()
    val weatherEnd = today.plusDays(forecastDayCount.coerceIn(1, 14).toLong())
    forecastDates.filter { it >= today && it < weatherEnd }.forEach { date ->
        days.putIfAbsent(date, emptyList())
    }
    return days
}
