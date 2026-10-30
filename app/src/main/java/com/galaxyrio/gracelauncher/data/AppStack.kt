package com.galaxyrio.gracelauncher.data

internal const val AppStackInset = 8f
private const val AppStackColumnGap = 12f
/** Page dots under a stack with more than one page. */
internal const val StackDotsHeight = 14f

/** Shared by the inline surface and widget-grid footprint, in density-independent units. */
internal fun appStackColumns(width: Float, iconSize: Float): Int =
    ((width - AppStackInset * 2 + AppStackColumnGap) / (iconSize + AppStackColumnGap)).toInt().coerceAtLeast(2)

/** One row of icons; apps beyond it go on further pages, which add the page dots below. */
internal fun appStackHeight(iconSize: Float, paged: Boolean = false): Float =
    AppStackInset * 2 + iconSize + if (paged) StackDotsHeight else 0f

/** Full-width stacks insert temporary rows; never persist these expansion-only coordinates. */
internal fun expandWidgetStacks(cells: List<WidgetGridCell>, rows: Map<String, Int>): List<WidgetGridCell> = cells.map { cell ->
    val shift = cells.filter { it.row < cell.row }.sumOf { ((rows[it.key] ?: 1) - 1).coerceAtLeast(0) }
    cell.copy(row = cell.row + shift, size = rows[cell.key]?.let { cell.size.copy(rows = it.coerceAtLeast(1)) } ?: cell.size)
}

internal fun collapseWidgetStacks(cells: List<WidgetGridCell>, stackKeys: Set<String>): List<WidgetGridCell> = cells.map { cell ->
    val shift = cells.filter { it.key in stackKeys && it.row < cell.row }.sumOf { (it.size.rows - 1).coerceAtLeast(0) }
    cell.copy(row = (cell.row - shift).coerceAtLeast(0),
        size = if (cell.key in stackKeys) cell.size.copy(rows = 1) else cell.size)
}
