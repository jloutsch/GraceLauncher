package com.galaxyrio.gracelauncher.data

import org.json.JSONObject

enum class CalendarRefreshMode(val intervalMillis: Long?) {
    EveryMinute(60_000L),
    Every15Minutes(900_000L),
    Hourly(3_600_000L),
    OnHomeEntry(null),
    Manual(null),
}

data class CalendarPreferences(
    val daysAhead: Int = 14,
    val refreshMode: CalendarRefreshMode = CalendarRefreshMode.EveryMinute,
) {
    fun normalized() = copy(daysAhead = daysAhead.coerceIn(DaysAheadRange))

    fun encode(): String = normalized().let { value ->
        JSONObject().put("daysAhead", value.daysAhead).put("refreshMode", value.refreshMode.name).toString()
    }

    companion object {
        val DaysAheadOptions = listOf(1, 3, 7, 14, 30, 90)
        val DaysAheadRange = DaysAheadOptions.first()..DaysAheadOptions.last()

        fun decode(value: String?): CalendarPreferences = runCatching {
            val json = JSONObject(value ?: "{}")
            val defaults = CalendarPreferences()
            CalendarPreferences(
                daysAhead = json.optInt("daysAhead", defaults.daysAhead),
                refreshMode = CalendarRefreshMode.entries.firstOrNull { it.name == json.optString("refreshMode") }
                    ?: defaults.refreshMode,
            ).normalized()
        }.getOrDefault(CalendarPreferences())
    }
}
