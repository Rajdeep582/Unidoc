package com.rajdeep.unidoc.core.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageRangesTest {
    @Test
    fun parsesRangesListsAndOpenEnds() {
        assertEquals((0 until 5).toList(), PageRanges.parse("", 5))
        assertEquals(listOf(0, 1, 2, 4), PageRanges.parse("1-3, 5", 5))
        assertEquals(listOf(7, 8, 9), PageRanges.parse("8-", 10))
        assertEquals(listOf(0, 1, 2), PageRanges.parse("-3", 10))
        assertEquals(listOf(2, 0, 1), PageRanges.parse("3,1,2", 3))
        assertEquals(listOf(4, 3, 2), PageRanges.parse("5-3", 5))
    }

    @Test
    fun rejectsInvalid() {
        assertThrows(IllegalArgumentException::class.java) { PageRanges.parse("0", 3) }
        assertThrows(IllegalArgumentException::class.java) { PageRanges.parse("4", 3) }
        assertThrows(IllegalArgumentException::class.java) { PageRanges.parse("a-b", 3) }
    }
}
