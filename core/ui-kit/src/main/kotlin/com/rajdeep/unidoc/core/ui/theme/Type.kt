package com.rajdeep.unidoc.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.ui.R

private fun variableFont(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Inter (variable) for body and UI text. SIL OFL 1.1. */
val InterFamily = FontFamily(
    variableFont(R.font.inter, 400),
    variableFont(R.font.inter, 500),
    variableFont(R.font.inter, 600),
    variableFont(R.font.inter, 700)
)

/** Space Grotesk (variable) for display and headlines — the "high-tech but calm" voice. SIL OFL 1.1. */
val GroteskFamily = FontFamily(
    variableFont(R.font.space_grotesk, 400),
    variableFont(R.font.space_grotesk, 500),
    variableFont(R.font.space_grotesk, 600),
    variableFont(R.font.space_grotesk, 700)
)

// Headings balance their lines (no single orphan word on the last line); body text wraps as a
// paragraph and never hyphenates mid-word.
private val Display = TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading)
private val Body =
    TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None)

internal val UnidocTypography = Typography(
    displayLarge = Display.copy(fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-0.03).em),
    displayMedium = Display.copy(fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.025).em),
    displaySmall = Display.copy(fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.02).em),
    headlineLarge = Display.copy(fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em),
    headlineMedium = Display.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.015).em),
    headlineSmall = Display.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em),
    titleLarge = Display.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = Body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading),
    titleSmall = Body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading),
    bodyLarge = Body.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Body.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Body.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = Body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = Body.copy(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.02.em
    )
)
