package com.wanderwildwood.satsuire.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowsHeightTest {
    @Test
    fun allRowsWholeGiveTheirHeight() {
        val rows = listOf(RowExtent(0, 0, 100), RowExtent(1, 100, 50), RowExtent(2, 150, 1000))
        // Row 2 is the list's blank tail, not counted.
        assertEquals(150, rowsHeight(rows, rowCount = 2, viewportEnd = 1000))
    }

    @Test
    fun aRowOffThePageNeedsAllTheRoom() {
        val rows = listOf(RowExtent(0, 0, 600), RowExtent(1, 600, 500))
        assertNull(rowsHeight(rows, rowCount = 2, viewportEnd = 1000))
        assertNull(rowsHeight(rows.take(1), rowCount = 2, viewportEnd = 1000))
    }

    @Test
    fun notFromTheTopOrEmpty() {
        assertNull(rowsHeight(listOf(RowExtent(1, 0, 100)), rowCount = 1, viewportEnd = 1000))
        assertNull(rowsHeight(emptyList(), rowCount = 0, viewportEnd = 1000))
    }
}
