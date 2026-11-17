package com.rajdeep.unidoc.feature.scanner.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajdeep.unidoc.core.model.LibraryLayout
import com.rajdeep.unidoc.core.model.SortOrder
import com.rajdeep.unidoc.core.ui.components.ConfirmDialog
import com.rajdeep.unidoc.core.ui.components.EmptyState
import com.rajdeep.unidoc.core.ui.components.ErrorState
import com.rajdeep.unidoc.core.ui.components.IconButtonSurface
import com.rajdeep.unidoc.core.ui.components.ScreenHeader
import com.rajdeep.unidoc.core.ui.components.SearchField
import com.rajdeep.unidoc.core.ui.components.SectionHeader
import com.rajdeep.unidoc.core.ui.components.SkeletonBlock
import com.rajdeep.unidoc.core.ui.components.UnidocBottomSheet
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.feature.scanner.home.components.DocumentGridCard
import com.rajdeep.unidoc.feature.scanner.home.components.DocumentListRow
import com.rajdeep.unidoc.feature.scanner.home.components.QuickActions
import com.rajdeep.unidoc.feature.scanner.home.components.SelectionActionBar
import com.rajdeep.unidoc.feature.scanner.home.components.SelectionTopBar

/** Library-level callbacks provided by navigation (capture, import, document detail, settings). */
data class ScannerHomeCallbacks(
    val onScan: () -> Unit = {},
    val onImportPhotos: () -> Unit = {},
    val onImportFiles: () -> Unit = {},
    val onOpenDocument: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onShareSelected: (Set<String>) -> Unit = {},
    val onMergeSelected: (Set<String>) -> Unit = {},
    val onMoveSelected: (Set<String>) -> Unit = {},
    val onDeleteSelected: (Set<String>) -> Unit = {},
    val onFolderLongPress: (com.rajdeep.unidoc.core.model.Folder) -> Unit = {}
)

@Composable
fun ScannerHomeScreen(
    contentPadding: PaddingValues,
    callbacks: ScannerHomeCallbacks,
    modifier: Modifier = Modifier,
    viewModel: ScannerHomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var moveIds by remember { mutableStateOf<Set<String>?>(null) }
    var folderMenu by remember { mutableStateOf<com.rajdeep.unidoc.core.model.Folder?>(null) }
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.effects.collect { e ->
            when (e) {
                is HomeEffect.Share -> com.rajdeep.unidoc.core.pdf.ExportFiles.share(context, e.files)
                is HomeEffect.Open -> callbacks.onOpenDocument(e.id)
                is HomeEffect.Message -> android.widget.Toast.makeText(context, e.text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    Box(modifier) {
        ScannerHomeContent(
            state = state,
            onAction = viewModel::onAction,
            callbacks = callbacks.copy(
                onShareSelected = viewModel::share,
                onMergeSelected = viewModel::merge,
                onMoveSelected = { moveIds = it },
                onDeleteSelected = viewModel::delete,
                onFolderLongPress = { folderMenu = it }
            ),
            contentPadding = contentPadding
        )
        com.rajdeep.unidoc.feature.scanner.home.components.BusyOverlay(busy)
    }
    moveIds?.let { ids ->
        com.rajdeep.unidoc.feature.scanner.home.components.MoveToFolderSheet(
            folders = state.folders,
            onMove = { folder ->
                viewModel.move(ids, folder)
                moveIds = null
            },
            onNewFolder = { name ->
                viewModel.moveToNewFolder(ids, name)
                moveIds = null
            },
            onDismiss = { moveIds = null }
        )
    }
    folderMenu?.let { folder ->
        com.rajdeep.unidoc.feature.scanner.home.components.FolderOptionsSheet(
            folder = folder,
            onRename = { viewModel.renameFolder(folder.id, it) },
            onDelete = { viewModel.deleteFolder(folder.id) },
            onDismiss = { folderMenu = null }
        )
    }
}

@Composable
fun ScannerHomeContent(
    state: ScannerHomeUiState,
    onAction: (ScannerHomeAction) -> Unit,
    callbacks: ScannerHomeCallbacks,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    nowMillis: Long? = null
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val docs = state.visibleDocuments
    val columns = if (state.layout == LibraryLayout.GRID) 2 else 1

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Dimens.SpaceXl),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            modifier = Modifier.fillMaxSize()
        ) {
            fullWidth("header") {
                AnimatedContent(
                    targetState = state.selectionMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "homeHeader"
                ) { selecting ->
                    if (selecting) {
                        SelectionTopBar(
                            count = state.selectedIds.size,
                            onClose = { onAction(ScannerHomeAction.ClearSelection) },
                            onSelectAll = { onAction(ScannerHomeAction.SelectAll) }
                        )
                    } else {
                        ScreenHeader(
                            title = "Documents",
                            subtitle = "Scan, organize and export",
                            modifier = Modifier.statusBarsPadding()
                        ) {
                            IconButtonSurface(Icons.Rounded.Settings, "Settings", callbacks.onOpenSettings)
                        }
                    }
                }
            }
            fullWidth("search") {
                SearchField(
                    query = state.query,
                    onQueryChange = { onAction(ScannerHomeAction.QueryChanged(it)) },
                    placeholder = "Search documents and text",
                    modifier = Modifier.padding(horizontal = Dimens.Gutter)
                )
            }
            fullWidth("actions") {
                QuickActions(
                    onScan = callbacks.onScan,
                    onImportPhotos = callbacks.onImportPhotos,
                    onImportFiles = callbacks.onImportFiles,
                    modifier = Modifier.padding(top = Dimens.SpaceS)
                )
            }

            when (val status = state.status) {
                LibraryStatus.Loading -> {
                    fullWidth("loadingHeader") { SectionHeader("Recent") }
                    items(4, key = { "skeleton$it" }) {
                        SkeletonBlock(
                            Modifier
                                .padding(horizontal = Dimens.Gutter)
                                .aspectRatio(0.78f),
                            RoundedCornerShape(22.dp)
                        )
                    }
                }
                is LibraryStatus.Error -> fullWidth("error") {
                    ErrorState(
                        title = "Couldn't load your library",
                        body = status.message,
                        onRetry = { onAction(ScannerHomeAction.Retry) },
                        modifier = Modifier.padding(top = Dimens.SpaceXxl)
                    )
                }
                LibraryStatus.Ready -> readyContent(state, docs, columns, onAction, callbacks, nowMillis, onSort = {
                    showSort =
                        true
                })
            }
        }

        AnimatedVisibility(
            visible = state.selectionMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(UnidocMotion.spatial()) { it } + fadeIn(),
            exit = slideOutVertically(UnidocMotion.spatial()) { it } + fadeOut()
        ) {
            SelectionActionBar(
                onShare = { callbacks.onShareSelected(state.selectedIds) },
                onMerge = { callbacks.onMergeSelected(state.selectedIds) },
                onMove = { callbacks.onMoveSelected(state.selectedIds) },
                onDelete = { confirmDelete = true },
                mergeEnabled = state.selectedIds.size >= 2,
                modifier = Modifier
                    .padding(horizontal = Dimens.NavBarHorizontalMargin)
                    .padding(
                        bottom = (
                            contentPadding.calculateBottomPadding() - Dimens.NavBarHeight -
                                Dimens.NavBarBottomMargin
                            ).coerceAtLeast(Dimens.SpaceL)
                    )
            )
        }
    }

    if (showSort) {
        SortSheet(
            current = state.sort,
            onSelect = {
                onAction(ScannerHomeAction.SortChanged(it))
                showSort = false
            },
            onDismiss = { showSort = false }
        )
    }
    if (confirmDelete) {
        val count = state.selectedIds.size
        ConfirmDialog(
            title = "Delete $count ${if (count == 1) "document" else "documents"}?",
            message = "This removes the scans and their pages from Unidoc. Files you already exported stay where you saved them.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                callbacks.onDeleteSelected(state.selectedIds)
                onAction(ScannerHomeAction.ClearSelection)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.readyContent(
    state: ScannerHomeUiState,
    docs: List<com.rajdeep.unidoc.core.model.ScanDocument>,
    columns: Int,
    onAction: (ScannerHomeAction) -> Unit,
    callbacks: ScannerHomeCallbacks,
    nowMillis: Long?,
    onSort: () -> Unit
) {
    if (state.documents.isEmpty()) {
        fullWidth("empty") {
            EmptyState(
                title = "No scans yet",
                body = "Tap Scan to capture your first document. Edges, shadows and perspective are fixed for you.",
                modifier = Modifier.padding(top = Dimens.SpaceXl)
            )
        }
        return
    }
    if (state.folders.isNotEmpty()) {
        fullWidth("folders") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.Gutter),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                modifier = Modifier.padding(top = Dimens.SpaceS)
            ) {
                item {
                    UnidocChip("All", state.selectedFolderId == null, {
                        onAction(ScannerHomeAction.FolderSelected(null))
                    })
                }
                items(state.folders, key = { it.id }) { folder ->
                    UnidocChip(
                        label = "${folder.name}  ${folder.documentCount}",
                        selected = state.selectedFolderId == folder.id,
                        onClick = { onAction(ScannerHomeAction.FolderSelected(folder.id)) },
                        leadingIcon = Icons.Rounded.Folder,
                        onLongClick = { callbacks.onFolderLongPress(folder) }
                    )
                }
            }
        }
    }
    fullWidth("recentHeader") {
        Row(
            Modifier.fillMaxWidth().padding(end = Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(if (state.query.isBlank()) "Recent" else "Results", Modifier.weight(1f))
            IconButtonSurface(
                Icons.AutoMirrored.Rounded.Sort,
                "Sort",
                onSort,
                style = com.rajdeep.unidoc.core.ui.components.IconButtonStyle.Ghost,
                size = 40.dp
            )
            IconButtonSurface(
                if (state.layout == LibraryLayout.GRID) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                if (state.layout == LibraryLayout.GRID) "Show as list" else "Show as grid",
                { onAction(ScannerHomeAction.ToggleLayout) },
                style = com.rajdeep.unidoc.core.ui.components.IconButtonStyle.Ghost,
                size = 40.dp
            )
        }
    }
    if (docs.isEmpty()) {
        fullWidth("noResults") {
            Column(
                Modifier.fillMaxWidth().padding(Dimens.SpaceXxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No matches",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Try another name or words inside the document.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    items(docs, key = { it.id }) { doc ->
        val selected = doc.id in state.selectedIds
        val onClick = {
            if (state.selectionMode) {
                onAction(
                    ScannerHomeAction.ToggleSelection(doc.id)
                )
            } else {
                callbacks.onOpenDocument(doc.id)
            }
        }
        val onLongClick = { onAction(ScannerHomeAction.ToggleSelection(doc.id)) }
        val itemModifier = Modifier
            .animateItem()
            .padding(
                start = if (columns == 1) Dimens.Gutter else 0.dp,
                end = if (columns == 1) Dimens.Gutter else 0.dp
            )
        if (columns == 2) {
            val index = docs.indexOf(doc)
            DocumentGridCard(
                document = doc,
                selected = selected,
                selectionMode = state.selectionMode,
                onClick = onClick,
                onLongClick = onLongClick,
                nowMillis = nowMillis,
                modifier = itemModifier.padding(
                    start = if (index % 2 == 0) Dimens.Gutter else 0.dp,
                    end = if (index % 2 == 1) Dimens.Gutter else 0.dp
                )
            )
        } else {
            DocumentListRow(doc, selected, state.selectionMode, onClick, onLongClick, itemModifier, nowMillis)
        }
    }
}

@Composable
private fun SortSheet(current: SortOrder, onSelect: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    val labels = mapOf(
        SortOrder.RECENT to "Recently updated",
        SortOrder.OLDEST to "Oldest first",
        SortOrder.NAME_ASC to "Name A–Z",
        SortOrder.NAME_DESC to "Name Z–A",
        SortOrder.SIZE_DESC to "Largest first"
    )
    UnidocBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Sort by",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceS)
        )
        SortOrder.entries.forEach { order ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onSelect(order) }
                    .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = order == current, onClick = { onSelect(order) })
                Text(labels.getValue(order), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(Dimens.SpaceXxl))
    }
}
