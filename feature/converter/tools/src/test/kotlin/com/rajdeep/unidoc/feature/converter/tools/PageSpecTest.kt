package com.rajdeep.unidoc.feature.converter.tools

import com.rajdeep.unidoc.feature.converter.tools.editor.PageItem
import com.rajdeep.unidoc.feature.converter.tools.editor.PageSpec
import com.rajdeep.unidoc.feature.converter.tools.editor.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageSpecTest {
    @Test
    fun formatCompactsRuns() {
        assertEquals("1-3, 7, 5", PageSpec.format(listOf(0, 1, 2, 6, 4)))
        assertEquals("", PageSpec.format(emptyList()))
        assertEquals("4", PageSpec.format(listOf(3)))
    }

    @Test
    fun parseIsLenientAndRoundTrips() {
        assertEquals(listOf(0, 1, 2, 6, 4), PageSpec.parse("1-3, 7, 5", 10))
        assertEquals(listOf(7, 8, 9), PageSpec.parse("8-", 10))
        assertEquals(listOf(2, 1, 0), PageSpec.parse("3-1", 10))
        assertEquals(listOf(1), PageSpec.parse("x, 2, 99", 10))
    }

    @Test
    fun organizerEncodeDecode() {
        val items = listOf(PageItem(2, 90), PageItem(0), PageItem(1, 180))
        val (order, rotate) = PageSpec.encode(items, 4)
        assertEquals("3, 1-2", order)
        assertEquals("3:90, 2:180", rotate)
        assertEquals(items, PageSpec.items(order, rotate, 4))
        assertEquals("" to "", PageSpec.encode((0 until 4).map { PageItem(it) }, 4))
        assertEquals(4, PageSpec.items("", "", 4).size)
    }

    @Test
    fun placementRoundTrip() {
        val p = Placement(2, 0.25f, 0.5f, 0.3f)
        assertEquals(p, Placement.decode(p.encode()))
        assertNull(Placement.decode("1,2"))
        assertNull(Placement.decode(""))
    }
}
