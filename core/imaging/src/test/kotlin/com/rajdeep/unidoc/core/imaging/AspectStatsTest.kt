package com.rajdeep.unidoc.core.imaging

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Test

class AspectStatsTest {
    @Test
    fun pinholeAspectBeatsEdgeRatio() {
        val rnd = java.util.Random(5)
        val errNew = ArrayList<Double>()
        val errOld = ArrayList<Double>()
        repeat(2000) {
            val imgW = 3000
            val imgH = 4000
            val f = 4000 * (0.6 + rnd.nextDouble() * 0.6)
            val tx = Math.toRadians(rnd.nextGaussian() * 15)
            val ty = Math.toRadians(rnd.nextGaussian() * 10)
            val rz = Math.toRadians(rnd.nextGaussian() * 8)
            val wM = 0.21
            val hM = if (rnd.nextBoolean()) 0.297 else 0.15 + rnd.nextDouble() * 0.4
            val dist = 0.35 + rnd.nextDouble() * 0.2
            val shiftX = rnd.nextGaussian() * 0.03
            val shiftY = rnd.nextGaussian() * 0.03
            fun p(x0: Double, y0: Double): Pt {
                val x = x0 * cos(rz) - y0 * sin(rz)
                val yy = x0 * sin(rz) + y0 * cos(rz)
                val y1 = yy * cos(tx)
                val z1 = yy * sin(tx)
                val x2 = x * cos(ty) + z1 * sin(ty) + shiftX
                val z2 = -x * sin(ty) + z1 * cos(ty) + dist
                return Pt(imgW / 2.0 + f * x2 / z2, imgH / 2.0 + f * (y1 + shiftY) / z2)
            }
            val q = Quad(p(-wM / 2, -hM / 2), p(wM / 2, -hM / 2), p(wM / 2, hM / 2), p(-wM / 2, hM / 2))
            val truth = wM / hM
            errNew += Math.abs(PerspectiveWarper.estimateAspect(q, imgW, imgH) / truth - 1) * 100
            errOld += Math.abs(q.averageWidth() / q.averageHeight() / truth - 1) * 100
        }
        fun s(l: List<Double>) = l.sorted().let {
            "median %.2f%% p90 %.2f%% p99 %.2f%% p99.8 %.2f%% max %.2f%%".format(
                it[it.size / 2],
                it[
                    it.size *
                        9 /
                        10
                ],
                it[it.size * 99 / 100],
                it[it.size * 998 / 1000],
                it.last()
            )
        }
        println("ASPECT new: ${s(errNew)}")
        println("ASPECT old: ${s(errOld)}")
        val sorted = errNew.sorted()
        org.junit.Assert.assertTrue("p99 aspect error ${sorted[sorted.size * 99 / 100]}", sorted[sorted.size * 99 / 100] < 1.0)
    }
}
