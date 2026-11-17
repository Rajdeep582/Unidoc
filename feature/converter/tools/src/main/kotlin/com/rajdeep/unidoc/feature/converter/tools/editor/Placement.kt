package com.rajdeep.unidoc.feature.converter.tools.editor

import java.util.Locale

/** Where a stamp goes: page (0-based), top-left (x, y) and width [w], as fractions of the displayed page. */
data class Placement(val page: Int, val x: Float, val y: Float, val w: Float) {
    fun encode(): String = String.format(Locale.US, "%d,%.4f,%.4f,%.4f", page, x, y, w)

    companion object {
        fun decode(value: String?): Placement? {
            val p = value?.split(',')?.map { it.trim() } ?: return null
            if (p.size != 4) return null
            val page = p[0].toIntOrNull() ?: return null
            val (x, y, w) = p.drop(1).map { it.toFloatOrNull() ?: return null }
            return Placement(page, x, y, w)
        }
    }
}
