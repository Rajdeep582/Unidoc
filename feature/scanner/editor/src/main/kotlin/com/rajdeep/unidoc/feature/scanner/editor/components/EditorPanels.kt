package com.rajdeep.unidoc.feature.scanner.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.model.ScanFilter
import com.rajdeep.unidoc.core.ui.components.DocumentThumbnail
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.scanner.editor.Adjustment
import com.rajdeep.unidoc.feature.scanner.editor.AnnotateTool
import kotlin.math.roundToInt

val FilterLabels = mapOf(
    ScanFilter.ORIGINAL to "Original",
    ScanFilter.AUTO to "Magic",
    ScanFilter.BLACK_WHITE to "B&W",
    ScanFilter.GRAYSCALE to "Gray",
    ScanFilter.ENHANCED to "Vivid",
    ScanFilter.LIGHTEN to "Lighten",
    ScanFilter.WHITEBOARD to "Board"
)

/** Approximates each filter's look on the abstract thumbnail (real previews are rendered in Phase 3). */
fun Modifier.filterLook(filter: ScanFilter): Modifier = drawWithContent {
    drawContent()
    when (filter) {
        ScanFilter.ORIGINAL -> drawRect(Brush.linearGradient(listOf(Color(0x33C9A46A), Color(0x55303040))))
        ScanFilter.AUTO -> Unit
        ScanFilter.BLACK_WHITE -> drawRect(Color(0x10000000))
        ScanFilter.GRAYSCALE -> drawRect(Color(0x26808080))
        ScanFilter.ENHANCED -> drawRect(Brush.linearGradient(listOf(Color(0x1A5A6E36), Color(0x1AAFBE87))))
        ScanFilter.LIGHTEN -> drawRect(Color(0x33FFFFFF))
        ScanFilter.WHITEBOARD -> drawRect(Color(0x14FFFFFF))
    }
}

@Composable
fun FilterStrip(selected: ScanFilter, onSelect: (ScanFilter) -> Unit, onApplyAll: () -> Unit) {
    val colors = UnidocTheme.colors
    val haptics = rememberHaptics()
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
        ) {
            items(ScanFilter.entries) { filter ->
                val isSelected = filter == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) {
                            haptics.tick()
                            onSelect(filter)
                        }
                        .semantics { this.selected = isSelected }
                ) {
                    Box(
                        Modifier
                            .size(width = 64.dp, height = 84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.5.dp, colors.brandBrush, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                }
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DocumentThumbnail(seed = 7, modifier = Modifier.fillMaxWidth().filterLook(filter))
                        if (isSelected) {
                            Box(
                                Modifier.align(
                                    Alignment.TopEnd
                                ).size(18.dp).clip(CircleShape).background(colors.brandBrush),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        FilterLabels.getValue(filter),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceS),
            horizontalArrangement = Arrangement.End
        ) {
            UnidocChip(
                "Apply to all pages",
                selected = false,
                onClick = onApplyAll,
                leadingIcon = Icons.Rounded.AutoFixHigh
            )
        }
    }
}

@Composable
fun AdjustPanel(selected: Adjustment, values: Map<Adjustment, Float>, onSelect: (Adjustment) -> Unit, onValueChange: (Float) -> Unit) {
    val labels = mapOf(
        Adjustment.BRIGHTNESS to "Brightness",
        Adjustment.CONTRAST to "Contrast",
        Adjustment.SATURATION to "Saturation",
        Adjustment.SHARPNESS to "Sharpness"
    )
    val value = values[selected] ?: 0f
    Column(Modifier.padding(horizontal = Dimens.Gutter)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            items(Adjustment.entries) { adj ->
                UnidocChip(labels.getValue(adj), adj == selected, { onSelect(adj) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dimens.SpaceS)) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = -1f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier.weight(1f).semantics { contentDescription = labels.getValue(selected) }
            )
            Text(
                "${if (value > 0) "+" else ""}${(value * 100).roundToInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(48.dp).padding(start = Dimens.SpaceS)
            )
        }
    }
}

@Composable
fun RotatePanel(straighten: Float, onRotateLeft: () -> Unit, onRotateRight: () -> Unit, onFlip: () -> Unit, onStraighten: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = Dimens.Gutter)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            UnidocChip("Left", false, onRotateLeft, leadingIcon = Icons.AutoMirrored.Rounded.RotateLeft)
            UnidocChip("Right", false, onRotateRight, leadingIcon = Icons.AutoMirrored.Rounded.RotateRight)
            UnidocChip("Flip", false, onFlip, leadingIcon = Icons.Rounded.Flip)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dimens.SpaceS)) {
            Text(
                "Straighten",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = straighten,
                onValueChange = onStraighten,
                valueRange = -45f..45f,
                modifier = Modifier.weight(1f).padding(horizontal = Dimens.SpaceS)
            )
            Text(
                "${straighten.roundToInt()}°",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

val AnnotateColors = listOf(
    Color(0xFF15190F),
    Color(0xFF4A5B2C),
    Color(0xFFE53950),
    Color(0xFF12A671),
    Color(0xFFFFC53D),
    Color(0xFFFFFFFF)
)

@Composable
fun AnnotatePanel(
    tool: AnnotateTool,
    colorIndex: Int,
    strokeWidth: Float,
    onTool: (AnnotateTool) -> Unit,
    onColor: (Int) -> Unit,
    onStroke: (Float) -> Unit,
    onNewSignature: () -> Unit = {}
) {
    val tools = listOf(
        Triple(AnnotateTool.PEN, "Pen", Icons.Rounded.Brush),
        Triple(AnnotateTool.HIGHLIGHT, "Highlight", Icons.Rounded.BorderColor),
        Triple(AnnotateTool.TEXT, "Text", Icons.Rounded.TextFields),
        Triple(AnnotateTool.SIGNATURE, "Sign", Icons.Rounded.Draw),
        Triple(AnnotateTool.ERASER, "Erase", Icons.Rounded.CleaningServices),
        Triple(AnnotateTool.WATERMARK, "Watermark", Icons.Rounded.WaterDrop)
    )
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            items(tools) { (t, label, icon) -> UnidocChip(label, t == tool, { onTool(t) }, leadingIcon = icon) }
            if (tool == AnnotateTool.SIGNATURE) {
                item { UnidocChip("New signature", false, onNewSignature, leadingIcon = Icons.Rounded.Draw) }
            }
        }
        Row(
            Modifier.padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnnotateColors.forEachIndexed { i, c -> ColorDot(c, i == colorIndex) { onColor(i) } }
            Slider(
                value = strokeWidth,
                onValueChange = onStroke,
                modifier = Modifier.weight(1f).padding(start = Dimens.SpaceS)
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = UnidocTheme.colors
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, colors.brandBrush, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(if (selected) 5.dp else 3.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

data class ToolRailItem(val label: String, val icon: ImageVector)
