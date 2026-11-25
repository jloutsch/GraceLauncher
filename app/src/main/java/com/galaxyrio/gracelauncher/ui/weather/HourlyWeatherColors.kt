package com.galaxyrio.gracelauncher.ui.weather

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.galaxyrio.gracelauncher.data.AgendaHourWeatherField
import com.galaxyrio.gracelauncher.data.HourWeatherChartType

/** Shared by labels, bars and lines. Values use the chart's units (Celsius for temperatures). */
internal class HourlyValueColors(
    private val stops: List<Pair<Double, Color>>,
    private val steppedBars: Boolean = false,
) {
    fun color(value: Double, type: HourWeatherChartType): Color {
        val index = stops.indexOfLast { value >= it.first }.coerceAtLeast(0)
        val (lower, start) = stops[index]
        if (index == stops.lastIndex || value <= lower || (steppedBars && type == HourWeatherChartType.Bar)) return start
        val (upper, end) = stops[index + 1]
        val fraction = ((value - lower) / (upper - lower)).toFloat().coerceIn(0f, 1f)
        // Keep existing bar interpolation; line labels sample the same sRGB gradient as the brush.
        return if (type == HourWeatherChartType.Bar) lerp(start, end, fraction) else Color(
            red = start.red + (end.red - start.red) * fraction,
            green = start.green + (end.green - start.green) * fraction,
            blue = start.blue + (end.blue - start.blue) * fraction,
            alpha = start.alpha + (end.alpha - start.alpha) * fraction,
        )
    }

    fun brush(y: (Double) -> Float): Brush {
        if (stops.size == 1) return SolidColor(stops.first().second)
        val lower = stops.first().first
        val upper = stops.last().first
        return Brush.verticalGradient(
            *stops.asReversed().map { (value, color) -> ((upper - value) / (upper - lower)).toFloat() to color }.toTypedArray(),
            startY = y(upper), endY = y(lower),
        )
    }
}

// Absolute Celsius anchors, independent of the forecast's min/max range.
private val TemperatureValueColors = HourlyValueColors(listOf(
    0.0 to Color(0xFF2196F3), 10.0 to Color(0xFF4CAF50),
    20.0 to Color(0xFFFDD835), 30.0 to Color(0xFFF44336),
))
private val HumidityValueColors = HourlyValueColors(listOf(0.0 to Color(0xFF2196F3)))
// Both sharing contracts use Plume AQI. Bars use the supplied six level colors; lines interpolate.
private val AirQualityValueColors = HourlyValueColors(listOf(
    0.0 to Color(0xFF00FF00), 20.0 to Color(0xFFFFC107), 50.0 to Color(0xFFCE8500),
    100.0 to Color(0xFFFF0000), 150.0 to Color(0xFFC41061), 250.0 to Color(0xFF800080),
), steppedBars = true)

/** Theme-only series have no dedicated value palette, so their color toggles stay disabled. */
internal val AgendaHourWeatherField.valueColors: HourlyValueColors?
    get() = when (this) {
        AgendaHourWeatherField.Temperature, AgendaHourWeatherField.FeelsLike, AgendaHourWeatherField.DewPoint -> TemperatureValueColors
        AgendaHourWeatherField.Humidity -> HumidityValueColors
        AgendaHourWeatherField.AirQuality -> AirQualityValueColors
        else -> null
    }
