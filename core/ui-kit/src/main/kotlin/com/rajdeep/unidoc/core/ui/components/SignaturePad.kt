package com.rajdeep.unidoc.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.theme.Dimens

private const val PAD_ASPECT = 3f

/** Draws normalized signature strokes (x,y pairs in 0..1) smoothed into [box]. */
fun DrawScope.drawSignature(strokes: List<List<Float>>, box: Rect, color: Color, widthFraction: Float = 0.012f) {
    val strokeWidth = (box.width * widthFraction).coerceAtLeast(1.2f)
    strokes.forEach { pts ->
        if (pts.size < 4) return@forEach
        val path = Path()
        fun pt(i: Int) = Offset(box.left + pts[i] * box.width, box.top + pts[i + 1] * box.height)
        path.moveTo(pt(0).x, pt(0).y)
        var i = 2
        while (i + 1 < pts.size) {
            val prev = pt(i - 2)
            val cur = pt(i)
            // Quadratic through midpoints for smooth curves.
            path.quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
            i += 2
        }
        val last = pt(pts.size - 2)
        path.lineTo(last.x, last.y)
        drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Draw-your-signature sheet. Returns strokes normalized to a 3:1 box and that aspect. */
@Composable
fun SignaturePadSheet(onUse: (strokes: List<List<Float>>, aspect: Float) -> Unit, onDismiss: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Float>>() }
    val current = remember { mutableStateListOf<Float>() }
    UnidocBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = Dimens.Gutter).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
        ) {
            Text("Your signature", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Sign with your finger. It's saved on this phone only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PAD_ASPECT)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { p ->
                                current.clear()
                                current.addAll(listOf(p.x / size.width, p.y / size.height))
                            },
                            onDragEnd = {
                                if (current.size >= 4) strokes += current.toList()
                                current.clear()
                            }
                        ) { change, _ ->
                            change.consume()
                            current.addAll(
                                listOf(
                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                    (change.position.y / size.height).coerceIn(0f, 1f)
                                )
                            )
                        }
                    }
            ) {
                val all = strokes.toList() + listOfNotNull(current.toList().takeIf { it.size >= 4 })
                drawSignature(all, Rect(0f, 0f, size.width, size.height), Color(0xFF12131C), widthFraction = 0.008f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                TextButton(onClick = { strokes.clear() }) { Text("Clear") }
                GradientButton(
                    "Use signature",
                    { onUse(strokes.toList(), PAD_ASPECT) },
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
