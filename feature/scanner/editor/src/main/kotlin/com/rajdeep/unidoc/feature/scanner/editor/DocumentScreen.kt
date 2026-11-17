package com.rajdeep.unidoc.feature.scanner.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.model.ScanDocument
import com.rajdeep.unidoc.core.ui.components.DocumentThumbnail
import com.rajdeep.unidoc.core.ui.components.IconButtonStyle
import com.rajdeep.unidoc.core.ui.components.IconButtonSurface
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.components.UnidocTopBar
import com.rajdeep.unidoc.core.ui.format.formatFileSize
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

data class DocumentCallbacks(
    val onBack: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onText: () -> Unit = {},
    val onRename: () -> Unit = {},
    val onMore: () -> Unit = {}
)

/** Read view of a saved document: continuous pages + floating action dock. */
@Composable
fun DocumentScreen(
    document: ScanDocument,
    callbacks: DocumentCallbacks,
    modifier: Modifier = Modifier,
    pageContent: @Composable (Int) -> Unit = { index ->
        DocumentThumbnail(seed = document.id.hashCode() * 10L + index, modifier = Modifier.fillMaxWidth())
    }
) {
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 120.dp, bottom = 140.dp, start = 32.dp, end = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
                ) {
                    UnidocChip("${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}", false, {})
                    UnidocChip(formatFileSize(document.sizeBytes), false, {})
                    if (document.hasOcrText) UnidocChip("Searchable", true, {}, leadingIcon = Icons.AutoMirrored.Rounded.TextSnippet)
                }
            }
            items((0 until document.pageCount).toList()) { index ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    pageContent(index)
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.SpaceS)
                    )
                }
            }
        }
        UnidocTopBar(
            title = document.title,
            onBack = callbacks.onBack,
            scrolled = scrolled,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            IconButtonSurface(
                Icons.Rounded.DriveFileRenameOutline,
                "Rename",
                callbacks.onRename,
                style = IconButtonStyle.Ghost
            )
            IconButtonSurface(Icons.Rounded.MoreVert, "More options", callbacks.onMore, style = IconButtonStyle.Ghost)
        }
        ActionDock(
            callbacks,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Dimens.NavBarHorizontalMargin, vertical = Dimens.SpaceL)
        )
    }
}

@Composable
private fun ActionDock(callbacks: DocumentCallbacks, modifier: Modifier) {
    val colors = UnidocTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(20.dp, shape, spotColor = colors.gradientMid.copy(alpha = 0.5f))
            .clip(shape)
            .background(colors.brandBrush)
            .padding(horizontal = Dimens.SpaceS),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockAction(Icons.Rounded.IosShare, "Share", callbacks.onShare)
        DockAction(Icons.Rounded.PictureAsPdf, "Export", callbacks.onExport)
        DockAction(Icons.Rounded.Edit, "Edit", callbacks.onEdit)
        DockAction(Icons.AutoMirrored.Rounded.TextSnippet, "Text", callbacks.onText)
    }
}

@Composable
private fun DockAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}
