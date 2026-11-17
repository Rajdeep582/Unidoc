package com.rajdeep.unidoc.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Root theme. Material 3 color roles may come from Material You when [dynamicColor] is on (Android 12+),
 * but [UnidocTheme.colors] (brand gradient) is always the Unidoc palette.
 */
@Composable
fun UnidocTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val unidocColors = if (darkTheme) DarkUnidocColors else LightUnidocColors

    CompositionLocalProvider(LocalUnidocColors provides unidocColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UnidocTypography,
            shapes = UnidocShapes
        ) {
            // Screens draw on the background directly (no Surface), so set the default content color here.
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
        }
    }
}

object UnidocTheme {
    val colors: UnidocColors
        @Composable
        @ReadOnlyComposable
        get() = LocalUnidocColors.current
}
