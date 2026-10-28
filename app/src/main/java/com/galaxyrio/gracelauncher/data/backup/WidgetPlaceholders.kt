package com.galaxyrio.gracelauncher.data.backup

import com.galaxyrio.gracelauncher.data.PopupItem

/** Restored widgets get temporary negative IDs until each is bound to a real host ID. -1 means "no widget". */
const val FIRST_PLACEHOLDER_ID = -2

fun isWidgetPlaceholder(id: Int) = id <= FIRST_PLACEHOLDER_ID

data class WidgetLayout(val popups: Map<String, List<PopupItem>>, val homeOrder: List<String>?)

data class PendingWidget(val ownerKey: String, val placeholderId: Int, val provider: String?)

private fun widgetKey(id: Int) = "widget:$id"

fun WidgetLayout.toPlaceholders(): WidgetLayout {
    // Skip used IDs instead of counting down from the lowest one: a hand-made file can hold Int.MIN_VALUE.
    val used = popups.values.flatten().mapNotNull { it.widget?.widgetId }.filter(::isWidgetPlaceholder).toMutableSet()
    var free = FIRST_PLACEHOLDER_ID
    fun nextFree(): Int { while (free in used) free--; return free.also { used += it } }
    val renamed = mutableMapOf<String, String>()
    val remapped = popups.toSortedMap().mapValues { (_, items) ->
        items.map { item ->
            val widget = item.widget
            if (widget == null || widget.widgetId < 0) item else {
                val id = nextFree()
                renamed.putIfAbsent(item.key, widgetKey(id))
                item.copy(key = widgetKey(id), widget = widget.copy(widgetId = id))
            }
        }
    }
    return WidgetLayout(remapped, homeOrder?.map { renamed[it] ?: it })
}

fun WidgetLayout.pending(): List<PendingWidget> = popups.toSortedMap().flatMap { (owner, items) ->
    items.mapNotNull { item -> item.widget?.takeIf { isWidgetPlaceholder(it.widgetId) }
        ?.let { PendingWidget(owner, it.widgetId, it.widgetProvider) } }
}

fun WidgetLayout.replaceWidget(placeholderId: Int, newId: Int): WidgetLayout {
    val oldKey = widgetKey(placeholderId)
    return WidgetLayout(
        popups.mapValues { (_, items) -> items.map { item ->
            if (item.widget?.widgetId != placeholderId) item
            else item.copy(key = widgetKey(newId), widget = item.widget.copy(widgetId = newId))
        } },
        homeOrder?.map { if (it == oldKey) widgetKey(newId) else it },
    )
}

fun WidgetLayout.dropWidget(placeholderId: Int): WidgetLayout {
    val oldKey = widgetKey(placeholderId)
    return WidgetLayout(
        popups.mapValues { (_, items) -> items.filterNot { it.widget?.widgetId == placeholderId } },
        homeOrder?.filterNot { it == oldKey },
    )
}
