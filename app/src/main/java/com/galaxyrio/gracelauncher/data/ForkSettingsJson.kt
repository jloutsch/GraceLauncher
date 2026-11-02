package com.galaxyrio.gracelauncher.data

import org.json.JSONArray
import org.json.JSONObject

// Fork-only settings are stored as extra fields inside upstream's JSON columns, so the
// fork's database schema stays identical to upstream's and upstream migrations apply as-is.

/** The app opened from the weather forecast; stored in clockStyleJson. */
internal const val WeatherAppKeyField = "weatherAppKey"

/** Ids of folders shown as widget stacks; stored in shortcutsFoldersJson. */
internal const val WidgetStackFoldersField = "widgetStackFolderIds"

internal fun String.withField(name: String, value: Any?): String =
    JSONObject(this).apply { if (value == null) remove(name) else put(name, value) }.toString()

internal fun stringField(json: String?, name: String): String? = runCatching {
    JSONObject(json ?: "{}").optString(name).takeIf(String::isNotEmpty)
}.getOrNull()

internal fun stringSetField(json: String?, name: String): Set<String> = runCatching {
    val array = JSONObject(json ?: "{}").optJSONArray(name) ?: JSONArray()
    (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.takeIf(String::isNotBlank) }.toSet()
}.getOrDefault(emptySet())

internal fun Set<String>.toJsonArrayOrNull(): JSONArray? = if (isEmpty()) null else JSONArray(sorted())
