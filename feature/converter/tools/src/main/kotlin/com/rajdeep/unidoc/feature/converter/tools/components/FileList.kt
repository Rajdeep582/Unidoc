package com.rajdeep.unidoc.feature.converter.tools.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rajdeep.unidoc.core.model.PickedFile
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.IconBadge
import com.rajdeep.unidoc.core.ui.components.IconButtonStyle
import com.rajdeep.unidoc.core.ui.components.IconButtonSurface
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.format.formatFileSize
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.feature.converter.tools.preview.LocalPdfPreviews
import com.rajdeep.unidoc.feature.converter.tools.preview.PdfPage

/** Dashed drop-zone shown before any file is chosen. */
@Composable
fun FileDropZone(acceptedLabel: String, multiple: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    UnidocCard(modifier.fillMaxWidth(), onClick = onPick) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceS)
                .drawBehind {
                    drawRoundRect(
                        outline.copy(alpha = 0.5f),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                        cornerRadius = CornerRadius(18.dp.toPx())
                    )
                }
                .padding(vertical = Dimens.SpaceXxl, horizontal = Dimens.SpaceL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(Icons.Rounded.UploadFile, size = 56.dp)
            Spacer(Modifier.height(Dimens.SpaceL))
            Text(if (multiple) "Choose files" else "Choose a file", style = MaterialTheme.typography.titleMedium)
            Text(
                acceptedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.SpaceL))
            GradientButton(text = "Browse", onClick = onPick, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
fun FileRows(
    files: List<PickedFile>,
    reorderable: Boolean,
    canAddMore: Boolean,
    onRemove: (PickedFile) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddMore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        files.forEachIndexed { index, file ->
            UnidocCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(Dimens.SpaceM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
                ) {
                    FileThumb(file)
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (file.sizeBytes >= 0) formatFileSize(file.sizeBytes) else "Unknown size",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (reorderable && files.size > 1) {
                        Column {
                            IconButtonSurface(
                                Icons.Rounded.KeyboardArrowUp,
                                "Move up",
                                {
                                    onMove(index, index - 1)
                                },
                                style = IconButtonStyle.Ghost,
                                size = 28.dp,
                                enabled =
                                index > 0
                            )
                            IconButtonSurface(
                                Icons.Rounded.KeyboardArrowDown,
                                "Move down",
                                {
                                    onMove(index, index + 1)
                                },
                                style = IconButtonStyle.Ghost,
                                size = 28.dp,
                                enabled =
                                index < files.lastIndex
                            )
                        }
                    }
                    IconButtonSurface(Icons.Rounded.Close, "Remove ${file.name}", {
                        onRemove(file)
                    }, style = IconButtonStyle.Ghost, size = 36.dp)
                }
            }
        }
        if (canAddMore) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                UnidocChip("Add more", selected = false, onClick = onAddMore, leadingIcon = Icons.Rounded.Add)
            }
        }
    }
}

/** Real first-page / image thumbnail, falling back to a type icon. */
@Composable
private fun FileThumb(file: PickedFile) {
    val shape = RoundedCornerShape(8.dp)
    when {
        file.mimeType == "application/pdf" && LocalPdfPreviews.current != null -> PdfPage(
            file.uri,
            0,
            Modifier.size(width = 40.dp, height = 52.dp).shadow(2.dp, shape).clip(shape),
            contentScale = ContentScale.Crop
        )
        file.mimeType?.startsWith("image/") == true -> AsyncImage(
            model = file.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 40.dp, height = 52.dp).clip(shape)
        )
        else -> IconBadge(
            if (file.mimeType == "application/pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
            size = 40.dp
        )
    }
}
