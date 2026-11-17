package com.rajdeep.unidoc.feature.converter.hub

import com.rajdeep.unidoc.core.model.ToolCategory
import com.rajdeep.unidoc.core.model.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterToolsTest {
    @Test
    fun noFilterReturnsEverything() {
        assertEquals(ToolId.entries.size, filterTools("", null).values.sumOf { it.size })
    }

    @Test
    fun categoryFilter() {
        val result = filterTools("", ToolCategory.SECURITY)
        assertEquals(setOf(ToolCategory.SECURITY), result.keys)
        assertEquals(3, result.getValue(ToolCategory.SECURITY).size)
    }

    @Test
    fun searchMatchesTitleDescriptionAndCategory() {
        assertTrue(ToolId.WORD_TO_PDF in filterTools("word", null).values.flatten())
        assertTrue(ToolId.PROTECT_PDF in filterTools("aes", null).values.flatten())
        assertTrue(filterTools("optimize", null).keys == setOf(ToolCategory.OPTIMIZE))
        assertTrue(filterTools("zzzz", null).isEmpty())
    }
}
