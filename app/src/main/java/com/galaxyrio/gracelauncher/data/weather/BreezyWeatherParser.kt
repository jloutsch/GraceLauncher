package com.galaxyrio.gracelauncher.data.weather

import android.database.Cursor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject

/** Minimal independent decoder of the public 0.x contract, not Breezy's internal database model. */
internal object BreezyWeatherParser {
    private const val MAX_UNCOMPRESSED_BYTES = 8 * 1024 * 1024

    fun location(cursor: Cursor): WeatherLocation {
        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        require(!id.isNullOrBlank()) { "Missing Breezy location id" }
        val zone = cursor.string("timezone")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
        return WeatherLocation(
            id = id,
            name = cursor.string("custom_name") ?: cursor.string("city") ?: cursor.string("district")
                ?: cursor.string("admin1") ?: cursor.string("country") ?: "Breezy Weather",
            timeZone = zone,
            isCurrentPosition = cursor.getColumnIndex("is_current_position").let { it >= 0 && cursor.getInt(it) > 0 },
        )
    }

    fun weather(blob: ByteArray, location: WeatherLocation, now: Instant = Instant.now()): WeatherSnapshot? {
        val json = JSONObject(decompress(blob))
        val hourly = json.optJSONArray("hourly").objects().mapNotNull { item ->
            val at = item.instant("date") ?: return@mapNotNull null
            WeatherHour(
                at = at,
                temperature = item.temperature(),
                condition = condition(item.string("weatherCode")),
                isDaylight = item.optBoolean("isDaylight", true),
                description = item.string("weatherText"),
            )
        }.distinctBy(WeatherHour::at).sortedBy(WeatherHour::at)
        val currentDaylight = hourly.minByOrNull { Duration.between(now, it.at).abs() }
            ?.takeIf { Duration.between(now, it.at).abs() <= Duration.ofHours(2) }?.isDaylight
            // Version 0.x does not expose current daylight or sunrise/sunset. Use nearby hourly data.
            ?: (now.atZone(location.timeZone).hour in 6..17)
        val current = json.optJSONObject("current")?.let { item ->
            val temperature = item.temperature()
            val condition = condition(item.string("weatherCode"))
            val description = item.string("weatherText")
            val feelsLike = item.temperature("sourceFeelsLike") ?: item.temperature("computedApparent")
            val humidity = item.measure("relativeHumidity", setOf("percent"))?.takeIf { it.value in 0.0..100.0 }
            val wind = item.optJSONObject("wind")?.let { value ->
                val degree = (value.opt("degree") as? Number)?.toDouble()?.takeIf {
                    it.isFinite() && (it == -1.0 || it in 0.0..360.0)
                }
                val speed = value.measure("speed", setOf("mps", "kph", "kn", "mph", "ftps", "bf"))
                    ?.takeIf { it.value >= 0 }
                if (degree != null || speed != null) WeatherWind(degree, speed) else null
            }
            val airQuality = item.optJSONObject("airQuality")?.measure("index", setOf("aqi"))?.takeIf { it.value >= 0 }
            val uv = item.measure("uV", setOf("uvi"))?.takeIf { it.value >= 0 }
            if (temperature == null && condition == WeatherCondition.Unknown && description == null &&
                feelsLike == null && humidity == null && wind == null && airQuality == null && uv == null) null
            else WeatherCurrent(temperature, condition, currentDaylight, description, feelsLike, humidity, wind, airQuality, uv)
        }
        val daily = json.optJSONArray("daily").objects().mapNotNull { item ->
            val date = item.instant("date")?.atZone(location.timeZone)?.toLocalDate() ?: return@mapNotNull null
            val day = item.optJSONObject("day")
            val night = item.optJSONObject("night")
            WeatherDay(
                date = date,
                high = day?.temperature(),
                low = night?.temperature(),
                condition = condition(day?.string("weatherCode") ?: night?.string("weatherCode")),
                description = day?.string("weatherText") ?: night?.string("weatherText"),
            )
        }.distinctBy(WeatherDay::date).sortedBy(WeatherDay::date)
        if (current == null && hourly.isEmpty() && daily.isEmpty()) return null

        // Include separate air-quality attribution when its current index is available.
        val sources = json.optJSONObject("sources")
        val credits = listOfNotNull(sources?.optJSONObject("current"), sources?.optJSONObject("forecast"),
            sources?.optJSONObject("airQuality")?.takeIf { current?.airQuality != null })
        val attribution = credits.mapNotNull { it.string("text") }.distinct().joinToString(" · ")
        val links = buildMap {
            credits.forEach { source ->
                val sourceLinks = source.optJSONObject("links") ?: return@forEach
                sourceLinks.keys().forEach { label ->
                    sourceLinks.string(label)?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?.let { put(label, it) }
                }
            }
        }
        return WeatherSnapshot(
            location = location,
            current = current,
            hourly = hourly,
            daily = daily,
            updatedAt = json.instant("refreshTime"),
            attribution = listOf("Breezy Weather", attribution).filter(String::isNotBlank).joinToString(" · "),
            sourceLinks = links,
        )
    }

    internal fun condition(code: String?): WeatherCondition = when (code?.lowercase(Locale.ROOT)) {
        "clear" -> WeatherCondition.Clear
        "partly_cloudy" -> WeatherCondition.PartlyCloudy
        "cloudy" -> WeatherCondition.Cloudy
        "rain" -> WeatherCondition.Rain
        "snow" -> WeatherCondition.Snow
        "thunder", "thunderstorm" -> WeatherCondition.Thunderstorm
        "fog", "haze" -> WeatherCondition.Fog
        "wind" -> WeatherCondition.Wind
        "hail" -> WeatherCondition.Hail
        "sleet" -> WeatherCondition.Sleet
        else -> WeatherCondition.Unknown
    }

    private fun JSONObject.temperature(part: String = "temperature"): WeatherTemperature? =
        optJSONObject("temperature")?.measure(part, setOf("c", "f", "k"))?.let { WeatherTemperature(it.value, it.unit) }

    private fun JSONObject.measure(key: String, units: Set<String>): WeatherMeasure? {
        val data = optJSONObject(key) ?: return null
        val value = (data.opt("value") as? Number)?.toDouble()?.takeIf(Double::isFinite) ?: return null
        val unit = data.string("unit")?.lowercase(Locale.ROOT)?.takeIf { it in units } ?: return null
        return WeatherMeasure(value, unit, data.string("description"))
    }

    private fun JSONObject.instant(key: String): Instant? = (opt(key) as? Number)?.toLong()?.let {
        runCatching { Instant.ofEpochMilli(it) }.getOrNull()
    }

    private fun JSONObject.string(key: String): String? = (opt(key) as? String)?.trim()?.takeIf(String::isNotEmpty)

    private fun Cursor.string(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }
        ?.let(::getString)?.trim()?.takeIf(String::isNotEmpty)

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
        (0 until length()).mapNotNull(::optJSONObject)

    private fun decompress(blob: ByteArray): String = GZIPInputStream(ByteArrayInputStream(blob)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_UNCOMPRESSED_BYTES) throw IOException("Breezy weather data exceeds size limit")
            output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
    }
}
