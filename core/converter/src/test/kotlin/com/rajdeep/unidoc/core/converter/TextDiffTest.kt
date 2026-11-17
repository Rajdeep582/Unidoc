package com.rajdeep.unidoc.core.converter

import com.rajdeep.unidoc.core.converter.pdf.TextDiff
import com.rajdeep.unidoc.core.converter.pdf.TextDiff.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class TextDiffTest {
    private fun apply(ops: List<TextDiff.Op>, keep: Kind) = ops.filter { it.kind == Kind.SAME || it.kind == keep }.map { it.token }

    @Test
    fun reconstructsBothSides() {
        val a = "the quick brown fox jumps over the lazy dog".split(' ')
        val b = "the quick red fox leaps over the very lazy dog today".split(' ')
        val ops = TextDiff.diff(a, b)
        assertEquals(a, apply(ops, Kind.REMOVED))
        assertEquals(b, apply(ops, Kind.ADDED))
        assertEquals(6, ops.count { it.kind != Kind.SAME })
    }

    @Test
    fun identicalAndEmpty() {
        val a = listOf("x", "y")
        assertEquals(0, TextDiff.diff(a, a).count { it.kind != Kind.SAME })
        assertEquals(listOf(Kind.ADDED, Kind.ADDED), TextDiff.diff(emptyList(), a).map { it.kind })
        assertEquals(listOf(Kind.REMOVED, Kind.REMOVED), TextDiff.diff(a, emptyList()).map { it.kind })
    }

    @Test
    fun randomizedConsistency() {
        val rnd = java.util.Random(7)
        repeat(50) {
            val a = List(rnd.nextInt(60)) { "w${rnd.nextInt(8)}" }
            val b = List(rnd.nextInt(60)) { "w${rnd.nextInt(8)}" }
            val ops = TextDiff.diff(a, b)
            assertEquals(a, apply(ops, Kind.REMOVED))
            assertEquals(b, apply(ops, Kind.ADDED))
        }
    }
}
