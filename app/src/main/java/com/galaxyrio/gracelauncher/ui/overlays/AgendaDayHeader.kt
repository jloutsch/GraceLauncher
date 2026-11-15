package com.galaxyrio.gracelauncher.ui.overlays

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.data.weather.WeatherCurrent
import com.galaxyrio.gracelauncher.data.weather.WeatherDay
import com.galaxyrio.gracelauncher.data.weather.WeatherSnapshot
import com.galaxyrio.gracelauncher.ui.home.ClockMetadataLayout
import com.galaxyrio.gracelauncher.ui.weather.DailyWeather
import com.galaxyrio.gracelauncher.ui.weather.dailyWeatherFields
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
internal fun AgendaDayHeader(date: LocalDate, today: LocalDate, forecast: WeatherDay?, current: WeatherCurrent?,
    appearance: AgendaAppearance, weatherEnabled: Boolean,
    snapshot: WeatherSnapshot? = null,
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMEd"), locale) }
    val compactDateFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }
    val numericDateFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md"), locale) }
    val weatherFields = appearance.dayWeatherOrder.filter { it in appearance.dayWeatherSelected }
    val hasWeather = weatherEnabled && dailyWeatherFields(forecast, current, weatherFields, snapshot).isNotEmpty()
    val defaults = remember { AgendaAppearance() }
    val defaultHeader = appearance.headerOrder.filter { it in appearance.headerSelected } == defaults.headerOrder &&
        appearance.headerSelected == defaults.headerSelected && appearance.headerRightAligned == defaults.headerRightAligned &&
        weatherFields == defaults.dayWeatherOrder.filter { it in defaults.dayWeatherSelected }
    val difference = ChronoUnit.DAYS.between(today, date).toInt()
    val relativeDay = when {
        difference == 0 -> stringResource(R.string.today)
        difference < 0 -> stringResource(R.string.agenda_days_ago, -difference)
        else -> stringResource(R.string.event_in_days, difference)
    }
    val spokenDate = "${date.format(dateFormat)}, $relativeDay"
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = hasWeather && (maxWidth < 300.dp || configuration.fontScale >= 1.3f)
        val rowDateFormat = when {
            compact && maxWidth.value / configuration.fontScale < 150f -> numericDateFormat
            compact -> compactDateFormat
            else -> dateFormat
        }
        val modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).testTag("agenda_day:$date")
        if (defaultHeader) Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(date.format(rowDateFormat), Modifier.weight(1f, fill = false).testTag("agenda_date:$date")
                    .semantics { contentDescription = spokenDate },
                    fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    Spacer(Modifier.width(8.dp))
                    Text(relativeDay, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (hasWeather) {
                Spacer(Modifier.width(10.dp))
                DailyWeather(forecast, current, Modifier.testTag("agenda_weather:$date"), weatherFields,
                    appearance.dayWeatherLabeledFields, snapshot)
            }
        } else {
            val fields = appearance.headerOrder.filter { it in appearance.headerSelected && (it != AgendaHeaderField.Weather || hasWeather) }
                .let { values -> values.filterNot { it in appearance.headerRightAligned } + values.filter { it in appearance.headerRightAligned } }
            if (fields.isNotEmpty()) ClockMetadataLayout(fields.map { it != AgendaHeaderField.Countdown }, modifier,
                wrapWidth = false, alignment = ClockAlignment.Left,
                rightStart = fields.count { it !in appearance.headerRightAligned },
                remainingWidthIndex = fields.indexOf(AgendaHeaderField.Weather).takeIf { it >= 0 }) {
                fields.forEach { field -> when (field) {
                    AgendaHeaderField.Date -> Text(date.format(rowDateFormat), Modifier.testTag("agenda_date:$date")
                        .semantics { contentDescription = spokenDate }, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AgendaHeaderField.Countdown -> Text(relativeDay, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AgendaHeaderField.Weather -> Box { DailyWeather(forecast, current, Modifier.testTag("agenda_weather:$date"), weatherFields,
                        appearance.dayWeatherLabeledFields, snapshot) }
                } }
            }
        }
    }
}
