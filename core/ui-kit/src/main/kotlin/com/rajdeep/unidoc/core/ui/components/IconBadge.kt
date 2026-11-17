package com.rajdeep.unidoc.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/** Paints any content with [brush], keeping its alpha (gradient icons/text). */
fun Modifier.gradientTint(brush: Brush): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
    }

/** Rounded tile holding a gradient-tinted icon over a faint wash of [accent]. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    accent: List<Color> = UnidocTheme.colors.brandColors,
    solid: Boolean = false
) {
    val shape = RoundedCornerShape(size * 0.32f)
    val wash = Brush.linearGradient(accent.map { it.copy(alpha = if (UnidocTheme.colors.isDark) 0.22f else 0.13f) })
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (solid) Brush.linearGradient(accent) else wash),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(size * 0.5f)
                .then(if (solid) Modifier else Modifier.gradientTint(Brush.linearGradient(accent)))
        )
    }
}
