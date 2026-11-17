package com.rajdeep.unidoc.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Brand colors that stay constant even when Material You dynamic color is enabled,
 * so the Unidoc olive identity never changes.
 */
@Immutable
data class UnidocColors(
    val gradientStart: Color,
    val gradientMid: Color,
    val gradientEnd: Color,
    val onGradient: Color,
    val glow: Color,
    val success: Color,
    val warning: Color,
    val isDark: Boolean
) {
    val brandColors: List<Color> get() = listOf(gradientStart, gradientMid, gradientEnd)

    /** Diagonal brand gradient for CTAs, indicators and the logo. */
    val brandBrush: Brush get() = Brush.linearGradient(brandColors)

    /** Horizontal variant used on pills and progress bars. */
    val brandHorizontalBrush: Brush get() = Brush.horizontalGradient(brandColors)
}

internal val LightUnidocColors = UnidocColors(
    gradientStart = BrandPalette.Olive700,
    gradientMid = BrandPalette.Olive500,
    gradientEnd = BrandPalette.Olive400,
    onGradient = Color.White,
    glow = BrandPalette.Olive400.copy(alpha = 0.12f),
    success = Color(0xFF3E8E5A),
    warning = Color(0xFFC08A1E),
    isDark = false
)

internal val DarkUnidocColors = UnidocColors(
    gradientStart = BrandPalette.Olive600,
    gradientMid = BrandPalette.Olive500,
    gradientEnd = Color(0xFF9CB06C),
    onGradient = Color.White,
    glow = BrandPalette.Olive400.copy(alpha = 0.20f),
    success = Color(0xFF7CC98F),
    warning = Color(0xFFE2B85A),
    isDark = true
)

val LocalUnidocColors = staticCompositionLocalOf { LightUnidocColors }
