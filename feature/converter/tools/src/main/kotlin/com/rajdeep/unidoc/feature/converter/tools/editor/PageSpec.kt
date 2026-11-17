package com.rajdeep.unidoc.feature.converter.tools.editor

/** One page in the organizer: source index (0-based) and extra clockwise rotation. */
data class PageItem(val source: Int, val rotation: Int = 0)

/** Converts between visual page edits and the engine's text options ("1-3, 5", "2:90"). */
object PageSpec {
    /** 0-based indices → compact 1-based ranges, keeping order ("1-3, 7, 5"). */
    fun format(indices: List<Int>): String {
        if (indices.isEmpty()) return ""
        val parts = ArrayList<String>()
        var start = indices[0]
        var prev = start
        fun flush() {
            parts += when {
                start == prev -> "${start + 1}"
                else -> "${start + 1}-${prev + 1}"
            }
        }
        for (i in 1 until indices.size) {
            val v = indices[i]
            if (v == prev + 1 && prev >= start) {
                prev = v
            } else {
                flush()
                start = v
                prev = v
            }
        }
        flush()
        return parts.joinToString(", ")
    }

    /** Lenient parse of 1-based ranges into 0-based indices; invalid tokens are skipped. */
    fun parse(spec: String?, pageCount: Int): List<Int> {
        val text = spec?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        return text.split(',', ';', ' ').filter { it.isNotBlank() }.flatMap { token ->
            val t = token.trim()
            val dash = t.indexOf('-')
            val (a, b) = when {
                dash < 0 -> t.toIntOrNull() to t.toIntOrNull()
                dash == 0 -> 1 to t.substring(1).toIntOrNull()
                dash == t.length - 1 -> t.substring(0, dash).toIntOrNull() to pageCount
                else -> t.substring(0, dash).toIntOrNull() to t.substring(dash + 1).toIntOrNull()
            }
            if (a == null || b == null || a !in 1..pageCount || b !in 1..pageCount) {
                emptyList()
            } else if (a <= b) {
                (a..b).map { it - 1 }
            } else {
                (a downTo b).map { it - 1 }
            }
        }
    }

    fun items(order: String?, rotate: String?, pageCount: Int): List<PageItem> {
        val rotations = rotate.orEmpty().split(',').mapNotNull { t ->
            val p = t.split(':').map { it.trim() }
            val page = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val angle = p.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            (page - 1) to ((angle % 360 + 360) % 360)
        }.toMap()
        val sources = parse(order, pageCount).distinct().ifEmpty { (0 until pageCount).toList() }
        return sources.map { PageItem(it, rotations[it] ?: 0) }
    }

    /** Organizer state → ("order", "rotate") option values; blank when unchanged. */
    fun encode(items: List<PageItem>, pageCount: Int): Pair<String, String> {
        val identity = items.map { it.source } == (0 until pageCount).toList()
        val order = if (identity) "" else format(items.map { it.source })
        val rotate = items.filter { it.rotation != 0 }.joinToString(", ") { "${it.source + 1}:${it.rotation}" }
        return order to rotate
    }
}
