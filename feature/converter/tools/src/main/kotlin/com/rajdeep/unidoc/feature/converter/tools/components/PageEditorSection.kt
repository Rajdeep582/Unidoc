package com.rajdeep.unidoc.feature.converter.tools.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.ui.components.IconBadge
import com.rajdeep.unidoc.core.ui.components.SignaturePadSheet
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.components.drawSignature
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.feature.converter.tools.KEY_PLACE
import com.rajdeep.unidoc.feature.converter.tools.PageEditorKind
import com.rajdeep.unidoc.feature.converter.tools.ToolAction
import com.rajdeep.unidoc.feature.converter.tools.ToolUiState
import com.rajdeep.unidoc.feature.converter.tools.editor.ArrangePagesDialog
import com.rajdeep.unidoc.feature.converter.tools.editor.PageItem
import com.rajdeep.unidoc.feature.converter.tools.editor.PageSpec
import com.rajdeep.unidoc.feature.converter.tools.editor.PlacementDialog
import com.rajdeep.unidoc.feature.converter.tools.editor.RotatedPdfPage
import com.rajdeep.unidoc.feature.converter.tools.editor.SelectPagesDialog
import com.rajdeep.unidoc.feature.converter.tools.editor.Stamp

/** What the result will look like, page by page: kept pages in order with rotation, removed pages faded. */
internal fun resultPages(state: ToolUiState): List<Pair<PageItem, Boolean>> {
    val info = state.pdfInfo ?: return emptyList()
    val n = info.pageCount
    val opts = state.options
    return when (state.tool) {
        ToolId.ORGANIZE_PDF -> PageSpec.items(opts["order"], opts["rotate"], n).map { it to true }
        ToolId.REMOVE_PAGES -> {
            val drop = PageSpec.parse(opts["pages"], n).toSet()
            (0 until n).map { PageItem(it) to (it !in drop) }
        }
        ToolId.EXTRACT_PAGES -> {
            val keep = PageSpec.parse(opts["pages"], n)
            if (keep.isEmpty()) (0 until n).map { PageItem(it) to false } else keep.map { PageItem(it) to true }
        }
        ToolId.ROTATE_PDF -> {
            val angle = opts["angle"]?.toIntOrNull() ?: 90
            val pages = PageSpec.parse(opts["pages"], n).toSet().ifEmpty { (0 until n).toSet() }
            (0 until n).map { PageItem(it, if (it in pages) angle else 0) to true }
        }
        else -> (0 until n).map { PageItem(it) to true }
    }
}

@Composable
fun PageEditorSection(state: ToolUiState, onAction: (ToolAction) -> Unit) {
    val kind = state.pageEditor ?: return
    val file = state.editablePdf ?: return
    val info = state.pdfInfo ?: return
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var drawSignature by remember { mutableStateOf(false) }
    val pages = resultPages(state)

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM), modifier = Modifier.animateContentSize()) {
        when (kind) {
            PageEditorKind.ARRANGE -> EditorCard(
                icon = Icons.Rounded.GridView,
                title = "Arrange pages",
                subtitle = PageSpec.encode(PageSpec.items(state.options["order"], state.options["rotate"], info.pageCount), info.pageCount)
                    .let { (o, r) ->
                        if (o.isEmpty() &&
                            r.isEmpty()
                        ) {
                            "Drag to reorder · rotate · delete"
                        } else {
                            "${pages.size} ${if (pages.size == 1) "page" else "pages"} · edited"
                        }
                    },
                onClick = { dialog = "arrange" }
            )
            PageEditorKind.SELECT -> EditorCard(
                icon = Icons.Rounded.TouchApp,
                title = when (state.tool) {
                    ToolId.REMOVE_PAGES -> "Pick pages to remove"
                    ToolId.EXTRACT_PAGES -> "Pick pages to extract"
                    else -> "Pick pages to rotate"
                },
                subtitle = state.options["pages"].orEmpty().ifBlank {
                    if (state.tool ==
                        ToolId.ROTATE_PDF
                    ) {
                        "All pages"
                    } else {
                        "Tap pages visually"
                    }
                },
                onClick = { dialog = "select" }
            )
            PageEditorKind.PLACE -> {
                if (state.tool == ToolId.SIGN_PDF) {
                    SignatureCard(state, onDraw = { drawSignature = true })
                }
                val canPlace = when (state.tool) {
                    ToolId.SIGN_PDF -> state.signature != null
                    else -> !state.options["text"].isNullOrBlank()
                }
                val place = state.placement
                EditorCard(
                    icon = Icons.Rounded.PanTool,
                    title = if (place == null) "Place on page" else "Placed on page ${place.page + 1}",
                    subtitle = when {
                        !canPlace && state.tool == ToolId.SIGN_PDF -> "Draw your signature first"
                        !canPlace -> "Enter the text first"
                        place == null -> "Drag it exactly where it goes (optional)"
                        else -> "Tap to adjust"
                    },
                    onClick = if (canPlace) ({ dialog = "place" }) else null,
                    trailing = if (place != null) {
                        { TextButton(onClick = { onAction(ToolAction.OptionsChanged(mapOf(KEY_PLACE to ""))) }) { Text("Reset") } }
                    } else {
                        null
                    }
                )
            }
            PageEditorKind.PREVIEW -> Unit
        }
        if (pages.isNotEmpty()) PagesStrip(file.uri, info, pages)
    }

    when (dialog) {
        "arrange" -> ArrangePagesDialog(
            uri = file.uri,
            info = info,
            initial = PageSpec.items(state.options["order"], state.options["rotate"], info.pageCount),
            onDone = { items ->
                val (order, rotate) = PageSpec.encode(items, info.pageCount)
                onAction(ToolAction.OptionsChanged(mapOf("order" to order, "rotate" to rotate)))
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        "select" -> SelectPagesDialog(
            uri = file.uri,
            info = info,
            title = when (state.tool) {
                ToolId.REMOVE_PAGES -> "Remove pages"
                ToolId.EXTRACT_PAGES -> "Extract pages"
                else -> "Rotate pages"
            },
            initial = PageSpec.parse(state.options["pages"], info.pageCount).toSet(),
            confirmLabel = "Done",
            previewRotation = if (state.tool == ToolId.ROTATE_PDF) state.options["angle"]?.toIntOrNull() ?: 90 else 0,
            onDone = { set ->
                onAction(ToolAction.OptionsChanged(mapOf("pages" to PageSpec.format(set.sorted()))))
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        "place" -> {
            val stamp = if (state.tool == ToolId.SIGN_PDF) {
                state.signature?.let { Stamp.Signature(it.strokes, it.aspect) }
            } else {
                Stamp.Label(state.options["text"].orEmpty(), state.options["size"]?.toFloatOrNull() ?: 14f)
            }
            if (stamp == null) {
                dialog = null
            } else {
                PlacementDialog(
                    uri = file.uri,
                    info = info,
                    stamp = stamp,
                    initial = state.placement,
                    onDone = {
                        onAction(ToolAction.OptionsChanged(mapOf(KEY_PLACE to it.encode())))
                        dialog = null
                    },
                    onDismiss = { dialog = null }
                )
            }
        }
    }
    if (drawSignature) {
        SignaturePadSheet(
            onUse = { strokes, aspect ->
                onAction(ToolAction.SaveSignature(strokes, aspect))
                drawSignature = false
            },
            onDismiss = { drawSignature = false }
        )
    }
}

@Composable
private fun EditorCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null
) {
    UnidocCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
        ) {
            IconBadge(icon, size = 40.dp, solid = onClick != null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SignatureCard(state: ToolUiState, onDraw: () -> Unit) {
    val sig = state.signature
    UnidocCard(Modifier.fillMaxWidth(), onClick = onDraw) {
        Row(
            Modifier.padding(Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
        ) {
            if (sig == null) {
                IconBadge(Icons.Rounded.Draw, size = 40.dp, solid = true)
                Column(Modifier.weight(1f)) {
                    Text("Draw your signature", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Saved on this phone, reused everywhere",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    Modifier
                        .width(120.dp)
                        .aspectRatio(sig.aspect)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                ) {
                    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                        drawSignature(sig.strokes, Rect(0f, 0f, size.width, size.height), Color(0xFF1E2A12))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDraw) {
                    androidx.compose.material3.Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Redraw")
                }
            }
        }
    }
}

/** Horizontal strip of result pages; removed pages are faded and struck through. */
@Composable
private fun PagesStrip(
    uri: String,
    info: com.rajdeep.unidoc.feature.converter.tools.preview.PdfInfo,
    pages: List<Pair<PageItem, Boolean>>
) {
    val outline = MaterialTheme.colorScheme.error
    Column {
        Text(
            "Preview · ${pages.count { it.second }.let { n -> "$n ${if (n == 1) "page" else "pages"}" }}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.SpaceS))
        LazyRow(
            contentPadding = PaddingValues(end = Dimens.SpaceL),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            itemsIndexed(pages, key = { i, p -> "${p.first.source}-$i" }) { _, (item, kept) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.animateItem()) {
                    RotatedPdfPage(
                        uri,
                        item.source,
                        info.aspect(item.source),
                        item.rotation,
                        Modifier
                            .width(64.dp)
                            .height(84.dp)
                            .drawWithContent {
                                drawContent()
                                if (!kept) {
                                    drawRect(Color.White.copy(alpha = 0.6f))
                                    drawLine(
                                        outline,
                                        androidx.compose.ui.geometry.Offset(0f, size.height),
                                        androidx.compose.ui.geometry.Offset(size.width, 0f),
                                        3f
                                    )
                                }
                            }
                    )
                    Text(
                        "${item.source + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (kept) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
