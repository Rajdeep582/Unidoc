package com.rajdeep.unidoc.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {
    @Test
    fun buildsPrefixQueryAndStripsSyntax() {
        assertEquals("elec* bill*", LibraryRepository.toFtsQuery("  Elec  BILL "))
        assertEquals("o* reilly*", LibraryRepository.toFtsQuery("o' reilly\"*"))
        assertEquals("बिल*", LibraryRepository.toFtsQuery("बिल"))
        assertNull(LibraryRepository.toFtsQuery(" *\" "))
    }
}
