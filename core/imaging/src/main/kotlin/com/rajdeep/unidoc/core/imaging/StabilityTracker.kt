package com.rajdeep.unidoc.core.imaging

/**
 * Decides when a live detection is steady enough to auto-capture.
 * Feed normalized quads (0..1) per analyzed frame; [progress] rises while corners stay within
 * [tolerance] of the previous frame and the frame is sharp, and resets on movement or loss.
 */
class StabilityTracker(
    private val requiredFrames: Int = 12,
    private val tolerance: Double = 0.02,
    private val minSharpness: Double = 45.0
) {
    private var last: Quad? = null
    private var stableFrames = 0

    val progress: Float get() = (stableFrames.toFloat() / requiredFrames).coerceIn(0f, 1f)
    val isReady: Boolean get() = stableFrames >= requiredFrames

    fun update(normalizedQuad: Quad?, sharpness: Double): Float {
        val prev = last
        stableFrames = when {
            normalizedQuad == null -> 0
            prev == null -> 0
            normalizedQuad.maxCornerDistance(prev) > tolerance -> 0
            sharpness < minSharpness -> stableFrames.coerceAtMost(requiredFrames - 1)
            else -> stableFrames + 1
        }
        last = normalizedQuad
        return progress
    }

    fun reset() {
        last = null
        stableFrames = 0
    }
}
