package com.rajdeep.unidoc.feature.scanner.editor.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rajdeep.unidoc.core.scan.annotation.PageAnnotation
import com.rajdeep.unidoc.core.ui.components.DocumentThumbnail
import com.rajdeep.unidoc.feature.scanner.editor.AnnotateTool
import com.rajdeep.unidoc.feature.scanner.editor.EditorPage
import java.io.File

/**
 * Rendered page sized to its true aspect ratio with the markup layer on top; spinner while
 * (re)processing. [markupTool] non-null makes the markup interactive.
 */
@Composable
fun ScanPageImage(
    page: EditorPage,
    modifier: Modifier = Modifier,
    markupTool: AnnotateTool? = null,
    markupColor: Long = 0xFF12131C,
    markupStroke: Float = 0.35f,
    onAnnotations: (List<PageAnnotation>) -> Unit = {},
    onTextRequest: (Float, Float) -> Unit = { _, _ -> },
    onSignatureRequest: (Float, Float) -> Unit = { _, _ -> }
) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (page.imagePath == null || page.aspect == null) {
            DocumentThumbnail(seed = page.id, modifier = Modifier.fillMaxWidth())
        } else {
            Box(
                Modifier
                    .aspectRatio(page.aspect, matchHeightConstraintsFirst = page.aspect < 0.75f)
                    .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                    .clip(shape)
                    .background(Color.White)
            ) {
                AsyncImage(
                    model = File(page.imagePath),
                    contentDescription = "Page",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
                MarkupLayer(
                    annotations = page.annotations,
                    tool = markupTool,
                    color = markupColor,
                    strokeValue = markupStroke,
                    onChange = onAnnotations,
                    onTextRequest = onTextRequest,
                    onSignatureRequest = onSignatureRequest,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        AnimatedVisibility(page.processing || page.imagePath == null, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.padding(8.dp).size(36.dp)
            )
        }
    }
}
