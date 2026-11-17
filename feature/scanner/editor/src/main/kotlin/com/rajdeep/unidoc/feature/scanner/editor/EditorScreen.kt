package com.rajdeep.unidoc.feature.scanner.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.ui.components.DocumentThumbnail
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.IconButtonStyle
import com.rajdeep.unidoc.core.ui.components.IconButtonSurface
import com.rajdeep.unidoc.core.ui.components.UnidocTopBar
import com.rajdeep.unidoc.core.ui.components.gradientTint
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.scanner.editor.components.AdjustPanel
import com.rajdeep.unidoc.feature.scanner.editor.components.AnnotatePanel
import com.rajdeep.unidoc.feature.scanner.editor.components.FilterStrip
import com.rajdeep.unidoc.feature.scanner.editor.components.RotatePanel
import com.rajdeep.unidoc.feature.scanner.editor.components.ToolRailItem
import com.rajdeep.unidoc.feature.scanner.editor.components.filterLook

private val Rail = linkedMapOf(
    EditorTool.CROP to ToolRailItem("Adjust", Icons.Rounded.Crop),
    EditorTool.FILTERS to ToolRailItem("Filters", Icons.Rounded.AutoAwesome),
    EditorTool.ADJUST to ToolRailItem("Tune", Icons.Rounded.Tune),
    EditorTool.ROTATE to ToolRailItem("Rotate", Icons.Rounded.Rotate90DegreesCcw),
    EditorTool.ANNOTATE to ToolRailItem("Markup", Icons.Rounded.Draw),
    EditorTool.PAGES to ToolRailItem("Pages", Icons.Rounded.ViewModule),
    EditorTool.TEXT to ToolRailItem("Text", Icons.AutoMirrored.Rounded.TextSnippet)
)

/**
 * Page review/editor. [pageContent] renders a page bitmap (Phase 3/4); defaults to an abstract page.
 */
@Composable
fun EditorScreen(
    state: EditorUiState,
    onAction: (EditorAction) -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (EditorPage) -> Unit = { page ->
        DocumentThumbnail(seed = page.id, modifier = Modifier.fillMaxWidth().filterLook(page.filter))
    }
) {
    val pager = rememberPagerState(initialPage = state.currentPage) { state.pages.size }
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage !=
            state.currentPage
        ) {
            onAction(EditorAction.PageChanged(pager.currentPage))
        }
    }
    LaunchedEffect(state.currentPage) {
        if (pager.currentPage !=
            state.currentPage
        ) {
            pager.animateScrollToPage(state.currentPage)
        }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        UnidocTopBar(
            title = state.title,
            subtitle = "Page ${state.currentPage + 1} of ${state.pages.size}",
            onBack = { onAction(EditorAction.Back) }
        ) {
            IconButtonSurface(Icons.AutoMirrored.Rounded.Undo, "Undo", {
                onAction(EditorAction.Undo)
            }, style = IconButtonStyle.Ghost, enabled = state.canUndo, size = 36.dp)
            IconButtonSurface(Icons.AutoMirrored.Rounded.Redo, "Redo", {
                onAction(EditorAction.Redo)
            }, style = IconButtonStyle.Ghost, enabled = state.canRedo, size = 36.dp)
            GradientButton(
                text = "Export",
                onClick = { onAction(EditorAction.Export) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.padding(end = Dimens.SpaceS).height(44.dp)
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 48.dp),
                pageSpacing = 20.dp,
                userScrollEnabled = state.activeTool != EditorTool.ANNOTATE,
                modifier = Modifier.fillMaxSize()
            ) { index ->
                val page = state.pages[index]
                val focus by animateFloatAsState(
                    if (index ==
                        pager.currentPage
                    ) {
                        1f
                    } else {
                        0.9f
                    },
                    UnidocMotion.spatial(),
                    label = "pageFocus"
                )
                val rotation by animateFloatAsState(
                    page.rotationDegrees.toFloat(),
                    UnidocMotion.spatial(),
                    label = "pageRotation"
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.graphicsLayer {
                            scaleX = focus
                            scaleY = focus
                            alpha = 0.5f + 0.5f * focus
                            rotationZ = rotation
                        }
                    ) { pageContent(page) }
                }
            }
        }
        // Shortcut to fix the detected edges (8-point adjust), below the page so it never covers it.
        androidx.compose.animation.AnimatedVisibility(
            visible = state.page != null && state.activeTool != EditorTool.ANNOTATE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Dimens.SpaceS)
        ) {
            com.rajdeep.unidoc.core.ui.components.UnidocChip(
                "Adjust edges",
                false,
                { onAction(EditorAction.ToolSelected(EditorTool.CROP)) },
                leadingIcon = Icons.Rounded.Crop
            )
        }
        PageDots(state.pages.size, state.currentPage)
        AnimatedContent(
            targetState = state.activeTool,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 3 }) togetherWith (fadeOut() + slideOutVertically { it / 3 }) using
                    SizeTransform(clip = false)
            },
            label = "editorPanel"
        ) { tool ->
            Box(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceS)) {
                val page = state.page
                when (tool) {
                    EditorTool.FILTERS -> if (page != null) {
                        FilterStrip(page.filter, {
                            onAction(EditorAction.FilterSelected(it))
                        }, { onAction(EditorAction.ApplyFilterToAll) })
                    }
                    EditorTool.ADJUST -> AdjustPanel(
                        state.adjustment,
                        state.adjustments,
                        { onAction(EditorAction.AdjustmentSelected(it)) },
                        { onAction(EditorAction.AdjustmentChanged(it)) }
                    )
                    EditorTool.ROTATE -> RotatePanel(
                        state.straighten,
                        { onAction(EditorAction.RotateLeft) },
                        { onAction(EditorAction.RotateRight) },
                        { onAction(EditorAction.FlipHorizontal) },
                        { onAction(EditorAction.StraightenChanged(it)) }
                    )
                    EditorTool.ANNOTATE -> AnnotatePanel(
                        state.annotateTool,
                        state.annotateColorIndex,
                        state.strokeWidth,
                        { onAction(EditorAction.AnnotateToolSelected(it)) },
                        { onAction(EditorAction.AnnotateColorSelected(it)) },
                        { onAction(EditorAction.StrokeWidthChanged(it)) },
                        { onAction(EditorAction.NewSignature) }
                    )
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
        }
        ToolRail(state.activeTool) { tool ->
            onAction(
                EditorAction.ToolSelected(
                    if (tool ==
                        state.activeTool
                    ) {
                        null
                    } else {
                        tool
                    }
                )
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val colors = UnidocTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceS),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        repeat(count) { i ->
            val width by animateFloatAsState(if (i == current) 22f else 6f, UnidocMotion.spatial(), label = "dotWidth")
            Box(
                Modifier
                    .height(6.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .then(
                        if (i ==
                            current
                        ) {
                            Modifier.background(colors.brandHorizontalBrush)
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.outlineVariant)
                        }
                    )
            )
        }
    }
}

@Composable
private fun ToolRail(active: EditorTool?, onSelect: (EditorTool) -> Unit) {
    val haptics = rememberHaptics()
    val colors = UnidocTheme.colors
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow)
            .navigationBarsPadding()
            .padding(horizontal = Dimens.SpaceXs, vertical = Dimens.SpaceS),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Rail.forEach { (tool, item) ->
            val selected = tool == active
            val tint by animateColorAsState(
                if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                label = "railTint"
            )
            // Equal-width slots keep the seven tools evenly spaced on any screen and font size.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab) {
                        haptics.tick()
                        onSelect(tool)
                    }
                    .semantics { this.selected = selected }
                    .padding(horizontal = 2.dp, vertical = 6.dp)
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 32.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.background(colors.glow.copy(alpha = 0.35f)) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon,
                        null,
                        tint = tint,
                        modifier = Modifier.size(
                            22.dp
                        ).then(if (selected) Modifier.gradientTint(colors.brandBrush) else Modifier)
                    )
                }
                com.rajdeep.unidoc.core.ui.components.FitText(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    minSize = 8.sp
                )
            }
        }
    }
}
