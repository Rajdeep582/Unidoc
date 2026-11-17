package com.rajdeep.unidoc.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Raw brand palette: calm olive greens on warm whites. Prefer [MaterialTheme.colorScheme] or
 * [UnidocTheme.colors] in UI code.
 */
object BrandPalette {
    val Olive900 = Color(0xFF2B3519)
    val Olive800 = Color(0xFF3A4722)
    val Olive700 = Color(0xFF4A5B2C)
    val Olive600 = Color(0xFF5A6E36)
    val Olive500 = Color(0xFF6E8443)
    val Olive400 = Color(0xFF8A9E5C)
    val Olive300 = Color(0xFFAFBE87)
    val Olive200 = Color(0xFFD0DAB4)
    val Olive100 = Color(0xFFE7EDD8)
    val Olive50 = Color(0xFFF4F7EE)
    val Sage = Color(0xFF7E8C66)
    val Khaki = Color(0xFF9A8B55)
    val Ink = Color(0xFF11140D)
    val Mist = Color(0xFFFAFBF6)
}

internal val LightColors = lightColorScheme(
    primary = BrandPalette.Olive600,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BrandPalette.Olive100,
    onPrimaryContainer = Color(0xFF1C2410),
    inversePrimary = BrandPalette.Olive300,
    secondary = BrandPalette.Sage,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6EADB),
    onSecondaryContainer = Color(0xFF242B19),
    tertiary = BrandPalette.Khaki,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0EAD2),
    onTertiaryContainer = Color(0xFF332B0E),
    background = BrandPalette.Mist,
    onBackground = Color(0xFF1B1F15),
    surface = BrandPalette.Mist,
    onSurface = Color(0xFF1B1F15),
    surfaceVariant = Color(0xFFE5E9DA),
    onSurfaceVariant = Color(0xFF59604D),
    surfaceTint = BrandPalette.Olive600,
    inverseSurface = Color(0xFF2C3224),
    inverseOnSurface = Color(0xFFF1F4EA),
    error = Color(0xFFB9443A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF8E0DB),
    onErrorContainer = Color(0xFF4A0F0A),
    outline = Color(0xFF8A917D),
    outlineVariant = Color(0xFFD7DCCB),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDE1D3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6EE),
    surfaceContainer = Color(0xFFEEF1E6),
    surfaceContainerHigh = Color(0xFFE8ECDF),
    surfaceContainerHighest = Color(0xFFE2E7D8)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C98E),
    onPrimary = Color(0xFF243013),
    primaryContainer = BrandPalette.Olive800,
    onPrimaryContainer = BrandPalette.Olive100,
    inversePrimary = BrandPalette.Olive600,
    secondary = Color(0xFFC3CBAC),
    onSecondary = Color(0xFF2D3420),
    secondaryContainer = Color(0xFF434C34),
    onSecondaryContainer = Color(0xFFE2E8D2),
    tertiary = Color(0xFFD8CB96),
    onTertiary = Color(0xFF3A3110),
    tertiaryContainer = Color(0xFF524824),
    onTertiaryContainer = Color(0xFFF2EACB),
    background = BrandPalette.Ink,
    onBackground = Color(0xFFE5E9DC),
    surface = BrandPalette.Ink,
    onSurface = Color(0xFFE5E9DC),
    surfaceVariant = Color(0xFF2C3225),
    onSurfaceVariant = Color(0xFFB3BAA5),
    surfaceTint = Color(0xFFB7C98E),
    inverseSurface = Color(0xFFE5E9DC),
    inverseOnSurface = Color(0xFF1C2016),
    error = Color(0xFFF0958A),
    onError = Color(0xFF4A0F0A),
    errorContainer = Color(0xFF7A2A22),
    onErrorContainer = Color(0xFFF8E0DB),
    outline = Color(0xFF7D8572),
    outlineVariant = Color(0xFF30362A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2E3428),
    surfaceDim = BrandPalette.Ink,
    surfaceContainerLowest = Color(0xFF0B0D09),
    surfaceContainerLow = Color(0xFF171B13),
    surfaceContainer = Color(0xFF1B2017),
    surfaceContainerHigh = Color(0xFF22281D),
    surfaceContainerHighest = Color(0xFF293023)
)
