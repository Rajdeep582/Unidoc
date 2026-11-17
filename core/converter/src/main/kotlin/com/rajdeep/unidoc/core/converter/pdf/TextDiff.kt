package com.rajdeep.unidoc.core.converter.pdf

/** Myers O(ND) diff over tokens, with a common prefix/suffix trim and an edit-distance cap. */
object TextDiff {
    enum class Kind { SAME, ADDED, REMOVED }

    data class Op(val kind: Kind, val token: String)

    fun diff(a: List<String>, b: List<String>, maxD: Int = 2500): List<Op> {
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size
        var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) {
            endA--
            endB--
        }
        val prefix = a.subList(0, start).map { Op(Kind.SAME, it) }
        val suffix = a.subList(endA, a.size).map { Op(Kind.SAME, it) }
        val middle = myers(a.subList(start, endA), b.subList(start, endB), maxD)
        return prefix + middle + suffix
    }

    private fun myers(a: List<String>, b: List<String>, maxD: Int): List<Op> {
        val n = a.size
        val m = b.size
        if (n == 0) return b.map { Op(Kind.ADDED, it) }
        if (m == 0) return a.map { Op(Kind.REMOVED, it) }
        val max = n + m
        val offset = max
        val v = IntArray(2 * max + 2)
        val trace = ArrayList<IntArray>()
        var found = -1
        for (d in 0..minOf(max, maxD)) {
            // Store only the live diagonals (-d..d) so memory is O(D²) not O(D·(N+M)).
            trace += v.copyOfRange(offset - d, offset + d + 2)
            var k = -d
            while (k <= d) {
                var x = if (k == -d || k != d && v[offset + k - 1] < v[offset + k + 1]) v[offset + k + 1] else v[offset + k - 1] + 1
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) {
                    x++
                    y++
                }
                v[offset + k] = x
                if (x >= n && y >= m) {
                    found = d
                    break
                }
                k += 2
            }
            if (found >= 0) break
        }
        // Too different: report as full replacement.
        if (found < 0) return a.map { Op(Kind.REMOVED, it) } + b.map { Op(Kind.ADDED, it) }
        val ops = ArrayList<Op>()
        var x = n
        var y = m
        for (d in found downTo 1) {
            val vd = trace[d]

            // trace[d] holds diagonals -d..d+1 of the state before step d, indexed from -d.
            fun at(kk: Int) = vd[kk + d]
            val k = x - y
            val prevK = if (k == -d || k != d && at(k - 1) < at(k + 1)) k + 1 else k - 1
            val prevX = at(prevK)
            val prevY = prevX - prevK
            while (x > prevX && y > prevY) {
                ops += Op(Kind.SAME, a[x - 1])
                x--
                y--
            }
            if (x == prevX) ops += Op(Kind.ADDED, b[y - 1]).also { y-- } else ops += Op(Kind.REMOVED, a[x - 1]).also { x-- }
        }
        while (x > 0 && y > 0) {
            ops += Op(Kind.SAME, a[x - 1])
            x--
            y--
        }
        return ops.asReversed()
    }
}
