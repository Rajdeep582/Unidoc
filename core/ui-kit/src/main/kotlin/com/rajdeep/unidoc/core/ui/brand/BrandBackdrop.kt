package com.rajdeep.unidoc.core.ui.brand

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rajdeep.unidoc.core.ui.theme.UnidocColors
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One drift phase (0..1) shared by every screen, so the backdrop never jumps between screens. */
val LocalBackdropPhase = staticCompositionLocalOf<State<Float>?> { null }

@Composable
fun rememberBackdropPhase(): State<Float> = rememberInfiniteTransition(label = "backdrop").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(durationMillis = 16_000, easing = LinearEasing), RepeatMode.Restart),
    label = "backdropPhase"
)

/**
 * Calm ambient backdrop: two very soft brand-colored light pools that drift slowly.
 * Drawn in a single Canvas; no recomposition per frame (reads happen in the draw phase).
 */
@Composable
fun BrandBackdrop(modifier: Modifier = Modifier, animated: Boolean = true) {
    val colors = UnidocTheme.colors
    val phase = if (animated) LocalBackdropPhase.current ?: rememberBackdropPhase() else null
    Canvas(modifier.fillMaxSize()) { drawBrandPools(colors, phase?.value ?: 0f) }
}

/**
 * Opaque page surface: theme background + the shared backdrop. Every navigation destination sits on
 * one, so during a transition (including the predictive-back gesture) the outgoing page fully covers
 * the one below instead of the two blending into a ghosted double image.
 */
@Composable
fun ScreenSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = UnidocTheme.colors
    val background = MaterialTheme.colorScheme.background
    val phase = LocalBackdropPhase.current
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(background)
                drawBrandPools(colors, phase?.value ?: 0f)
            }
    ) { content() }
}

fun DrawScope.drawBrandPools(colors: UnidocColors, phase: Float) {
    val strength = if (colors.isDark) 0.20f else 0.11f
    val t = phase * 2f * PI.toFloat()
    val w = size.width
    val h = size.height
    val r1 = w * 0.85f
    val c1 = Offset(w * (0.92f + 0.04f * cos(t)), h * (0.04f + 0.03f * sin(t)))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.gradientMid.copy(alpha = strength), Color.Transparent),
            center = c1,
            radius = r1
        ),
        radius = r1,
        center = c1
    )
    val r2 = w * 0.75f
    val c2 = Offset(w * (0.02f + 0.05f * sin(t)), h * (0.30f + 0.03f * cos(t)))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.gradientEnd.copy(alpha = strength * 0.7f), Color.Transparent),
            center = c2,
            radius = r2
        ),
        radius = r2,
        center = c2
    )
}
