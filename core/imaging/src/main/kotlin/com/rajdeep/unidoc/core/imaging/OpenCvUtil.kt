package com.rajdeep.unidoc.core.imaging

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Runs [block] and releases every Mat registered with [track] afterwards. */
internal inline fun <T> withMats(block: MatScope.() -> T): T {
    val scope = MatScope()
    try {
        return scope.block()
    } finally {
        scope.releaseAll()
    }
}

internal class MatScope {
    private val mats = ArrayList<Mat>()

    fun <M : Mat> track(m: M): M {
        mats += m
        return m
    }

    fun mat(): Mat = track(Mat())

    fun releaseAll() {
        mats.forEach { it.release() }
        mats.clear()
    }
}

internal fun Int.odd(): Int = if (this % 2 == 0) this + 1 else this

/** Converts to single-channel gray (copy). */
internal fun Mat.toGray(): Mat {
    val out = Mat()
    when (channels()) {
        1 -> copyTo(out)
        3 -> Imgproc.cvtColor(this, out, Imgproc.COLOR_BGR2GRAY)
        else -> Imgproc.cvtColor(this, out, Imgproc.COLOR_BGRA2GRAY)
    }
    return out
}

/** Converts to 3-channel BGR (copy). */
internal fun Mat.toBgr(): Mat {
    val out = Mat()
    when (channels()) {
        1 -> Imgproc.cvtColor(this, out, Imgproc.COLOR_GRAY2BGR)
        3 -> copyTo(out)
        else -> Imgproc.cvtColor(this, out, Imgproc.COLOR_BGRA2BGR)
    }
    return out
}

/** Resizes so the longest side is at most [maxSide]; returns a copy and the scale used. */
internal fun Mat.fitWithin(maxSide: Int): Pair<Mat, Double> {
    val longest = maxOf(cols(), rows())
    val out = Mat()
    if (longest <= maxSide) {
        copyTo(out)
        return out to 1.0
    }
    val s = maxSide.toDouble() / longest
    Imgproc.resize(this, out, Size(cols() * s, rows() * s), 0.0, 0.0, Imgproc.INTER_AREA)
    return out to s
}
