package com.galaxyrio.gracelauncher.data.backup

import com.galaxyrio.gracelauncher.data.PopupItem

/** Restored widgets get temporary negative IDs until each is bound to a real host ID. -1 means "no widget". */
const val FIRST_PLACEHOLDER_ID = -2

fun isWidgetPlaceholder(id: Int) = id <= FIRST_PLACEHOLDER_ID

data class WidgetLayout(val popups: Map<String, List<PopupItem>>, val homeOrder: List<String>?)

data class PendingWidget(val ownerKey: String, val placeholderId: Int, val provider: String?)

private fun widgetKey(id: Int) = "widget:$id"

fun WidgetLayout.toPlaceholders(): WidgetLayout = renumbered(bound = true)

/** Fork (#38): only renumbers repeated placeholders, for layouts saved by an import before toPlaceholders() did. */
fun WidgetLayout.withUniquePlaceholders(): WidgetLayout = renumbered(bound = false)

/** [bound]: also turn bound widget IDs into placeholders, as an import does. */
private fun WidgetLayout.renumbered(bound: Boolean): WidgetLayout {
    // Skip used IDs instead of counting down from the lowest one: a hand-made file can hold Int.MIN_VALUE.
    val used = popups.values.flatten().mapNotNull { it.widget?.widgetId }.filter(::isWidgetPlaceholder).toMutableSet()
    var free = FIRST_PLACEHOLDER_ID
    fun nextFree(): Int { while (free in used) free--; return free.also { used += it } }
    val renamed = mutableMapOf<String, String>()
    // Fork: a placeholder ID seen twice would be restored into both entries and one dropped on save, so renumber repeats.
    val kept = mutableSetOf<Int>()
    // Keys that still name a kept placeholder: a repeat sharing that key must not take over its home position.
    val keptKeys = mutableSetOf<String>()
    val remapped = popups.toSortedMap().mapValues { (_, items) ->
        items.map { item ->
            val widget = item.widget
            if (widget == null || (!bound && widget.widgetId >= 0) || (widget.widgetId < 0 && (!isWidgetPlaceholder(widget.widgetId) ||
                    kept.add(widget.widgetId).also { if (it) keptKeys += item.key }))) item else {
                val id = nextFree()
                if (item.key !in keptKeys) renamed.putIfAbsent(item.key, widgetKey(id))
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

/**
 * Fork #7: whether [placeholderId] still names a slot saved for [provider]. A second import restarts the numbers, so
 * a setup screen left open from the first one must not fill the new slot with a different widget.
 */
fun WidgetLayout.accepts(placeholderId: Int, provider: String): Boolean {
    val slot = popups.values.flatten().firstOrNull { it.widget?.widgetId == placeholderId }?.widget ?: return false
    return slot.widgetProvider == null || slot.widgetProvider == provider
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

/** Imported widgets still waiting for setup, across home, the widget screen, widget stacks and pop-ups. */
fun Map<String, List<PopupItem>>.pendingWidgetCount(): Int =
    values.sumOf { items -> items.count { item -> item.widget?.let { isWidgetPlaceholder(it.widgetId) } == true } }

/** The widget the next tap sets up: the first one not skipped after an error, or the first again once all were. */
fun List<PendingWidget>.nextToRestore(skipped: Set<Int>): PendingWidget? =
    firstOrNull { it.placeholderId !in skipped } ?: firstOrNull()
