package com.rajdeep.unidoc.feature.scanner.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.ui.components.FitText
import com.rajdeep.unidoc.core.ui.components.IconBadge
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.motion.pressScale
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/** Hero "Scan" card + two compact import tiles. */
@Composable
fun QuickActions(onScan: () -> Unit, onImportPhotos: () -> Unit, onImportFiles: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .padding(horizontal = Dimens.Gutter),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
    ) {
        ScanHeroCard(onScan, Modifier.weight(1.35f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            ImportTile("Photos", Icons.Rounded.PhotoLibrary, onImportPhotos, Modifier.weight(1f))
            ImportTile("Files", Icons.Rounded.FolderOpen, onImportFiles, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScanHeroCard(onClick: () -> Unit, modifier: Modifier) {
    val colors = UnidocTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .pressScale(interaction, 0.97f)
            .shadow(18.dp, shape, ambientColor = colors.gradientMid, spotColor = colors.gradientMid)
            .clip(shape)
            .background(colors.brandBrush)
            .drawBehind {
                // Faint viewfinder rings for depth.
                val c = Offset(size.width * 0.92f, size.height * 0.1f)
                listOf(0.55f, 0.85f, 1.15f).forEach { r ->
                    drawCircle(
                        Color.White.copy(alpha = 0.07f),
                        radius = size.width * r,
                        center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.confirm()
                onClick()
            }
            .padding(Dimens.SpaceL)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.DocumentScanner, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.weight(1f))
            // End padding keeps text clear of the arrow button.
            FitText("Scan", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(end = 44.dp))
            FitText(
                "Auto edge detection",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                minSize = 9.sp,
                modifier = Modifier.padding(end = 44.dp)
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.85f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                "Start scanning",
                tint = colors.gradientStart,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ImportTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    UnidocCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS + 2.dp)
        ) {
            IconBadge(icon, size = 36.dp)
            Column(Modifier.weight(1f)) {
                FitText(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, minSize = 11.sp)
                FitText(
                    "Import",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minSize = 9.sp
                )
            }
        }
    }
}
