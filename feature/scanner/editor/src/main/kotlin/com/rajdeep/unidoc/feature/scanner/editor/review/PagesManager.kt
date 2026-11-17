package com.rajdeep.unidoc.feature.scanner.editor.review

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.rajdeep.unidoc.core.scan.ScanRepository
import com.rajdeep.unidoc.core.ui.components.ConfirmDialog
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.components.UnidocTopBar
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.scanner.editor.navigation.PagesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

data class PageTile(val key: String, val thumbnail: String?, val processing: Boolean)

@HiltViewModel
class PagesViewModel @Inject constructor(savedState: SavedStateHandle, private val repository: ScanRepository) : ViewModel() {
    val sessionId: String = savedState.toRoute<PagesRoute>().sessionId

    val pages: StateFlow<List<PageTile>> = repository.observe(sessionId)
        .map { s ->
            s?.pages?.map { p -> PageTile(p.id, p.thumbnail?.let { repository.file(sessionId, it).absolutePath }, p.isRendering) }.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun reorder(keys: List<String>) = viewModelScope.launch { repository.reorder(sessionId, keys) }

    fun duplicate(keys: Set<String>) = viewModelScope.launch { keys.forEach { repository.duplicate(sessionId, it) } }

    fun delete(keys: Set<String>) = viewModelScope.launch { keys.forEach { repository.deletePage(sessionId, it) } }
}

/** Page manager: long-press and drag to reorder; select to duplicate, delete or retake. */
@Composable
fun PagesRouteScreen(
    onBack: () -> Unit,
    onAddFromCamera: (String) -> Unit,
    onAddFromPhotos: (String) -> Unit,
    onRetake: (sessionId: String, pageKey: String) -> Unit,
    viewModel: PagesViewModel = hiltViewModel()
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val colors = UnidocTheme.colors
    val haptics = rememberHaptics()
    var order by remember { mutableStateOf(pages) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(pages) { order = pages }

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.snap()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        UnidocTopBar(
            title = "Pages",
            subtitle = if (selected.isEmpty()) {
                "${order.size} ${if (order.size == 1) "page" else "pages"} · long-press to drag"
            } else {
                "${selected.size} selected"
            },
            onBack = onBack
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(order, key = { _, p -> p.key }) { index, page ->
                ReorderableItem(reorderState, key = page.key) { dragging ->
                    val elevation by animateDpAsState(if (dragging) 16.dp else 0.dp, label = "dragElevation")
                    val isSelected = page.key in selected
                    UnidocCard(
                        onClick = { selected = if (isSelected) selected - page.key else selected + page.key },
                        selected = isSelected,
                        modifier = Modifier
                            .shadow(elevation, RoundedCornerShape(22.dp))
                            .longPressDraggableHandle(
                                onDragStarted = { haptics.longPress() },
                                onDragStopped = { viewModel.reorder(order.map { it.key }) }
                            )
                    ) {
                        Column(Modifier.padding(Dimens.SpaceS), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                            ) {
                                page.thumbnail?.let {
                                    AsyncImage(File(it), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = Dimens.SpaceXs)
                            )
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
            if (selected.isEmpty()) {
                UnidocChip("Camera", false, { onAddFromCamera(viewModel.sessionId) }, leadingIcon = Icons.Rounded.AddAPhoto)
                UnidocChip("Photos", false, { onAddFromPhotos(viewModel.sessionId) }, leadingIcon = Icons.Rounded.PhotoLibrary)
            } else {
                if (selected.size == 1) {
                    UnidocChip("Retake", false, { onRetake(viewModel.sessionId, selected.first()) }, leadingIcon = Icons.Rounded.CameraAlt)
                }
                UnidocChip("Duplicate", false, {
                    viewModel.duplicate(selected)
                    selected = emptySet()
                }, leadingIcon = Icons.Rounded.ContentCopy)
                UnidocChip("Delete", false, { confirmDelete = true }, leadingIcon = Icons.Rounded.DeleteOutline)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${selected.size} ${if (selected.size == 1) "page" else "pages"}?",
            message = if (selected.size >=
                order.size
            ) {
                "This removes every page — the document will be empty."
            } else {
                "You can undo this from the editor."
            },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.delete(selected)
                selected = emptySet()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
