package com.galaxyrio.gracelauncher.data.weather

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject

/** Independent decoder of the public GenericWeatherReceiver / WeatherSpec contract.
 * https://gadgetbridge.org/internals/development/weather-support/
 * Like Kvaesitso, prefer WeatherGz and retain compatibility with WeatherJson.
 */
internal object GadgetbridgeWeatherParser {
    const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
    private const val MAX_LOCATIONS = 50
    private const val MAX_HOURS = 24 * 16
    private const val MAX_DAYS = 16

    fun decodePayload(gzip: ByteArray?, legacy: String?, secondary: String?): String? {
        if (gzip != null && gzip.size <= 1024 * 1024) {
            val decoded = runCatching { boundedArray(decompress(gzip)) }.getOrNull()
            if (decoded != null && decoded.length() > 0) return decoded.toString()
        }
        if (legacy == null || legacy.length > MAX_PAYLOAD_BYTES) return null
        return runCatching {
            val result = if (legacy.trimStart().startsWith("[")) boundedArray(legacy)
                else JSONArray().put(JSONObject(legacy))
            if (secondary != null && secondary.length <= MAX_PAYLOAD_BYTES) {
                // A malformed optional secondary list must not discard a valid primary location.
                runCatching { boundedArray(secondary) }.getOrNull()?.objects(MAX_LOCATIONS)?.forEach {
                    if (result.length() < MAX_LOCATIONS) result.put(it)
                }
            }
            result.toString().takeIf { it.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES }
        }.getOrNull()
    }

    fun snapshots(
        payload: String,
        unit: String,
        receivedAt: Instant,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<WeatherSnapshot> = boundedArray(payload).objects(MAX_LOCATIONS).mapIndexedNotNull { index, data ->
        // One unusable location should not hide the other locations in the same broadcast.
        runCatching { snapshot(data, index, unit, receivedAt, now, zone) }.getOrNull()
    }.distinctBy { it.location.id }

    private fun snapshot(
        data: JSONObject, index: Int, unit: String, receivedAt: Instant, now: Instant, zone: ZoneId,
    ): WeatherSnapshot? {
        val name = data.text("location") ?: "Gadgetbridge"
        val currentLocation = data.optBoolean("isCurrentLocation", false) || data.optInt("isCurrentLocation") == 1
        val latitude = data.number("latitude")?.takeIf { it in -90.0..90.0 }
        val longitude = data.number("longitude")?.takeIf { it in -180.0..180.0 }
        val id = when {
            currentLocation -> "gadgetbridge:current"
            latitude != null && longitude != null -> "gadgetbridge:$latitude:$longitude:$name"
            data.text("location") != null -> "gadgetbridge:$name"
            else -> "gadgetbridge:location:$index"
        }
        // WeatherSpec has no timezone field. Interpret undated daily entries in the device zone.
        val location = WeatherLocation(id, name, zone, currentLocation)
        val updatedAt = data.instant("timestamp")
        val forecastDate = (updatedAt ?: receivedAt).atZone(zone).toLocalDate()
        val sunrise = data.instant("sunRise")
        val sunset = data.instant("sunSet")
        val temperature = data.temperature("currentTemp", unit)
        val currentCondition = condition(data.optInt("currentConditionCode", -1))
        val description = data.text("currentCondition")
        val feelsLike = data.temperature("feelsLikeTemp", unit)
        val humidity = data.number("currentHumidity")?.takeIf { it in 0.0..100.0 }?.let { WeatherMeasure(it, "percent") }
        val speed = data.number("windSpeed")?.takeIf { it >= 0 }?.let { WeatherMeasure(it, "kph") }
        val direction = data.number("windDirection")?.takeIf { it in 0.0..360.0 }
        val wind = if (speed != null || direction != null) WeatherWind(direction, speed) else null
        val airQuality = data.optJSONObject("airQuality")?.number("aqi")?.takeIf { it >= 0 }?.let { WeatherMeasure(it, "aqi") }
        val uv = data.number("uvIndex")?.takeIf { it >= 0 }?.let { WeatherMeasure(it, "uvi") }
        val current = if (temperature == null && currentCondition == WeatherCondition.Unknown && description == null &&
            feelsLike == null && humidity == null && wind == null && airQuality == null && uv == null) null
        else WeatherCurrent(temperature, currentCondition, daylight(now, sunrise, sunset, zone), description,
            feelsLike, humidity, wind, airQuality, uv)

        val rawHours = data.optJSONArray("hourly").objects(MAX_HOURS)
        val rawDays = data.optJSONArray("forecasts").objects(MAX_DAYS)
        // WeatherMaster 3.10.5 uses "temperature" instead of "temp" and includes today
        // at forecasts[0]. Its daily entries contain a description instead of a conditionCode.
        val firstDay = rawDays.firstOrNull()
        val firstForecastIsToday = firstDay != null && firstDay.text("condition") != null && !firstDay.has("conditionCode") &&
            (rawHours.any { it.has("temperature") && !it.has("temp") } ||
                (firstDay.number("minTemp") != null && firstDay.number("maxTemp") != null &&
                    firstDay.number("minTemp") == data.number("todayMinTemp") &&
                    firstDay.number("maxTemp") == data.number("todayMaxTemp")))
        val hourly = rawHours.mapNotNull { hour ->
            val at = hour.instant("timestamp") ?: return@mapNotNull null
            WeatherHour(at, hour.temperature("temp", unit) ?: hour.temperature("temperature", unit), condition(hour.optInt("conditionCode", -1)),
                daylight(at, sunrise, sunset, zone), hour.text("condition"))
        }.distinctBy(WeatherHour::at).sortedBy(WeatherHour::at)
        val daily = buildList {
            val high = data.temperature("todayMaxTemp", unit)
            val low = data.temperature("todayMinTemp", unit)
            if (high != null || low != null) add(WeatherDay(forecastDate, high, low, currentCondition, description))
            rawDays.forEachIndexed { dayIndex, day ->
                // Standard WeatherSpec forecasts start tomorrow; WeatherMaster's variant starts today.
                val date = (day.instant("sunRise") ?: day.instant("sunSet"))?.atZone(zone)?.toLocalDate()
                    ?: forecastDate.plusDays(dayIndex.toLong() + if (firstForecastIsToday) 0 else 1)
                add(WeatherDay(date, day.temperature("maxTemp", unit), day.temperature("minTemp", unit),
                    condition(day.optInt("conditionCode", -1)), day.text("condition")))
            }
        }.distinctBy(WeatherDay::date).sortedBy(WeatherDay::date)
        if (current == null && hourly.isEmpty() && daily.isEmpty()) return null
        return WeatherSnapshot(location, current, hourly, daily, updatedAt, attribution = "Gadgetbridge")
    }

    /** WeatherSpec uses OpenWeather condition codes, independently of the actual data provider. */
    private fun condition(code: Int): WeatherCondition = when (code) {
        in 200..299 -> WeatherCondition.Thunderstorm
        in 300..399, in 500..510, in 520..531 -> WeatherCondition.Rain
        511, in 611..613, 615, 616 -> WeatherCondition.Sleet
        in 600..602, in 620..622 -> WeatherCondition.Snow
        701, 711, 721, 731, 741, 751, 761, 762 -> WeatherCondition.Fog
        771, 781 -> WeatherCondition.Wind
        800 -> WeatherCondition.Clear
        801, 802 -> WeatherCondition.PartlyCloudy
        803, 804 -> WeatherCondition.Cloudy
        else -> WeatherCondition.Unknown
    }

    private fun daylight(at: Instant, sunrise: Instant?, sunset: Instant?, zone: ZoneId): Boolean {
        val time = at.atZone(zone).toLocalTime()
        if (sunrise != null && sunset != null) {
            val rise = sunrise.atZone(zone).toLocalTime()
            val set = sunset.atZone(zone).toLocalTime()
            if (rise < set) return time >= rise && time < set
            if (rise > set) return time >= rise || time < set
        }
        return time.hour in 6..17
    }

    private fun JSONObject.temperature(key: String, unit: String): WeatherTemperature? {
        // Missing fields in older WeatherSpec senders may be represented by -1 or 0.
        val kelvin = number(key)?.takeIf { it in 100.0..400.0 } ?: return null
        val value = when (unit) {
            "f" -> (kelvin - 273.15) * 9 / 5 + 32
            "k" -> kelvin
            else -> kelvin - 273.15
        }
        return WeatherTemperature(value, unit)
    }

    private fun JSONObject.number(key: String): Double? = (opt(key) as? Number)?.toDouble()?.takeIf(Double::isFinite)
    private fun JSONObject.text(key: String): String? = (opt(key) as? String)?.trim()?.take(512)?.takeIf(String::isNotEmpty)
    private fun JSONObject.instant(key: String): Instant? = number(key)?.takeIf { it > 0 && it < 253_402_300_800.0 }
        ?.let { Instant.ofEpochSecond(it.toLong()) }
    private fun JSONArray?.objects(limit: Int): List<JSONObject> = if (this == null) emptyList()
        else (0 until minOf(length(), limit)).mapNotNull(::optJSONObject)
    private fun boundedArray(json: String): JSONArray {
        require(json.length <= MAX_PAYLOAD_BYTES) { "Gadgetbridge payload exceeds size limit" }
        val source = JSONArray(json)
        return JSONArray().apply { source.objects(MAX_LOCATIONS).forEach(::put) }
    }

    private fun decompress(blob: ByteArray): String = GZIPInputStream(ByteArrayInputStream(blob)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_PAYLOAD_BYTES) throw IOException("Gadgetbridge payload exceeds size limit")
            output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
    }
}
