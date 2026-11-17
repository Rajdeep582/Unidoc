package com.rajdeep.unidoc.core.converter

/**
 * Parses human page ranges ("1-3, 5, 8-") into 0-based indices for a document of [pageCount] pages.
 * Blank → all pages. Open-ended "8-" runs to the last page; "-3" means 1..3. Order and duplicates
 * are preserved (useful for reordering), out-of-range pages raise an error.
 */
object PageRanges {
    fun parse(spec: String?, pageCount: Int): List<Int> {
        val text = spec?.trim().orEmpty()
        if (text.isEmpty()) return (0 until pageCount).toList()
        return text.split(',', ';', ' ').filter { it.isNotBlank() }.flatMap { token ->
            val t = token.trim()
            val dash = t.indexOf('-')
            val (from, to) = when {
                dash < 0 -> t.toPage() to t.toPage()
                dash == 0 -> 1 to t.substring(1).toPage()
                dash == t.length - 1 -> t.substring(0, dash).toPage() to pageCount
                else -> t.substring(0, dash).toPage() to t.substring(dash + 1).toPage()
            }
            require(from in 1..pageCount && to in 1..pageCount) {
                "Page ${if (from !in 1..pageCount) from else to} doesn't exist (document has $pageCount)"
            }
            if (from <= to) (from..to).map { it - 1 } else (from downTo to).map { it - 1 }
        }
    }

    private fun String.toPage(): Int = trim().toIntOrNull() ?: throw IllegalArgumentException("“$this” isn't a page number")
}
