package com.rajdeep.unidoc.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.brand.BrandLogo
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/**
 * Friendly empty state: floating brand tile over a soft glow, headline, body and optional action slot.
 * [illustration] defaults to the brand tile; screens may pass their own.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = { BrandLogo(size = 88.dp) },
    action: (@Composable () -> Unit)? = null
) {
    val colors = UnidocTheme.colors
    val transition = rememberInfiniteTransition(label = "emptyFloat")
    val float by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 2_800, easing = UnidocMotion.Standard),
            RepeatMode.Reverse
        ),
        label = "emptyFloatValue"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            Canvas(Modifier.size(180.dp)) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(colors.gradientMid.copy(alpha = if (colors.isDark) 0.35f else 0.20f), Color.Transparent)
                    )
                )
            }
            Box(Modifier.graphicsLayer { translationY = float * 6.dp.toPx() }) { illustration() }
        }
        Spacer(Modifier.height(Dimens.SpaceL))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.SpaceS))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(Dimens.SpaceXl))
            action()
        }
    }
}
