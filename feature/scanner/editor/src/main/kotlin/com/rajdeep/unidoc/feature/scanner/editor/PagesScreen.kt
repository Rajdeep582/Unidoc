package com.rajdeep.unidoc.feature.scanner.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.components.DocumentThumbnail
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.components.UnidocTopBar
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/** Page manager: grid with page numbers, selection, add tile and page actions. */
@Composable
fun PagesScreen(
    pages: List<EditorPage>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onAddPage: () -> Unit,
    onRetake: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = UnidocTheme.colors
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        UnidocTopBar(
            title = "Pages",
            subtitle = if (selectedIds.isEmpty()) "Long-press and drag to reorder" else "${selectedIds.size} selected",
            onBack = onBack
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                val selected = page.id in selectedIds
                UnidocCard(onClick = { onToggle(page.id) }, selected = selected, modifier = Modifier.animateItem()) {
                    Column(Modifier.padding(Dimens.SpaceS), horizontalAlignment = Alignment.CenterHorizontally) {
                        DocumentThumbnail(seed = page.id, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.padding(top = Dimens.SpaceS), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.DragIndicator,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (selected) {
                        Box(
                            Modifier.align(
                                Alignment.TopEnd
                            ).padding(6.dp).size(22.dp).clip(CircleShape).background(colors.brandBrush)
                        )
                    }
                }
            }
            item(key = "add") {
                val outline = MaterialTheme.colorScheme.outline
                UnidocCard(onClick = onAddPage) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.62f)
                            .padding(Dimens.SpaceS)
                            .drawBehind {
                                drawRoundRect(
                                    outline.copy(alpha = 0.6f),
                                    style = Stroke(
                                        1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Add",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        FlowRow(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceM),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            val enabled = selectedIds.isNotEmpty()
            UnidocChip("Retake", false, {
                if (selectedIds.size ==
                    1
                ) {
                    onRetake()
                }
            }, leadingIcon = Icons.Rounded.CameraAlt)
            UnidocChip("Duplicate", false, { if (enabled) onDuplicate() }, leadingIcon = Icons.Rounded.ContentCopy)
            UnidocChip("Delete", false, { if (enabled) onDelete() }, leadingIcon = Icons.Rounded.DeleteOutline)
        }
    }
}
