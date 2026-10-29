package com.galaxyrio.gracelauncher.data.backup

import com.galaxyrio.gracelauncher.data.HomeContentKey
import com.galaxyrio.gracelauncher.data.WidgetScreenKey

private const val ProfilePrefix = "profile:"
private val NonAppPrefixes = listOf("folder:", "widget:", "grace:", "shortcut:")

// "profile:<serial>:<rest>" -> serial and rest; null for any other key.
private fun splitProfileKey(key: String): Pair<Long, String>? {
    if (!key.startsWith(ProfilePrefix)) return null
    val end = key.indexOf(':', ProfilePrefix.length)
    val serial = key.substring(ProfilePrefix.length, if (end < 0) key.length else end).toLongOrNull() ?: return null
    return if (end < 0) null else serial to key.substring(end + 1)
}

fun isAppKey(key: String): Boolean {
    val component = splitProfileKey(key)?.second ?: key
    return '/' in component && NonAppPrefixes.none { component.startsWith(it) } &&
        component != HomeContentKey && component != WidgetScreenKey
}

fun missingAppKeys(referenced: Iterable<String>, installed: Set<String>): List<String> =
    referenced.filter { isAppKey(it) && it !in installed }.distinct()

/**
 * Saved app keys whose activity is gone, mapped to the only launcher activity left in the same package and profile.
 * Some apps rename their launcher activity in every update; packages with several activities are left alone.
 */
fun renamedAppKeys(saved: Iterable<String>, installed: Collection<String>): Map<String, String> {
    // "profile:7:com.example/Main" -> "profile:7:com.example"
    val byPackage = installed.filter(::isAppKey).groupBy { it.substringBefore('/') }
    return missingAppKeys(saved, installed.toSet())
        .mapNotNull { key -> byPackage[key.substringBefore('/')]?.singleOrNull()?.let { key to it } }.toMap()
}

fun remapProfileKey(key: String, serials: Map<Long, Long>): String {
    val (serial, rest) = splitProfileKey(key) ?: return key
    val mapped = serials[serial] ?: return key
    return "$ProfilePrefix$mapped:$rest"
}
