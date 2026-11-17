package com.rajdeep.unidoc.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import kotlin.random.Random

/**
 * Paper-like thumbnail. Draws stacked sheets for multi-page documents; [content] overlays the
 * real page bitmap once available. Without content, renders tasteful abstract text lines
 * (deterministic per [seed]) so empty/loading states never look broken.
 */
@Composable
fun DocumentThumbnail(
    modifier: Modifier = Modifier,
    pageCount: Int = 1,
    seed: Long = 0,
    aspectRatio: Float = 1f / 1.414f,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    val colors = UnidocTheme.colors
    val sheet = if (colors.isDark) Color(0xFFF4F6EE) else Color.White
    val ink = Color(0xFF232A1A)
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.aspectRatio(aspectRatio)) {
        if (pageCount > 1) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, top = 0.dp)
                    .graphicsLayer {
                        rotationZ = 3.5f
                        translationX = 4.dp.toPx()
                    }
                    .clip(shape)
                    .shadow(2.dp, shape)
                    .padding(0.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) { drawRect(sheet.copy(alpha = 0.75f)) }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .shadow(
                    6.dp,
                    shape,
                    ambientColor = Color.Black.copy(alpha = 0.3f),
                    spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .clip(shape)
        ) {
            if (content != null) {
                content()
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(sheet)
                    val rnd = Random(seed)
                    val pad = size.width * 0.12f
                    val lineH = size.height * 0.022f
                    var y = pad
                    drawRoundRect(
                        brush = colors.brandBrush,
                        topLeft = Offset(pad, y),
                        size = Size(size.width * 0.45f, lineH * 1.8f),
                        cornerRadius = CornerRadius(lineH),
                        alpha = 0.85f
                    )
                    y += lineH * 4.5f
                    while (y < size.height - pad) {
                        val paragraphEnd = rnd.nextInt(2, 5)
                        repeat(paragraphEnd) { i ->
                            if (y >= size.height - pad) return@repeat
                            val w = if (i ==
                                paragraphEnd - 1
                            ) {
                                rnd.nextFloat() * 0.4f + 0.3f
                            } else {
                                rnd.nextFloat() * 0.12f + 0.84f
                            }
                            drawRoundRect(
                                color = ink.copy(alpha = 0.16f),
                                topLeft = Offset(pad, y),
                                size = Size((size.width - pad * 2) * w, lineH),
                                cornerRadius = CornerRadius(lineH / 2)
                            )
                            y += lineH * 2.1f
                        }
                        y += lineH * 1.6f
                    }
                }
            }
        }
    }
}
