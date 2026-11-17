package com.rajdeep.unidoc.feature.scanner.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.model.ScanDocument
import com.rajdeep.unidoc.core.ui.components.DocumentThumbnail
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.format.formatFileSize
import com.rajdeep.unidoc.core.ui.format.formatRelativeTime
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

private fun ScanDocument.thumbContent(): (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? =
    thumbnailPath?.let { path ->
        {
            coil3.compose.AsyncImage(
                model = java.io.File(path),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.matchParentSize().background(Color.White)
            )
        }
    }

private fun ScanDocument.meta(now: Long?) = "$pageCount ${if (pageCount == 1) "page" else "pages"} · ${formatFileSize(sizeBytes)} · " +
    (now?.let { formatRelativeTime(updatedAtMillis, it) } ?: formatRelativeTime(updatedAtMillis))

@Composable
fun DocumentGridCard(
    document: ScanDocument,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long? = null
) {
    UnidocCard(
        modifier = modifier.semantics { this.selected = selected },
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected
    ) {
        Column(Modifier.padding(Dimens.SpaceM)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                DocumentThumbnail(
                    pageCount = document.pageCount,
                    seed = document.id.hashCode().toLong(),
                    modifier = Modifier.fillMaxWidth(),
                    content = document.thumbContent()
                )
            }
            Row(Modifier.padding(top = Dimens.SpaceM), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (document.hasOcrText) {
                    Icon(
                        Icons.Rounded.TextFields,
                        contentDescription = "Searchable text",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            // Narrow grid cell: size and age on separate lines instead of an ellipsized "402 K…".
            com.rajdeep.unidoc.core.ui.components.FitText(
                "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"} · ${formatFileSize(document.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minSize = 10.sp
            )
            Text(
                nowMillis?.let { formatRelativeTime(document.updatedAtMillis, it) } ?: formatRelativeTime(document.updatedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SelectionCheck(selectionMode, selected, Modifier.align(Alignment.TopEnd).padding(Dimens.SpaceM))
    }
}

@Composable
fun DocumentListRow(
    document: ScanDocument,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long? = null
) {
    UnidocCard(modifier = modifier, onClick = onClick, onLongClick = onLongClick, selected = selected) {
        Row(
            Modifier.fillMaxWidth().padding(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
        ) {
            DocumentThumbnail(
                pageCount = document.pageCount,
                seed = document.id.hashCode().toLong(),
                modifier = Modifier.width(52.dp),
                content = document.thumbContent()
            )
            Column(Modifier.weight(1f)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    document.meta(nowMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SelectionCheck(selectionMode, selected)
        }
    }
}

@Composable
private fun SelectionCheck(visible: Boolean, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = UnidocTheme.colors
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(visible, modifier, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(
                            colors.brandBrush
                        )
                    } else {
                        Modifier.background(scheme.surface.copy(alpha = 0.9f))
                    }
                )
                .semantics { contentDescription = if (selected) "Selected" else "Not selected" },
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
