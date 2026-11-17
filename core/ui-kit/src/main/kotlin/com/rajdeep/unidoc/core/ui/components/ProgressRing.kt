package com.rajdeep.unidoc.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/**
 * Gradient progress ring. [progress] in 0..1 animates smoothly; null = indeterminate
 * (rotating arc that breathes in length).
 */
@Composable
fun ProgressRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    strokeWidth: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = UnidocTheme.colors
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val animated by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = UnidocMotion.spatialSlow(),
        label = "ringProgress"
    )
    val transition = rememberInfiniteTransition(label = "ringSpin")
    val rotation by transition.animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "ringRotation"
    )
    val sweep by transition.animateFloat(
        40f,
        260f,
        infiniteRepeatable(tween(900, easing = UnidocMotion.Standard), RepeatMode.Reverse),
        label = "ringSweep"
    )
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                progressBarRangeInfo =
                    if (progress == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(animated, 0f..1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
            val brush = Brush.sweepGradient(colors.brandColors + colors.gradientStart)
            if (progress == null) {
                rotate(rotation) { drawArc(brush, -90f, sweep, false, topLeft, arcSize, style = stroke) }
            } else if (animated > 0f) {
                rotate(-90f) { drawArc(brush, 0f, 360f * animated, false, topLeft, arcSize, style = stroke) }
            }
        }
        content()
    }
}
