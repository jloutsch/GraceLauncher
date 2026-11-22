package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.stackPages
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStackPagesTest {
    private fun pages(items: String, columns: Int) =
        stackPages(items.toList(), Char::isUpperCase, columns).map { it.joinToString("") }

    @Test fun widgetsOnlyGiveOnePageEach() = assertEquals(listOf("A", "B"), pages("AB", 4))

    @Test fun appsAfterTheWidgetsFillRowsOfTheColumnCount() =
        assertEquals(listOf("A", "B", "abcd", "ef"), pages("ABabcdef", 4))

    @Test fun appsKeepTheirPlaceBetweenWidgets() =
        assertEquals(listOf("ab", "A", "c", "B"), pages("abAcB", 4))

    @Test fun emptyAndAppOnlyStacks() {
        assertEquals(emptyList<String>(), pages("", 4))
        assertEquals(listOf("ab", "c"), pages("abc", 2))
    }
}
