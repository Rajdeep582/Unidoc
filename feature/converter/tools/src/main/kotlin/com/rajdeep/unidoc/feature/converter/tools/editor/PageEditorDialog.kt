package com.rajdeep.unidoc.feature.converter.tools.editor

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.IconButtonStyle
import com.rajdeep.unidoc.core.ui.components.IconButtonSurface
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.components.UnidocTopBar
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.converter.tools.preview.PdfInfo
import com.rajdeep.unidoc.feature.converter.tools.preview.PdfPage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** Page thumbnail rotated by [rotation] degrees, scaled so a sideways page still fits its cell. */
@Composable
fun RotatedPdfPage(uri: String, page: Int, aspect: Float, rotation: Int, modifier: Modifier = Modifier) {
    val angle by animateFloatAsState(rotation.toFloat(), UnidocMotion.bouncy(), label = "pageRotation")
    val sideways = rotation % 180 != 0
    val scale by animateFloatAsState(if (sideways) minOf(aspect, 1f / aspect) else 1f, label = "pageScale")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Fit the (unrotated) page inside the cell so labels below never overlap.
        val cellAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else aspect
        val pageW = if (aspect > cellAspect) maxWidth else maxHeight * aspect
        PdfPage(
            uri,
            page,
            Modifier
                .size(pageW, pageW / aspect)
                .graphicsLayer {
                    rotationZ = angle
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(2.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
        )
    }
}

/** Drag-to-reorder, rotate and delete pages (Organize PDF). Returns the new page list. */
@Composable
fun ArrangePagesDialog(uri: String, info: PdfInfo, initial: List<PageItem>, onDone: (List<PageItem>) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val colors = UnidocTheme.colors
    var items by remember { mutableStateOf(initial) }
    val original = remember { (0 until info.pageCount).map { PageItem(it) } }
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.snap()
    }
    FullScreenDialog(onDismiss) {
        UnidocTopBar(
            title = "Arrange pages",
            subtitle = "${items.size} of ${info.pageCount} pages · long-press to drag",
            onBack = onDismiss,
            actions = {
                IconButtonSurface(
                    Icons.AutoMirrored.Rounded.Undo,
                    "Reset",
                    { items = original },
                    style = IconButtonStyle.Ghost,
                    enabled = items != original
                )
            }
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            state = gridState,
            contentPadding = PaddingValues(Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(items, key = { _, it -> it.source }) { index, item ->
                ReorderableItem(reorderState, key = item.source) { dragging ->
                    val elevation by animateDpAsState(if (dragging) 16.dp else 0.dp, label = "dragElevation")
                    UnidocCard(
                        Modifier
                            .shadow(elevation, RoundedCornerShape(22.dp))
                            .longPressDraggableHandle(onDragStarted = { haptics.longPress() })
                    ) {
                        Column(Modifier.padding(Dimens.SpaceS), horizontalAlignment = Alignment.CenterHorizontally) {
                            RotatedPdfPage(
                                uri,
                                item.source,
                                info.aspect(item.source),
                                item.rotation,
                                Modifier.fillMaxWidth().aspectRatio(0.75f)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = Dimens.SpaceXs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButtonSurface(
                                    Icons.Rounded.RotateRight,
                                    "Rotate page ${index + 1}",
                                    {
                                        haptics.tick()
                                        items =
                                            items.map { if (it.source == item.source) it.copy(rotation = (it.rotation + 90) % 360) else it }
                                    },
                                    style = IconButtonStyle.Ghost,
                                    size = 32.dp
                                )
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                IconButtonSurface(
                                    Icons.Rounded.Close,
                                    "Delete page ${index + 1}",
                                    {
                                        haptics.tick()
                                        items = items.filterNot { it.source == item.source }
                                    },
                                    style = IconButtonStyle.Ghost,
                                    size = 32.dp,
                                    enabled = items.size > 1
                                )
                            }
                        }
                        if (item.source != index) {
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.brandBrush)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) { Text("was ${item.source + 1}", style = MaterialTheme.typography.labelSmall, color = Color.White) }
                        }
                    }
                }
            }
        }
        BottomBar {
            GradientButton("Apply", { onDone(items) }, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Tap to select pages (Remove / Extract / Rotate / Split). Returns sorted 0-based indices. */
@Composable
fun SelectPagesDialog(
    uri: String,
    info: PdfInfo,
    title: String,
    initial: Set<Int>,
    confirmLabel: String,
    onDone: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    previewRotation: Int = 0
) {
    val haptics = rememberHaptics()
    val colors = UnidocTheme.colors
    var selected by remember { mutableStateOf(initial) }
    val all = remember { (0 until info.pageCount).toSet() }
    FullScreenDialog(onDismiss) {
        UnidocTopBar(title = title, subtitle = "${selected.size} of ${info.pageCount} selected", onBack = onDismiss)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            val presets = listOf(
                "All" to all,
                "None" to emptySet(),
                "Odd" to all.filter { it % 2 == 0 }.toSet(),
                "Even" to all.filter { it % 2 == 1 }.toSet(),
                "Invert" to null
            )
            items(presets) { (label, set) ->
                UnidocChip(label, selected = set != null && set == selected && set.isNotEmpty(), onClick = {
                    haptics.tick()
                    selected = set ?: (all - selected)
                })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            modifier = Modifier.weight(1f)
        ) {
            items(info.pageCount, key = { it }) { page ->
                val isSelected = page in selected
                val dim by animateFloatAsState(if (isSelected || selected.isEmpty()) 1f else 0.55f, label = "dim")
                UnidocCard(
                    onClick = {
                        haptics.tick()
                        selected = if (isSelected) selected - page else selected + page
                    },
                    selected = isSelected,
                    modifier = Modifier.graphicsLayer { alpha = dim }
                ) {
                    Column(Modifier.padding(Dimens.SpaceS), horizontalAlignment = Alignment.CenterHorizontally) {
                        RotatedPdfPage(
                            uri,
                            page,
                            info.aspect(page),
                            if (isSelected) previewRotation else 0,
                            Modifier.fillMaxWidth().aspectRatio(0.75f)
                        )
                        Text("${page + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Dimens.SpaceXs))
                    }
                    if (isSelected) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(colors.brandBrush),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
        BottomBar {
            GradientButton(confirmLabel, { onDone(selected) }, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
            content = content
        )
    }
}

@Composable
internal fun BottomBar(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .navigationBarsPadding()
            .padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceM)
    ) { content() }
}
