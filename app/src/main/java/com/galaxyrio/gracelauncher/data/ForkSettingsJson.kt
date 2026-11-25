package com.galaxyrio.gracelauncher.data

import org.json.JSONArray
import org.json.JSONObject

// Fork-only settings are stored as extra fields inside upstream's JSON columns, so the
// fork's database schema stays identical to upstream's and upstream migrations apply as-is.

/** The fork's former weather app choice in clockStyleJson; now only read, to carry it into upstream's weatherConnection. */
internal const val WeatherAppKeyField = "weatherAppKey"

/** Ids of the fork's old widget stacks in shortcutsFoldersJson; read once to convert them to upstream's column. */
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
