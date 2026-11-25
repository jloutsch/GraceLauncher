package com.galaxyrio.gracelauncher.data

import org.json.JSONObject
import kotlin.math.roundToInt

enum class FontShadowPreset { None, Soft, Sharp, Custom }

/** Dimensions are in dp, independently of the text's font size. */
data class FontShadow(
    val preset: FontShadowPreset = FontShadowPreset.None,
    val opacity: Int = 0,
    val blur: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val spread: Float = 0f,
    val color: IconColor = IconColor(0xFF000000.toInt()),
) {
    fun normalized() = if (preset == FontShadowPreset.None) copy(
        opacity = 0, blur = 0f, offsetX = 0f, offsetY = 0f, spread = 0f,
        color = color.normalized(),
    ) else copy(
        opacity = opacity.coerceIn(0, 100),
        blur = blur.finiteIn(0f, 48f), offsetX = offsetX.finiteIn(-24f, 24f),
        offsetY = offsetY.finiteIn(-24f, 24f), spread = spread.finiteIn(0f, 16f),
        color = color.normalized(),
    )

    fun select(value: FontShadowPreset): FontShadow = when (value) {
        FontShadowPreset.None -> copy(preset = value).normalized()
        FontShadowPreset.Custom -> copy(preset = value)
        FontShadowPreset.Soft -> Soft
        FontShadowPreset.Sharp -> Sharp
    }

    fun json(): JSONObject = normalized().let {
        JSONObject().put("preset", it.preset.name).put("opacity", it.opacity)
            .put("blur", it.blur).put("offsetX", it.offsetX).put("offsetY", it.offsetY)
            .put("spread", it.spread).put("color", it.color.json())
    }

    fun encode(): String = json().toString()

    companion object {
        val Soft = FontShadow(preset = FontShadowPreset.Soft, opacity = 32, blur = 4f)
        val Sharp = FontShadow(preset = FontShadowPreset.Sharp, opacity = 95, blur = 2f, offsetY = 1f)

        fun decode(value: String?): FontShadow = runCatching { decode(value?.let(::JSONObject)) }.getOrDefault(FontShadow())
        fun decode(value: JSONObject?): FontShadow {
            if (value == null) return FontShadow()
            return FontShadow(
                preset = FontShadowPreset.entries.firstOrNull { it.name == value.optString("preset") } ?: FontShadowPreset.None,
                opacity = value.optInt("opacity", 32), blur = value.optDouble("blur", 4.0).toFloat(),
                offsetX = value.optDouble("offsetX", 0.0).toFloat(), offsetY = value.optDouble("offsetY", 0.0).toFloat(),
                spread = value.optDouble("spread", 0.0).toFloat(),
                color = IconColor.decode(value.optJSONObject("color")) ?: IconColor(0xFF000000.toInt()),
            ).normalized()
        }
    }
}

data class FontShadowOverride(val enabled: Boolean = false, val shadow: FontShadow = FontShadow()) {
    fun normalized() = copy(shadow = shadow.normalized())
    fun json(): JSONObject = JSONObject().put("enabled", enabled).put("shadow", shadow.json())

    companion object {
        fun fromLegacy(strength: Int): FontShadowOverride {
            val amount = strength.coerceIn(0, 24)
            if (amount == 0) return FontShadowOverride()
            return FontShadowOverride(true, FontShadow(
                preset = FontShadowPreset.Custom, opacity = ((.12f + .38f * amount / 24f) * 100).roundToInt(),
                blur = amount.toFloat(), offsetY = amount / 8f,
            ))
        }

        fun decode(value: JSONObject?, legacyStrength: Int = 0): FontShadowOverride = if (value == null) fromLegacy(legacyStrength)
            else FontShadowOverride(value.optBoolean("enabled", false), FontShadow.decode(value.optJSONObject("shadow"))).normalized()
    }
}

private fun Float.finiteIn(min: Float, max: Float): Float = if (isFinite()) coerceIn(min, max) else min
