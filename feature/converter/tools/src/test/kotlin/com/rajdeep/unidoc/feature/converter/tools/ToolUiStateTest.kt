package com.rajdeep.unidoc.feature.converter.tools

import com.rajdeep.unidoc.core.model.PickedFile
import com.rajdeep.unidoc.core.model.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolUiStateTest {
    private val pdf = PickedFile("u1", "a.pdf", 10, "application/pdf")

    @Test
    fun everyToolHasValidDefaults() {
        ToolId.entries.forEach { tool ->
            val spec = ToolOptions.forTool(tool)
            assertEquals("duplicate keys in $tool", spec.size, spec.map { it.key }.toSet().size)
            spec.filterIsInstance<ToolOption.Choice>().forEach { c ->
                assertTrue(c.choices.any { it.first == c.default })
            }
        }
    }

    @Test
    fun mergeNeedsTwoFiles() {
        assertEquals("Add at least 2 files", ToolUiState(ToolId.MERGE_PDF, files = listOf(pdf)).blocker)
        assertNull(ToolUiState(ToolId.MERGE_PDF, files = listOf(pdf, pdf.copy(uri = "u2"))).blocker)
    }

    @Test
    fun requiredTextBlocks() {
        val s = ToolUiState(ToolId.PROTECT_PDF, files = listOf(pdf))
        assertEquals("Fill in the required fields", s.blocker)
        assertNull(s.copy(options = s.options + ("password" to "secret")).blocker)
    }

    @Test
    fun htmlAcceptsUrlInsteadOfFile() {
        val s = ToolUiState(ToolId.HTML_TO_PDF)
        assertEquals("Add a file to start", s.blocker)
        assertNull(s.copy(options = s.options + ("url" to "https://example.com")).blocker)
    }

    @Test
    fun textToImageNeedsNoFile() {
        val s = ToolUiState(ToolId.TEXT_TO_IMAGE)
        assertEquals("Fill in the required fields", s.blocker)
        assertNull(s.copy(options = s.options + ("text" to "hello")).blocker)
    }
}
