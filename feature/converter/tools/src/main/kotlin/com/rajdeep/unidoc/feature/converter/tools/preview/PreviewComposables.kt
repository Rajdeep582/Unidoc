package com.rajdeep.unidoc.feature.converter.tools.preview

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.components.shimmer

/** Provided by the tool screen so deep composables can render previews without threading it through. */
val LocalPdfPreviews = staticCompositionLocalOf<PdfPreviewRenderer?> { null }

@Composable
fun ProvidePdfPreviews(renderer: PdfPreviewRenderer, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalPdfPreviews provides renderer, content = content)

private sealed interface Thumb {
    data object Loading : Thumb

    data object Failed : Thumb

    data class Ready(val bitmap: ImageBitmap) : Thumb
}

/** One PDF page, rendered to the composable's width. Shimmers while loading; lock icon if unreadable. */
@Composable
fun PdfPage(uri: String, page: Int, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val renderer = LocalPdfPreviews.current
    BoxWithConstraints(modifier.background(Color.White)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt().coerceAtLeast(64)
        // Bucket widths so small layout changes reuse cached bitmaps.
        val bucket = ((widthPx + 63) / 64) * 64
        val state by produceState<Thumb>(Thumb.Loading, uri, page, bucket) {
            value = renderer?.page(uri, page, bucket)?.let { Thumb.Ready(it) } ?: Thumb.Failed
        }
        Crossfade(state, label = "pdfPage") { s ->
            when (s) {
                Thumb.Loading -> Box(Modifier.fillMaxSize().shimmer())
                Thumb.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                }
                is Thumb.Ready -> Image(s.bitmap, null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
