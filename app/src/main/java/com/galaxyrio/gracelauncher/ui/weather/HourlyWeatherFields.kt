package com.galaxyrio.gracelauncher.ui.weather

import androidx.annotation.DrawableRes
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AgendaHourWeatherField
import com.galaxyrio.gracelauncher.data.weather.WeatherHour
import com.galaxyrio.gracelauncher.data.weather.WeatherTemperature

internal fun hourlyChartValue(hour: WeatherHour, field: AgendaHourWeatherField): Double? = when (field) {
    AgendaHourWeatherField.Temperature -> hour.temperature.celsius()
    AgendaHourWeatherField.FeelsLike -> hour.feelsLike.celsius()
    AgendaHourWeatherField.DewPoint -> hour.dewPoint.celsius()
    AgendaHourWeatherField.WindDirection -> hour.wind?.degree?.takeIf { it in 0.0..360.0 }
    else -> hour.hourlyMeasure(field)?.value?.takeIf { it.isFinite() && it >= 0 }
}

private fun WeatherTemperature?.celsius(): Double? = this?.let { temperature ->
    when (temperature.unit) {
        "c" -> temperature.value
        "f" -> (temperature.value - 32) * 5 / 9
        "k" -> temperature.value - 273.15
        else -> null
    }?.takeIf { it.isFinite() && it in -273.15..1000.0 }
}

internal data class HourlyChartRange(val lower: Double, val upper: Double) {
    fun fraction(value: Double) = ((value - lower) / (upper - lower)).coerceIn(0.0, 1.0).toFloat()
}

/** Temperatures share Celsius coordinates; units affect labels, never the color anchors. */
internal fun hourlyChartRanges(values: Map<AgendaHourWeatherField, List<Double?>>): Map<AgendaHourWeatherField, HourlyChartRange> {
    val temperatures = listOf(AgendaHourWeatherField.Temperature, AgendaHourWeatherField.FeelsLike)
    return values.mapValues { (field, samples) ->
        val upper = when (field) {
            AgendaHourWeatherField.Humidity, AgendaHourWeatherField.CloudCover,
            AgendaHourWeatherField.PrecipitationProbability, AgendaHourWeatherField.RainProbability,
            AgendaHourWeatherField.SnowProbability, AgendaHourWeatherField.IceProbability,
            AgendaHourWeatherField.ThunderstormProbability -> 100.0
            AgendaHourWeatherField.AirQuality -> 250.0
            AgendaHourWeatherField.UV -> 11.0
            else -> null
        }
        if (upper != null) HourlyChartRange(0.0, upper) else {
            val numbers = if (field in temperatures) temperatures.flatMap { values[it].orEmpty().filterNotNull() }
                else samples.filterNotNull()
            val minimum = numbers.minOrNull() ?: 0.0
            val maximum = numbers.maxOrNull() ?: 0.0
            when {
                maximum > minimum -> HourlyChartRange(minimum, maximum)
                minimum == 0.0 -> HourlyChartRange(0.0, 1.0)
                else -> HourlyChartRange(minimum - .5, maximum + .5)
            }
        }
    }
}

/** Distinct symbols keep the bar-series selector readable even for amounts and probabilities. */
@DrawableRes
internal fun AgendaHourWeatherField.chartSymbol(): Int = when (this) {
    AgendaHourWeatherField.Temperature -> R.drawable.ms_thermostat
    AgendaHourWeatherField.FeelsLike -> R.drawable.ms_device_thermostat
    AgendaHourWeatherField.Humidity -> R.drawable.ms_humidity_percentage
    AgendaHourWeatherField.Wind -> R.drawable.ms_air
    AgendaHourWeatherField.Gusts -> R.drawable.ms_storm
    AgendaHourWeatherField.AirQuality -> R.drawable.ms_airwave
    AgendaHourWeatherField.UV -> R.drawable.ms_sunny
    AgendaHourWeatherField.PrecipitationProbability -> R.drawable.ms_umbrella
    AgendaHourWeatherField.RainProbability -> R.drawable.ms_rainy_light
    AgendaHourWeatherField.SnowProbability -> R.drawable.ms_weather_snowy
    AgendaHourWeatherField.IceProbability -> R.drawable.ms_severe_cold
    AgendaHourWeatherField.ThunderstormProbability -> R.drawable.ms_thunderstorm
    AgendaHourWeatherField.Precipitation -> R.drawable.ms_water_drop
    AgendaHourWeatherField.Rain -> R.drawable.ms_rainy
    AgendaHourWeatherField.Snow -> R.drawable.ms_snowing
    AgendaHourWeatherField.Ice -> R.drawable.ms_ac_unit
    AgendaHourWeatherField.Thunderstorm -> R.drawable.ms_bolt
    AgendaHourWeatherField.DewPoint -> R.drawable.ms_dew_point
    AgendaHourWeatherField.Pressure -> R.drawable.ms_speed
    AgendaHourWeatherField.CloudCover -> R.drawable.ms_cloud
    AgendaHourWeatherField.Visibility -> R.drawable.ms_visibility
    else -> R.drawable.ms_bar_chart
}
