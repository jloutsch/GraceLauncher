package com.galaxyrio.gracelauncher.data

import org.json.JSONArray
import org.json.JSONObject

/** Presentation shared by every reference to a stack; widget IDs remain owned by its popup. */
data class WidgetStackStyle(
    /** Height of the hosted content only; pagination is a separate row below it. */
    val height: Int = 240,
) {
    fun layoutHeight(widgetCount: Int): Int = height.coerceIn(80, 1200) + widgetStackIndicatorHeight(widgetCount)

    fun normalized(): WidgetStackStyle = copy(
        height = height.coerceIn(80, 1200),
    )

    fun encode(): String = normalized().let { value ->
        JSONObject().put("height", value.height).toString()
    }

    companion object {
        /** Null denotes an ordinary folder/app stack, including every pre-migration row. */
        fun decode(json: String?): WidgetStackStyle? = json?.let {
            runCatching {
                val value = JSONObject(it)
                WidgetStackStyle(value.optInt("height", 240)).normalized()
            }.getOrDefault(WidgetStackStyle())
        }
    }
}

internal fun widgetStackIndicatorHeight(widgetCount: Int): Int = if (widgetCount > 1) 20 else 0

/** Change only legacy background fields; preserve member identities, order and explicit overrides. */
internal fun migrateWidgetStackMemberBackgrounds(members: JSONArray, legacy: JSONObject): Boolean {
    val color = legacy.optJSONObject("backgroundColor")
    val alpha = legacy.optDouble("backgroundAlpha", 0.0).toFloat()
        .takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    var changed = false
    for (index in 0 until members.length()) {
        val widget = members.optJSONObject(index)?.optJSONObject("widget") ?: continue
        if (widget.has("popupBackgroundAlpha")) continue
        widget.put("popupBackground", alpha > 0f)
        widget.put("popupBackgroundColor", color ?: JSONObject.NULL)
        // A formerly transparent stack stays off, but enabling its new switch must be visible.
        widget.put("popupBackgroundAlpha", if (alpha > 0f) alpha else 1f)
        changed = true
    }
    return changed
}
