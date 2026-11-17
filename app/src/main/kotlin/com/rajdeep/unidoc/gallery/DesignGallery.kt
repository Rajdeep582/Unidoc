package com.rajdeep.unidoc.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.rajdeep.unidoc.core.model.FlashMode
import com.rajdeep.unidoc.core.model.ScanFilter
import com.rajdeep.unidoc.core.ui.brand.ScreenSurface
import com.rajdeep.unidoc.core.ui.components.NavigationRow
import com.rajdeep.unidoc.core.ui.components.SettingsDivider
import com.rajdeep.unidoc.core.ui.components.SettingsGroup
import com.rajdeep.unidoc.core.ui.components.UnidocTopBar
import com.rajdeep.unidoc.feature.converter.tools.SampleTool
import com.rajdeep.unidoc.feature.converter.tools.ToolScreen
import com.rajdeep.unidoc.feature.scanner.capture.CaptureAction
import com.rajdeep.unidoc.feature.scanner.capture.CaptureScreen
import com.rajdeep.unidoc.feature.scanner.capture.SampleCapture
import com.rajdeep.unidoc.feature.scanner.editor.CropScreen
import com.rajdeep.unidoc.feature.scanner.editor.DocumentCallbacks
import com.rajdeep.unidoc.feature.scanner.editor.DocumentScreen
import com.rajdeep.unidoc.feature.scanner.editor.EditorAction
import com.rajdeep.unidoc.feature.scanner.editor.EditorScreen
import com.rajdeep.unidoc.feature.scanner.editor.EditorUiState
import com.rajdeep.unidoc.feature.scanner.editor.PagesScreen
import com.rajdeep.unidoc.feature.scanner.editor.SampleCrop
import com.rajdeep.unidoc.feature.scanner.editor.SampleEditor
import com.rajdeep.unidoc.feature.scanner.export.ExportOptionsContent
import com.rajdeep.unidoc.feature.scanner.export.SampleExport
import com.rajdeep.unidoc.feature.scanner.home.LibraryStatus
import com.rajdeep.unidoc.feature.scanner.home.SampleLibrary
import com.rajdeep.unidoc.feature.scanner.home.ScannerHomeAction
import com.rajdeep.unidoc.feature.scanner.home.ScannerHomeCallbacks
import com.rajdeep.unidoc.feature.scanner.home.ScannerHomeContent
import com.rajdeep.unidoc.feature.scanner.home.ScannerHomeUiState
import kotlinx.serialization.Serializable

@Serializable
data object GalleryRoute

@Serializable
data class GalleryItemRoute(val id: String)

fun NavController.navigateToGallery() = navigate(GalleryRoute)

/** Every designed screen/state, interactive where it helps judge motion. Debug builds only. */
enum class GalleryItem(val group: String, val title: String) {
    HOME_CONTENT("Library", "Documents"),
    HOME_SELECTION("Library", "Multi-select"),
    HOME_LOADING("Library", "Loading"),
    HOME_ERROR("Library", "Error"),
    CAPTURE_DETECT("Scanner", "Capture · detecting"),
    CAPTURE_IDLE("Scanner", "Capture · searching"),
    EDITOR("Scanner", "Editor (interactive)"),
    CROP("Scanner", "Crop with loupe"),
    PAGES("Scanner", "Page manager"),
    DOCUMENT("Scanner", "Document view"),
    EXPORT("Scanner", "Export options"),
    TOOL_MERGE("Converter", "Tool · files picked"),
    TOOL_EMPTY("Converter", "Tool · empty"),
    TOOL_PROCESSING("Converter", "Tool · processing"),
    TOOL_DONE("Converter", "Tool · result"),
    TOOL_ERROR("Converter", "Tool · error")
}

fun NavGraphBuilder.galleryScreens(nav: NavController) {
    composable<GalleryRoute> {
        ScreenSurface {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                UnidocTopBar("Design gallery", onBack = nav::popBackStack)
                LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    GalleryItem.entries.groupBy { it.group }.forEach { (group, items) ->
                        item {
                            SettingsGroup(group) {
                                items.forEachIndexed { i, entry ->
                                    if (i > 0) SettingsDivider()
                                    NavigationRow(entry.title, { nav.navigate(GalleryItemRoute(entry.name)) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    composable<GalleryItemRoute> { entry ->
        ScreenSurface {
            GalleryItemScreen(GalleryItem.valueOf(entry.toRoute<GalleryItemRoute>().id), nav::popBackStack)
        }
    }
}

@Composable
fun GalleryItemScreen(item: GalleryItem, onBack: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pad = PaddingValues(bottom = bottom + 24.dp)
    val ready = ScannerHomeUiState(LibraryStatus.Ready, SampleLibrary.documents, SampleLibrary.folders)
    when (item) {
        GalleryItem.HOME_CONTENT -> InteractiveHome(ready, pad)
        GalleryItem.HOME_SELECTION -> InteractiveHome(ready.copy(selectedIds = setOf("1", "4")), pad)
        GalleryItem.HOME_LOADING -> InteractiveHome(ScannerHomeUiState(LibraryStatus.Loading), pad)
        GalleryItem.HOME_ERROR -> InteractiveHome(
            ScannerHomeUiState(LibraryStatus.Error("Storage is not available right now.")),
            pad
        )
        GalleryItem.CAPTURE_DETECT, GalleryItem.CAPTURE_IDLE -> {
            var s by remember {
                mutableStateOf(
                    if (item ==
                        GalleryItem.CAPTURE_DETECT
                    ) {
                        SampleCapture.detecting
                    } else {
                        SampleCapture.idle
                    }
                )
            }
            CaptureScreen(s, { a ->
                s = when (a) {
                    is CaptureAction.ModeChanged -> s.copy(mode = a.mode)
                    CaptureAction.CycleFlash -> s.copy(
                        flash = FlashMode.entries[
                            (s.flash.ordinal + 1) %
                                FlashMode.entries.size
                        ]
                    )
                    CaptureAction.ToggleGrid -> s.copy(gridVisible = !s.gridVisible)
                    CaptureAction.ToggleAutoCapture -> s.copy(autoCapture = !s.autoCapture)
                    CaptureAction.ToggleBatch -> s.copy(batch = !s.batch)
                    CaptureAction.Shutter -> s.copy(pageCount = s.pageCount + 1)
                    CaptureAction.Close, CaptureAction.Done -> s.also { onBack() }
                    CaptureAction.OpenGallery -> s
                    CaptureAction.DismissQr -> s.copy(qrValue = null)
                }
            })
        }
        GalleryItem.EDITOR -> {
            var s by remember { mutableStateOf(SampleEditor.state) }
            EditorScreen(s, { a -> s = reduceEditor(s, a, onBack) })
        }
        GalleryItem.CROP -> {
            var corners by remember { mutableStateOf(SampleCrop.corners) }
            CropScreen(corners, { corners = it }, { corners = SampleCrop.corners }, {
                corners = listOf(
                    androidx.compose.ui.geometry.Offset(0f, 0f),
                    androidx.compose.ui.geometry.Offset(1f, 0f),
                    androidx.compose.ui.geometry.Offset(1f, 1f),
                    androidx.compose.ui.geometry.Offset(0f, 1f)
                )
            }, onBack, onBack)
        }
        GalleryItem.PAGES -> {
            var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
            PagesScreen(
                pages = SampleEditor.state.pages + SampleEditor.state.pages.map { it.copy(id = it.id + 10) },
                selectedIds = selected,
                onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                onAddPage = {},
                onRetake = {},
                onDuplicate = {},
                onDelete = {},
                onBack = onBack
            )
        }
        GalleryItem.DOCUMENT -> DocumentScreen(SampleLibrary.documents.first(), DocumentCallbacks(onBack = onBack))
        GalleryItem.EXPORT -> {
            var s by remember { mutableStateOf(SampleExport.state) }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).statusBarsPadding()) {
                ExportOptionsContent(s, { s = s.copy(fileName = it) }, { s = s.copy(options = it) }, onBack)
            }
        }
        GalleryItem.TOOL_MERGE -> ToolScreen(SampleTool.mergeInput, {}, onBack, {})
        GalleryItem.TOOL_EMPTY -> ToolScreen(SampleTool.compressEmpty, {}, onBack, {})
        GalleryItem.TOOL_PROCESSING -> ToolScreen(SampleTool.processing, {}, onBack, {})
        GalleryItem.TOOL_DONE -> ToolScreen(SampleTool.done, {}, onBack, {})
        GalleryItem.TOOL_ERROR -> ToolScreen(SampleTool.error, {}, onBack, {})
    }
}

@Composable
private fun InteractiveHome(initial: ScannerHomeUiState, pad: PaddingValues) {
    var s by remember { mutableStateOf(initial) }
    ScannerHomeContent(
        state = s,
        onAction = { a ->
            s = when (a) {
                is ScannerHomeAction.QueryChanged -> s.copy(query = a.query)
                is ScannerHomeAction.SortChanged -> s.copy(sort = a.sort)
                ScannerHomeAction.ToggleLayout -> s.copy(
                    layout = if (s.layout.name ==
                        "GRID"
                    ) {
                        com.rajdeep.unidoc.core.model.LibraryLayout.LIST
                    } else {
                        com.rajdeep.unidoc.core.model.LibraryLayout.GRID
                    }
                )
                is ScannerHomeAction.FolderSelected -> s.copy(selectedFolderId = a.folderId)
                is ScannerHomeAction.ToggleSelection -> s.copy(
                    selectedIds = if (a.documentId in
                        s.selectedIds
                    ) {
                        s.selectedIds - a.documentId
                    } else {
                        s.selectedIds + a.documentId
                    }
                )
                ScannerHomeAction.SelectAll -> s.copy(selectedIds = s.visibleDocuments.map { it.id }.toSet())
                ScannerHomeAction.ClearSelection -> s.copy(selectedIds = emptySet())
                ScannerHomeAction.Retry -> s.copy(status = LibraryStatus.Loading)
            }
        },
        callbacks = ScannerHomeCallbacks(),
        contentPadding = pad
    )
}

internal fun reduceEditor(s: EditorUiState, a: EditorAction, onBack: () -> Unit): EditorUiState = when (a) {
    EditorAction.Back -> s.also { onBack() }
    is EditorAction.PageChanged -> s.copy(currentPage = a.index)
    is EditorAction.ToolSelected -> s.copy(activeTool = a.tool)
    is EditorAction.FilterSelected -> s.copy(
        pages = s.pages.mapIndexed { i, p ->
            if (i ==
                s.currentPage
            ) {
                p.copy(filter = a.filter)
            } else {
                p
            }
        },
        canUndo = true
    )
    EditorAction.ApplyFilterToAll -> s.copy(pages = s.pages.map { it.copy(filter = s.page?.filter ?: ScanFilter.AUTO) })
    is EditorAction.AdjustmentSelected -> s.copy(adjustment = a.adjustment)
    is EditorAction.AdjustmentChanged -> s.copy(adjustments = s.adjustments + (s.adjustment to a.value))
    EditorAction.RotateLeft -> s.copy(
        pages = s.pages.mapIndexed { i, p ->
            if (i ==
                s.currentPage
            ) {
                p.copy(rotationDegrees = p.rotationDegrees - 90)
            } else {
                p
            }
        }
    )
    EditorAction.RotateRight -> s.copy(
        pages = s.pages.mapIndexed { i, p ->
            if (i ==
                s.currentPage
            ) {
                p.copy(rotationDegrees = p.rotationDegrees + 90)
            } else {
                p
            }
        }
    )
    is EditorAction.StraightenChanged -> s.copy(straighten = a.degrees)
    is EditorAction.AnnotateToolSelected -> s.copy(annotateTool = a.tool)
    is EditorAction.AnnotateColorSelected -> s.copy(annotateColorIndex = a.index)
    is EditorAction.StrokeWidthChanged -> s.copy(strokeWidth = a.value)
    EditorAction.Undo, EditorAction.Redo, EditorAction.Export, EditorAction.FlipHorizontal, EditorAction.NewSignature -> s
}
