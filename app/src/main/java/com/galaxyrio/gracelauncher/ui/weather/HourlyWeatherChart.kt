@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.galaxyrio.gracelauncher.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AgendaHourWeatherField
import com.galaxyrio.gracelauncher.data.AgendaAppearance
import com.galaxyrio.gracelauncher.data.HourWeatherChartType
import com.galaxyrio.gracelauncher.data.weather.WeatherHour
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val HumidityColor = Color(0xFF2196F3)
// Fixed Celsius anchors: 0 blue, 10 green, 20 yellow, 30 red.
private val TemperatureColors = listOf(0xFF2196F3, 0xFF4CAF50, 0xFFFDD835, 0xFFF44336).map { Color(it) }

// Cubic quarter-waves stay smooth while their centerline follows each series.
private fun Path.waveTo(start: Offset, end: Offset, amplitude: Float, wavelength: Float, originX: Float) {
    val distance = end.x - start.x
    val segments = ceil(distance / (wavelength / 4)).toInt().coerceAtLeast(1)
    val step = distance / segments
    val slope = (end.y - start.y) / distance
    val frequency = 2 * PI / wavelength
    fun phase(x: Float) = (x - originX) * frequency
    fun y(x: Float) = start.y + (x - start.x) * slope + amplitude * sin(phase(x)).toFloat()
    fun tangent(x: Float) = slope + amplitude * frequency.toFloat() * cos(phase(x)).toFloat()
    repeat(segments) { index ->
        val x0 = start.x + index * step
        val x1 = start.x + (index + 1) * step
        cubicTo(x0 + step / 3, y(x0) + tangent(x0) * step / 3,
            x1 - step / 3, y(x1) - tangent(x1) * step / 3, x1, y(x1))
    }
}

// Exact colors sampled from the supplied six-level reference image.
private val AirQualityColors = listOf(0xFF00FF00, 0xFFFFC107, 0xFFCE8500, 0xFFFF0000, 0xFFC41061, 0xFF800080).map { Color(it) }
private val PlumeAqiThresholds = listOf(0, 20, 50, 100, 150, 250)

private fun airQualityColor(value: Double): Color {
    // Both supported sharing contracts use Plume AQI, not US AQI.
    // Breezy: app/src/main/kotlin/org/breezyweather/domain/weather/index/PollutantIndex.kt
    val level = PlumeAqiThresholds.indexOfLast { value >= it }.coerceIn(0, 5)
    return AirQualityColors[level]
}

private fun temperatureColor(value: Double): Color {
    val position = (value / 10).coerceIn(0.0, 3.0)
    val index = position.toInt().coerceAtMost(2)
    return lerp(TemperatureColors[index], TemperatureColors[index + 1], (position - index).toFloat())
}

@Composable
internal fun HourlyWeatherChart(hours: List<WeatherHour>, fields: Set<AgendaHourWeatherField>,
    barField: AgendaHourWeatherField?, appearance: AgendaAppearance, height: Dp, columnWidth: Dp,
    visibleFields: Set<AgendaHourWeatherField> = fields,
) {
    if (hours.isEmpty() || fields.isEmpty()) return
    val values = remember(hours, fields) { fields.associateWith { field -> hours.map { hourlyChartValue(it, field) } } }
    val ranges = remember(values) { hourlyChartRanges(values) }
    val lines = fields.filter { it != AgendaHourWeatherField.WindDirection && appearance.hourWeatherStyle(it).chartType == HourWeatherChartType.Line }
    val wind = AgendaHourWeatherField.WindDirection in fields
    val plot = lines.isNotEmpty() || barField != null
    // Visibility never changes the coordinate ranges or wind's reserved space. Pinned series
    // stay still while the other series fade; with no pins the enclosing canvas exits as a unit.
    val fieldAlphas = fields.associateWith { field -> key(field) {
        animateFloatAsState(if (field in visibleFields) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "hourlyChartVisibility:${field.name}")
    } }
    val colors = MaterialTheme.colorScheme
    val seriesColors = remember(colors) { AgendaHourWeatherField.entries.associateWith { field -> when (field) {
        AgendaHourWeatherField.Humidity -> HumidityColor
        AgendaHourWeatherField.Wind, AgendaHourWeatherField.Gusts -> colors.tertiary
        AgendaHourWeatherField.Precipitation, AgendaHourWeatherField.Rain, AgendaHourWeatherField.Snow,
        AgendaHourWeatherField.Ice, AgendaHourWeatherField.Thunderstorm -> colors.secondary
        else -> colors.primary
    } } }
    fun valueColor(field: AgendaHourWeatherField, value: Double) = when (field) {
        AgendaHourWeatherField.Temperature, AgendaHourWeatherField.FeelsLike, AgendaHourWeatherField.DewPoint -> temperatureColor(value)
        AgendaHourWeatherField.AirQuality -> airQualityColor(value)
        else -> seriesColors.getValue(field)
    }
    // State is keyed by hour position, not series. Each column interpolates its own new height.
    val barHeights = hours.indices.map { index -> key(index) {
        val value = barField?.let { values[it]?.get(index) }
        animateFloatAsState(if (value != null && barField != null) ranges.getValue(barField).fraction(value) else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(), label = "hourlyBarHeight:$index")
    } }
    val barColors = hours.indices.map { index -> key(index) {
        val value = barField?.let { values[it]?.get(index) }
        animateColorAsState(if (value != null && barField != null) valueColor(barField, value) else Color.Transparent,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "hourlyBarColor:$index")
    } }
    val animatedBarWidth = animateFloatAsState(barField?.let { appearance.hourWeatherStyle(it).barWidth.toFloat() } ?: 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(), label = "hourlyBarWidth")
    val density = LocalDensity.current
    // Match each cell's pixel rounding so chart centers stay aligned across the whole scroll row.
    val columnPixels = with(density) { columnWidth.roundToPx() }
    val chartWidth = with(density) { (columnPixels * hours.size).toDp() }
    val arrowSize = with(density) { 20.dp.toPx() }
    val normalizedArrow = MaterialShapes.Arrow.toPath()
    val arrow = remember(normalizedArrow, arrowSize) {
        Path().apply {
            addPath(normalizedArrow)
            transform(Matrix().apply { scale(arrowSize, arrowSize) })
        }
    }
    val arrowColor = colors.onSurfaceVariant
    val arrowPaint = remember(arrowColor) { Paint().apply { color = arrowColor; isAntiAlias = true } }
    val description = stringResource(R.string.weather_chart) + ": " +
        (lines + listOfNotNull(barField) + listOfNotNull(AgendaHourWeatherField.WindDirection.takeIf { wind }))
            .filter { it in visibleFields }
            .map { stringResource(it.labelResource()) }.joinToString(", ")
    Canvas(Modifier.width(chartWidth).height(height)
        .testTag("weather_hourly_chart").semantics { contentDescription = description }) {
        val column = columnPixels.toFloat()
        val barWidth = animatedBarWidth.value.coerceAtLeast(0f).dp.toPx().coerceAtMost(column - 4.dp.toPx())
        val top = minOf(8.dp.toPx(), size.height * .1f)
        val rotatedArrowExtent = arrowSize * sqrt(2f)
        val arrowCenterY = if (plot) size.height - 4.dp.toPx() - rotatedArrowExtent / 2 else size.height / 2
        // Rounded rectangles stay inside the plot even with wide bars and the shortest chart height.
        // Leave room for line strokes/waves, plus 8 dp before the arrow's rotated bounds.
        val bottom = (if (wind) arrowCenterY - rotatedArrowExtent / 2 - 12.dp.toPx()
            else size.height - 8.dp.toPx()).coerceAtLeast(top + 1f)
        val plotHeight = bottom - top
        fun x(index: Int) = column * (index + .5f)
        fun y(value: Double, field: AgendaHourWeatherField) = bottom - ranges.getValue(field).fraction(value) * plotHeight
        fun rawY(value: Double, field: AgendaHourWeatherField): Float {
            val range = ranges.getValue(field)
            return bottom - ((value - range.lower) / (range.upper - range.lower)).toFloat() * plotHeight
        }
        fun brush(field: AgendaHourWeatherField): Brush = when (field) {
            AgendaHourWeatherField.Temperature, AgendaHourWeatherField.FeelsLike, AgendaHourWeatherField.DewPoint ->
                Brush.verticalGradient(TemperatureColors.reversed(), startY = rawY(30.0, field), endY = rawY(0.0, field))
            AgendaHourWeatherField.AirQuality -> Brush.verticalGradient(
                *AirQualityColors.indices.reversed().map { index -> (1f - PlumeAqiThresholds[index] / 250f) to AirQualityColors[index] }.toTypedArray(),
                startY = top, endY = bottom)
            else -> SolidColor(seriesColors.getValue(field))
        }
        val waveAmplitude = minOf(2.dp.toPx(), plotHeight * .1f)
        val waveLength = column / 4
        val singlePointHalfWidth = waveLength / 2
        val lineStroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Exactly one bar series sits behind every line; missing readings never borrow another hour.
        if (barWidth > 0f) hours.indices.forEach { index ->
            val barColor = barColors[index].value
            val alpha = barField?.let { fieldAlphas[it]?.value } ?: 0f
            val color = barColor.copy(alpha = barColor.alpha * alpha.coerceIn(0f, 1f))
            if (color.alpha > 0f) {
                drawRoundRect(colors.surfaceContainerHighest.copy(alpha = color.alpha),
                    Offset(x(index) - barWidth / 2, top), Size(barWidth, plotHeight), CornerRadius(barWidth / 2))
                val barHeight = (barHeights[index].value.coerceIn(0f, 1f) * plotHeight).coerceAtLeast(1.dp.toPx())
                drawRoundRect(color, Offset(x(index) - barWidth / 2, bottom - barHeight), Size(barWidth, barHeight),
                    CornerRadius(barWidth / 2))
            }
        }
        lines.forEach { field ->
            val path = Path()
            val samples = values.getValue(field)
            val wavy = appearance.hourWeatherStyle(field).wavy
            samples.forEachIndexed { index, value ->
                if (value != null) {
                    val point = Offset(x(index), y(value, field))
                    val previous = samples.getOrNull(index - 1)
                    if (previous != null) {
                        if (wavy) path.waveTo(Offset(x(index - 1), y(previous, field)), point,
                            waveAmplitude, waveLength, column / 2)
                        else path.lineTo(point.x, point.y)
                    } else if (samples.getOrNull(index + 1) == null) {
                        val start = Offset(point.x - singlePointHalfWidth, point.y)
                        val end = Offset(point.x + singlePointHalfWidth, point.y)
                        path.moveTo(start.x, start.y)
                        if (wavy) path.waveTo(start, end, waveAmplitude, waveLength, column / 2)
                        else path.lineTo(end.x, end.y)
                    } else path.moveTo(point.x, point.y)
                }
            }
            drawPath(path, brush(field), alpha = fieldAlphas.getValue(field).value.coerceIn(0f, 1f), style = lineStroke)
        }
        arrowPaint.alpha = fieldAlphas[AgendaHourWeatherField.WindDirection]?.value?.coerceIn(0f, 1f) ?: 0f
        values[AgendaHourWeatherField.WindDirection]?.forEachIndexed { index, degree ->
            if (degree != null) {
                val arrowTop = arrowCenterY - arrowSize / 2
                withTransform({
                    translate(x(index) - arrowSize / 2, arrowTop)
                    // Bearings describe where wind comes from; the arrow points where it blows.
                    rotate(degree.toFloat() + 180f, Offset(arrowSize / 2, arrowSize / 2))
                }) { drawIntoCanvas { it.drawPath(arrow, arrowPaint) } }
            }
        }
    }
}
