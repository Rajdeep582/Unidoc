package com.rajdeep.unidoc.feature.scanner.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.components.IconButtonStyle
import com.rajdeep.unidoc.core.ui.components.IconButtonSurface
import com.rajdeep.unidoc.core.ui.theme.Dimens

@Composable
fun SelectionTopBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButtonSurface(Icons.Rounded.Close, "Exit selection", onClose, style = IconButtonStyle.Ghost)
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = Dimens.SpaceXs)
        )
        IconButtonSurface(Icons.Rounded.SelectAll, "Select all", onSelectAll, style = IconButtonStyle.Ghost)
    }
}

@Composable
fun SelectionActionBar(
    onShare: () -> Unit,
    onMerge: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    mergeEnabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f))
            .clip(shape)
            .background(scheme.inverseSurface)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), shape)
            .padding(horizontal = Dimens.SpaceS),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarAction(Icons.Rounded.Share, "Share", onShare)
        BarAction(Icons.AutoMirrored.Rounded.MergeType, "Merge", onMerge, enabled = mergeEnabled)
        BarAction(Icons.AutoMirrored.Rounded.DriveFileMove, "Move", onMove)
        BarAction(Icons.Rounded.DeleteOutline, "Delete", onDelete, tint = Color(0xFFFF8A95))
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.inverseOnSurface
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.35f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceS),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
