package com.rajdeep.unidoc.feature.converter.tools.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rajdeep.unidoc.core.model.ToolOutput
import com.rajdeep.unidoc.feature.converter.tools.preview.LocalPdfPreviews
import com.rajdeep.unidoc.feature.converter.tools.preview.PdfPage

fun canPreviewOutput(out: ToolOutput): Boolean = out.mimeType == "application/pdf" || out.mimeType.startsWith("image/")

/** First page (PDF) or the image itself, as a floating paper card. */
@Composable
fun OutputPreview(out: ToolOutput, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    if (out.mimeType == "application/pdf") {
        val renderer = LocalPdfPreviews.current
        val aspect by produceState(0.7071f, out.uri) {
            renderer?.info(out.uri)?.let { value = it.aspect(0) }
        }
        PdfPage(
            out.uri,
            0,
            modifier
                .height(220.dp)
                .aspectRatio(aspect.coerceIn(0.4f, 2f))
                .shadow(12.dp, shape)
                .clip(shape)
        )
    } else {
        AsyncImage(
            model = out.uri,
            contentDescription = out.name,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .height(220.dp)
                .widthIn(max = 320.dp)
                .shadow(12.dp, shape)
                .clip(shape)
        )
    }
}
