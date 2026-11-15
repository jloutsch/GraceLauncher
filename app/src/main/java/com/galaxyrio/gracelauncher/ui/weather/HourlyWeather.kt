@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.weather

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AgendaHourWeatherField
import com.galaxyrio.gracelauncher.data.AgendaAppearance
import com.galaxyrio.gracelauncher.data.HourWeatherChartType
import com.galaxyrio.gracelauncher.data.weather.WeatherWind
import com.galaxyrio.gracelauncher.data.weather.WeatherCondition
import com.galaxyrio.gracelauncher.data.weather.WeatherHour
import com.galaxyrio.gracelauncher.data.weather.WeatherMeasure
import com.galaxyrio.gracelauncher.data.weather.WeatherSnapshot
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@StringRes
internal fun AgendaHourWeatherField.labelResource(): Int = when (this) {
    AgendaHourWeatherField.Chart -> R.string.weather_chart
    AgendaHourWeatherField.Icon -> R.string.clock_weather_field_icon
    AgendaHourWeatherField.Temperature -> R.string.clock_weather_field_temperature
    AgendaHourWeatherField.Description -> R.string.clock_weather_field_description
    AgendaHourWeatherField.FeelsLike -> R.string.clock_weather_field_feels_like
    AgendaHourWeatherField.Humidity -> R.string.clock_weather_field_humidity
    AgendaHourWeatherField.Wind -> R.string.weather_field_wind_speed
    AgendaHourWeatherField.WindDirection -> R.string.weather_field_wind_direction
    AgendaHourWeatherField.Gusts -> R.string.weather_field_gusts
    AgendaHourWeatherField.AirQuality -> R.string.clock_weather_field_air_quality
    AgendaHourWeatherField.UV -> R.string.clock_weather_field_uv
    AgendaHourWeatherField.PrecipitationProbability -> R.string.weather_field_precipitation_probability
    AgendaHourWeatherField.RainProbability -> R.string.weather_field_rain_probability
    AgendaHourWeatherField.SnowProbability -> R.string.weather_field_snow_probability
    AgendaHourWeatherField.IceProbability -> R.string.weather_field_ice_probability
    AgendaHourWeatherField.ThunderstormProbability -> R.string.weather_field_thunderstorm_probability
    AgendaHourWeatherField.Precipitation -> R.string.weather_field_precipitation
    AgendaHourWeatherField.Rain -> R.string.weather_field_rain
    AgendaHourWeatherField.Snow -> R.string.weather_field_snow
    AgendaHourWeatherField.Ice -> R.string.weather_field_ice
    AgendaHourWeatherField.Thunderstorm -> R.string.weather_field_thunderstorm
    AgendaHourWeatherField.DewPoint -> R.string.weather_field_dew_point
    AgendaHourWeatherField.Pressure -> R.string.weather_field_pressure
    AgendaHourWeatherField.CloudCover -> R.string.weather_field_cloud_cover
    AgendaHourWeatherField.Visibility -> R.string.weather_field_visibility
}

private data class HourWeatherColumn(val hour: WeatherHour, val current: Boolean = false)

@Composable
private fun hourlyChartTextColor(field: AgendaHourWeatherField) = when (field) {
    AgendaHourWeatherField.Temperature -> MaterialTheme.colorScheme.primary
    AgendaHourWeatherField.FeelsLike -> MaterialTheme.colorScheme.tertiary
    AgendaHourWeatherField.Humidity -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun HourlyWeather(snapshot: WeatherSnapshot, now: Instant, modifier: Modifier = Modifier,
    appearance: AgendaAppearance = AgendaAppearance(), onOpenWeatherApp: (() -> Unit)? = null,
    constrainContentHeight: Boolean = false,
) {
    val config = remember(appearance) { appearance.normalized() }
    val columnWidth = config.hourWeatherColumnWidth.dp
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val timeFormat = remember(locale, use24Hour, snapshot.location.timeZone) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (use24Hour) "HH" else "ha"), locale)
            .withZone(snapshot.location.timeZone)
    }
    val columns = remember(snapshot, now) {
        buildList {
            // Forecasts never borrow missing values from current observations.
            snapshot.current?.let {
                add(HourWeatherColumn(WeatherHour(now, it.temperature, it.condition, it.isDaylight, it.description,
                    feelsLike = it.feelsLike, humidity = it.humidity, wind = it.wind, airQuality = it.airQuality, uv = it.uv,
                    precipitationProbability = it.precipitationProbability, dewPoint = it.dewPoint,
                    pressure = it.pressure, cloudCover = it.cloudCover, visibility = it.visibility), current = true))
            }
            snapshot.hourly.filter { it.at.isAfter(now) }.take(24).forEach { add(HourWeatherColumn(it)) }
        }
    }
    val hours = remember(columns) { columns.map { it.hour } }
    val chartFields = config.hourWeatherChartFields.filter { field -> hours.any { hourlyChartValue(it, field) != null } }.toSet()
    val textFields = config.hourWeatherSelected.filter { field -> hours.any { it.hasField(field) } }.toSet()
    var manuallyExpanded by rememberSaveable(snapshot.location.id, config.hourWeatherChartsExpanded) { mutableStateOf(false) }
    val expanded = config.hourWeatherChartsExpanded || manuallyExpanded
    val pinnedChartFields = chartFields.filter { config.hourWeatherStyle(it).alwaysShowChart }.toSet()
    val chartShown = expanded || pinnedChartFields.isNotEmpty()
    val canCollapse = !config.hourWeatherChartsExpanded &&
        (chartFields.any { it !in pinnedChartFields } || textFields.any { !config.hourWeatherStyle(it).alwaysShowText })
    val barFields = config.hourWeatherOrder.filter { it in chartFields && it != AgendaHourWeatherField.WindDirection &&
        config.hourWeatherStyle(it).chartType == HourWeatherChartType.Bar }
    var chosenBar by rememberSaveable(snapshot.location.id) { mutableStateOf<AgendaHourWeatherField?>(null) }
    val selectedBar = chosenBar?.takeIf { it in barFields } ?: barFields.firstOrNull()
    val activeBar = if (expanded) selectedBar else selectedBar?.takeIf { it in pinnedChartFields }
        ?: barFields.firstOrNull { it in pinnedChartFields } ?: selectedBar
    val visibleChartFields = if (expanded) chartFields else pinnedChartFields
    // Freeze visibility and bar selection during the whole-canvas exit. Filtering the data itself
    // would remove wind's reserved space and stretch every remaining series before the exit ends.
    var retainedVisibleChartFields by remember(snapshot.location.id) { mutableStateOf(visibleChartFields) }
    var retainedBar by remember(snapshot.location.id) { mutableStateOf(activeBar) }
    SideEffect {
        if (chartShown) { retainedVisibleChartFields = visibleChartFields; retainedBar = activeBar }
    }
    val visibleFields = config.hourWeatherOrder.filter { field ->
        if (field == AgendaHourWeatherField.Chart) chartFields.isNotEmpty()
        else field in textFields
    }
    val scroll = rememberScrollState()
    Column(modifier.fillMaxWidth()) {
        if (columns.isNotEmpty()) Box(Modifier.fillMaxWidth().testTag("weather_hourly")
            .then(if (constrainContentHeight) Modifier.weight(1f, fill = false) else Modifier)
            .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceBright)
            // Fork: a tap anywhere on the forecast opens the weather app; scrolling it does not.
            .then(if (onOpenWeatherApp == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onOpenWeatherApp))
            .then(if (constrainContentHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            // One scroll surface centers values and chart points on the same fixed-width hour columns.
            Column(Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    columns.forEach { column ->
                        val time = if (column.current) stringResource(R.string.weather_now) else timeFormat.format(column.hour.at)
                        Box(Modifier.width(columnWidth).testTag("weather_hour:${if (column.current) "now" else column.hour.at}"),
                            contentAlignment = Alignment.Center) {
                            Text(time, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                visibleFields.forEach { field -> key(field) {
                    val shown = if (field == AgendaHourWeatherField.Chart) chartShown
                        else expanded || config.hourWeatherStyle(field).alwaysShowText
                    AnimatedVisibility(shown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            if (field == AgendaHourWeatherField.Chart) {
                                HourlyWeatherChart(hours, chartFields, if (shown) activeBar else retainedBar,
                                    config, config.hourWeatherChartHeight.dp, columnWidth,
                                    visibleFields = if (shown) visibleChartFields else retainedVisibleChartFields)
                            } else Row(verticalAlignment = Alignment.CenterVertically) {
                                columns.forEach { column ->
                                    Box(Modifier.width(columnWidth).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                                        if (column.hour.hasField(field)) {
                                            if (field == AgendaHourWeatherField.Icon) WeatherIcon(column.hour.condition, column.hour.isDaylight, Modifier.size(27.dp))
                                            else {
                                                val value = hourFieldText(column.hour, field, locale)
                                                val spoken = "${stringResource(field.labelResource())}: $value"
                                                Text(value, Modifier.semantics { contentDescription = spoken },
                                                    fontSize = if (field == AgendaHourWeatherField.Temperature) 16.sp else 13.sp,
                                                    fontWeight = if (field == AgendaHourWeatherField.Temperature) FontWeight.Medium else FontWeight.Normal,
                                                    color = if (field in chartFields) hourlyChartTextColor(field)
                                                        else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = if (field == AgendaHourWeatherField.Description) 2 else 1,
                                                    textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val controlsWidth = maxWidth * .65f
            WeatherSourceNote(snapshot, now, onOpenWeatherApp, trailing = {
                Row(Modifier.widthIn(max = controlsWidth), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(barFields.isNotEmpty() && (expanded || !canCollapse),
                        modifier = Modifier.weight(1f, fill = false),
                        enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            barFields.forEach { field -> key(field) {
                                val title = stringResource(field.labelResource())
                                TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                                    tooltip = { PlainTooltip { Text(title) } }, state = rememberTooltipState()) {
                                    IconButton(onClick = { chosenBar = field }, enabled = expanded || !canCollapse,
                                        modifier = Modifier.size(36.dp).testTag("weather_bar_series:${field.name}")
                                            .semantics { selected = activeBar == field }) {
                                        Icon(painterResource(field.chartSymbol()), title, Modifier.size(20.dp),
                                            tint = if (activeBar == field) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } }
                        }
                    }
                    if (canCollapse) {
                        val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "weatherChartExpand")
                        IconButton(onClick = { manuallyExpanded = !manuallyExpanded },
                            modifier = Modifier.size(36.dp).testTag("weather_chart_expand")) {
                            Icon(painterResource(R.drawable.ms_expand_more),
                                stringResource(if (expanded) R.string.weather_chart_collapse else R.string.weather_chart_expand),
                                Modifier.size(20.dp).rotate(angle))
                        }
                    }
                }
            })
        }
    }
}

private fun WeatherHour.hasField(field: AgendaHourWeatherField): Boolean = when (field) {
    AgendaHourWeatherField.Chart -> false
    AgendaHourWeatherField.Icon -> condition != WeatherCondition.Unknown
    AgendaHourWeatherField.Temperature -> temperature != null
    AgendaHourWeatherField.Description -> !description.isNullOrBlank() || condition != WeatherCondition.Unknown
    AgendaHourWeatherField.FeelsLike -> feelsLike != null
    AgendaHourWeatherField.Wind -> wind?.speed != null
    AgendaHourWeatherField.WindDirection -> wind?.degree != null
    AgendaHourWeatherField.DewPoint -> dewPoint != null
    else -> hourlyMeasure(field) != null
}

internal fun WeatherHour.hourlyMeasure(field: AgendaHourWeatherField): WeatherMeasure? = when (field) {
    AgendaHourWeatherField.Wind -> wind?.speed
    AgendaHourWeatherField.Humidity -> humidity
    AgendaHourWeatherField.Gusts -> wind?.gusts
    AgendaHourWeatherField.AirQuality -> airQuality
    AgendaHourWeatherField.UV -> uv
    AgendaHourWeatherField.PrecipitationProbability -> precipitationProbability?.total
    AgendaHourWeatherField.RainProbability -> precipitationProbability?.rain
    AgendaHourWeatherField.SnowProbability -> precipitationProbability?.snow
    AgendaHourWeatherField.IceProbability -> precipitationProbability?.ice
    AgendaHourWeatherField.ThunderstormProbability -> precipitationProbability?.thunderstorm
    AgendaHourWeatherField.Precipitation -> precipitation?.total
    AgendaHourWeatherField.Rain -> precipitation?.rain
    AgendaHourWeatherField.Snow -> precipitation?.snow
    AgendaHourWeatherField.Ice -> precipitation?.ice
    AgendaHourWeatherField.Thunderstorm -> precipitation?.thunderstorm
    AgendaHourWeatherField.Pressure -> pressure
    AgendaHourWeatherField.CloudCover -> cloudCover
    AgendaHourWeatherField.Visibility -> visibility
    else -> null
}

@Composable
private fun hourFieldText(hour: WeatherHour, field: AgendaHourWeatherField, locale: Locale): String = when (field) {
    AgendaHourWeatherField.Description -> weatherDescription(hour.condition, hour.description)
    AgendaHourWeatherField.Temperature -> hour.temperature!!.format()
    AgendaHourWeatherField.FeelsLike -> hour.feelsLike!!.format()
    AgendaHourWeatherField.WindDirection -> homeWindText(WeatherWind(degree = hour.wind!!.degree), locale)
    AgendaHourWeatherField.DewPoint -> hour.dewPoint!!.format()
    else -> hourlyMeasureText(hour.hourlyMeasure(field)!!, locale)
}

@Composable
private fun hourlyMeasureText(measure: WeatherMeasure, locale: Locale): String {
    val number = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = if (measure.unit in setOf("atm", "kgfpsqcm")) 3 else 1
        isGroupingUsed = false
    }.format(measure.value)
    return when (measure.unit) {
        "percent" -> "$number%"
        "aqi", "uvi" -> number
        "bf" -> stringResource(R.string.weather_wind_beaufort, number)
        else -> "$number ${when (measure.unit) {
            "mps" -> "m/s"
            "kph" -> "km/h"
            "ftps" -> "ft/s"
            "lpsqm" -> "L/m²"
            "kpa" -> "kPa"
            "hpa" -> "hPa"
            "mmhg" -> "mmHg"
            "inhg" -> "inHg"
            "kgfpsqcm" -> "kgf/cm²"
            else -> measure.unit
        }}"
    }
}
