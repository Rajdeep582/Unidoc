package com.rajdeep.unidoc.core.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun fileSizes() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("1023 B", formatFileSize(1023))
        assertEquals("1 KB", formatFileSize(1024))
        assertEquals("812 KB", formatFileSize(812 * 1024))
        assertEquals("3.4 MB", formatFileSize((3.4 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", formatFileSize(1024L * 1024 * 1024))
    }

    @Test
    fun relativeTimes() {
        val now = 1_000_000_000_000L
        assertEquals("Just now", formatRelativeTime(now - 10_000, now))
        assertEquals("12 min ago", formatRelativeTime(now - 12 * 60_000, now))
        assertEquals("3 h ago", formatRelativeTime(now - 3 * 3_600_000, now))
        assertEquals("Yesterday", formatRelativeTime(now - 30 * 3_600_000L, now))
    }
}
