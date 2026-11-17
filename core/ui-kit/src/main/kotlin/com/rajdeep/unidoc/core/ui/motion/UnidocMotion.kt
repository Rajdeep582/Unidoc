package com.rajdeep.unidoc.core.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Motion tokens. Springs for anything spatial (position, size, scale) so motion stays interruptible;
 * eased tweens only for pure opacity/color.
 */
object UnidocMotion {
    const val DURATION_SHORT = 150
    const val DURATION_MEDIUM = 280
    const val DURATION_LONG = 450

    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Default spatial spring: quick, very slight overshoot. */
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = 420f)

    /** Slower, softer spatial spring for large surfaces. */
    fun <T> spatialSlow(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    /** Bouncy spring for small, playful feedback (icon pops, press release). */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 650f)

    /** Non-overshooting spring for effects (alpha, color). */
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
}
