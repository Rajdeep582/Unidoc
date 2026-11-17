package com.rajdeep.unidoc.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {
    @Test
    fun everyCategoryHasTools() {
        val grouped = ToolId.byCategory()
        ToolCategory.entries.forEach { assertTrue("$it empty", grouped[it].orEmpty().isNotEmpty()) }
    }

    @Test
    fun fileBoundsAreConsistent() {
        ToolId.entries.forEach { tool ->
            assertTrue("$tool min>max", tool.minFiles <= tool.maxFiles || tool.maxFiles == 0)
            if (tool.input == ToolInput.NONE) assertEquals("$tool takes no files", 0, tool.maxFiles)
        }
    }

    @Test
    fun catalogCoversPlanScope() {
        assertEquals(35, ToolId.entries.size)
    }
}
