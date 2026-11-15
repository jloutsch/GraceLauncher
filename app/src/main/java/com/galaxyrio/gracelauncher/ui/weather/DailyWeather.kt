package com.galaxyrio.gracelauncher.ui.weather

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AgendaAppearance
import com.galaxyrio.gracelauncher.data.AgendaDayWeatherField
import com.galaxyrio.gracelauncher.data.weather.*
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter

@StringRes
internal fun AgendaDayWeatherField.labelResource(): Int = when (this) {
    AgendaDayWeatherField.Icon -> R.string.clock_weather_field_icon
    AgendaDayWeatherField.Temperature -> R.string.clock_weather_field_temperature
    AgendaDayWeatherField.Description -> R.string.clock_weather_field_description
    AgendaDayWeatherField.FeelsLike -> R.string.clock_weather_field_feels_like
    AgendaDayWeatherField.Humidity -> R.string.clock_weather_field_humidity
    AgendaDayWeatherField.Wind -> R.string.clock_weather_field_wind
    AgendaDayWeatherField.AirQuality -> R.string.clock_weather_field_air_quality
    AgendaDayWeatherField.UV -> R.string.clock_weather_field_uv
    AgendaDayWeatherField.Location -> R.string.clock_weather_field_location
    AgendaDayWeatherField.HighLow -> R.string.clock_weather_field_high_low
    AgendaDayWeatherField.UpdatedAt -> R.string.clock_weather_field_updated_at
}

private fun dailyValues(day: WeatherDay?, current: WeatherCurrent?) = WeatherCurrent(
    temperature = current?.temperature ?: day?.high,
    condition = day?.condition?.takeUnless { it == WeatherCondition.Unknown } ?: current?.condition ?: WeatherCondition.Unknown,
    isDaylight = day != null || current?.isDaylight != false,
    description = day?.description ?: current?.description,
    feelsLike = day?.feelsLike ?: current?.feelsLike,
    humidity = day?.humidity ?: current?.humidity,
    wind = day?.wind ?: current?.wind,
    airQuality = day?.airQuality ?: current?.airQuality,
    uv = day?.uv ?: current?.uv,
)

internal fun dailyWeatherFields(day: WeatherDay?, current: WeatherCurrent?, fields: List<AgendaDayWeatherField>,
    snapshot: WeatherSnapshot?,
): List<AgendaDayWeatherField> {
    val values = dailyValues(day, current)
    return fields.filter { when (it) {
        AgendaDayWeatherField.Icon -> values.condition != WeatherCondition.Unknown
        AgendaDayWeatherField.Temperature -> values.temperature != null
        AgendaDayWeatherField.Description -> !values.description.isNullOrBlank() || values.condition != WeatherCondition.Unknown
        AgendaDayWeatherField.FeelsLike -> values.feelsLike != null
        AgendaDayWeatherField.Humidity -> values.humidity != null
        AgendaDayWeatherField.Wind -> values.wind?.let { wind -> wind.speed != null || wind.degree != null } == true
        AgendaDayWeatherField.AirQuality -> values.airQuality != null
        AgendaDayWeatherField.UV -> values.uv != null
        AgendaDayWeatherField.Location -> !snapshot?.location?.name.isNullOrBlank()
        AgendaDayWeatherField.HighLow -> day?.high != null || day?.low != null || current?.temperature != null
        AgendaDayWeatherField.UpdatedAt -> snapshot?.updatedAt != null
    } }
}

@Composable
fun DailyWeather(day: WeatherDay?, current: WeatherCurrent?, modifier: Modifier = Modifier,
    fields: List<AgendaDayWeatherField> = listOf(AgendaDayWeatherField.Icon, AgendaDayWeatherField.HighLow),
    labeledFields: Set<AgendaDayWeatherField> = AgendaAppearance().dayWeatherLabeledFields,
    snapshot: WeatherSnapshot? = null,
    now: Instant = Instant.now(),
) {
    val available = dailyWeatherFields(day, current, fields, snapshot)
    if (available.isEmpty()) return
    val values = dailyValues(day, current)
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val texts = available.associateWith { field -> when (field) {
        AgendaDayWeatherField.Icon, AgendaDayWeatherField.Description -> weatherDescription(values.condition, values.description)
        AgendaDayWeatherField.Temperature -> values.temperature!!.format()
        AgendaDayWeatherField.FeelsLike -> values.feelsLike!!.format().let {
            if (field in labeledFields) stringResource(R.string.weather_feels_like_value, it) else it
        }
        AgendaDayWeatherField.Humidity -> NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
            .format(values.humidity!!.value / 100)
        AgendaDayWeatherField.Wind -> homeWindText(values.wind!!, locale).let {
            if (field in labeledFields) stringResource(R.string.weather_wind_value, it) else it
        }
        AgendaDayWeatherField.AirQuality -> (if (field in labeledFields) "AQI " else "") + weatherNumber(values.airQuality!!.value, locale)
        AgendaDayWeatherField.UV -> (if (field in labeledFields) "UV " else "") + weatherNumber(values.uv!!.value, locale)
        AgendaDayWeatherField.Location -> snapshot!!.location.name
        AgendaDayWeatherField.HighLow -> listOfNotNull(day?.high?.format(), day?.low?.format()).joinToString(" / ")
            .ifEmpty { current?.temperature?.format().orEmpty() }
        AgendaDayWeatherField.UpdatedAt -> {
            val updated = snapshot!!.updatedAt!!
            val date = if (updated.atZone(snapshot.location.timeZone).toLocalDate() == now.atZone(snapshot.location.timeZone).toLocalDate()) "" else "MMMd"
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, date + if (use24Hour) "Hm" else "hm"), locale)
                .withZone(snapshot.location.timeZone).format(updated)
        }
    } }
    val summary = available.joinToString(", ") { texts.getValue(it) }
    FlowRow(modifier.clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically) {
        available.forEach { field ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (field == AgendaDayWeatherField.Icon) {
                    WeatherIcon(values.condition, values.isDaylight, Modifier.size(21.dp))
                } else {
                    if (field in labeledFields) {
                        val icon = when (field) {
                            AgendaDayWeatherField.Humidity -> R.drawable.ms_humidity_percentage
                            AgendaDayWeatherField.Location -> R.drawable.ms_location_on
                            AgendaDayWeatherField.HighLow -> R.drawable.ms_mode_heat_cool
                            AgendaDayWeatherField.UpdatedAt -> R.drawable.ms_update
                            else -> null
                        }
                        if (icon != null) Icon(painterResource(icon), null, Modifier.size(17.dp))
                    }
                    if (field == AgendaDayWeatherField.HighLow && day?.high != null && day.low != null) {
                        Text(day.high.format(), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("/ ${day.low.format()}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    } else Text(texts.getValue(field), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
