package com.rajdeep.unidoc.feature.converter.tools

import com.rajdeep.unidoc.core.model.PickedFile
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.model.ToolInput
import com.rajdeep.unidoc.core.model.ToolOutput
import com.rajdeep.unidoc.core.scan.annotation.SavedSignature
import com.rajdeep.unidoc.feature.converter.tools.editor.Placement
import com.rajdeep.unidoc.feature.converter.tools.preview.PdfInfo

sealed interface ToolStep {
    data object Input : ToolStep

    data class Processing(val fraction: Float?, val stage: String) : ToolStep

    data class Done(val outputs: List<ToolOutput>, val inputBytes: Long, val note: String? = null) : ToolStep

    data class Error(val message: String, val retryable: Boolean) : ToolStep
}

data class ToolUiState(
    val tool: ToolId,
    val files: List<PickedFile> = emptyList(),
    val options: Map<String, String> = ToolOptions.defaults(tool),
    val step: ToolStep = ToolStep.Input,
    /** Page info of the (single) input PDF, for visual page editors and previews. */
    val pdfInfo: PdfInfo? = null,
    val signature: SavedSignature? = null,
    /** Unidoc Cloud is set up; cloud-only extras are hidden otherwise. */
    val cloudReady: Boolean = false
) {
    val spec: List<ToolOption> get() = ToolOptions.forTool(tool)

    val placement: Placement? get() = Placement.decode(options[KEY_PLACE])

    /** Option fields shown as text controls (some are replaced by visual editors). */
    val visibleSpec: List<ToolOption>
        get() {
            val hidden = buildSet {
                if (tool == ToolId.ORGANIZE_PDF && pdfInfo != null) addAll(listOf("order", "rotate"))
                if (placement != null && tool == ToolId.SIGN_PDF) addAll(listOf("page", "position", "size"))
                if (placement != null && tool == ToolId.EDIT_PDF) addAll(listOf("position", "pages"))
                if (!cloudReady) add("cloud_boost")
                if (tool == ToolId.SPLIT_PDF && options["mode"] == "each") add("ranges")
            }
            return spec.filterNot { it.key in hidden }.map { o ->
                // Split: the same field means "ranges" or "pages per file" depending on the mode.
                if (tool == ToolId.SPLIT_PDF && o.key == "ranges" && options["mode"] == "every" && o is ToolOption.Text) {
                    o.copy(label = "Pages per file", placeholder = "e.g. 2")
                } else {
                    o
                }
            }
        }

    /** The single input PDF when a visual page editor can be offered. */
    val editablePdf: PickedFile?
        get() = files.singleOrNull()?.takeIf { pdfInfo != null && tool.input == ToolInput.PDF }

    val pageEditor: PageEditorKind?
        get() = if (editablePdf == null) {
            null
        } else {
            when (tool) {
                ToolId.ORGANIZE_PDF -> PageEditorKind.ARRANGE
                ToolId.REMOVE_PAGES, ToolId.EXTRACT_PAGES, ToolId.ROTATE_PDF -> PageEditorKind.SELECT
                ToolId.SIGN_PDF, ToolId.EDIT_PDF -> PageEditorKind.PLACE
                else -> PageEditorKind.PREVIEW
            }
        }

    val needsFiles: Boolean get() = tool.input != ToolInput.NONE && tool.maxFiles > 0

    val canAddMore: Boolean get() = files.size < tool.maxFiles

    /** Why Run is disabled, or null when ready. */
    val blocker: String?
        get() {
            val urlGiven = tool == ToolId.HTML_TO_PDF && !options["url"].isNullOrBlank()
            return when {
                needsFiles && files.size < tool.minFiles.coerceAtLeast(1) && !urlGiven ->
                    if (tool.minFiles > 1) "Add at least ${tool.minFiles} files" else "Add a file to start"
                visibleSpec.any {
                    it is ToolOption.Text && it.required && options[it.key].isNullOrBlank()
                } -> "Fill in the required fields"
                tool == ToolId.SPLIT_PDF && options["mode"] == "every" && (options["ranges"]?.trim()?.toIntOrNull() ?: 0) < 1 ->
                    "Enter how many pages go in each file"
                tool == ToolId.SPLIT_PDF && options["mode"].let { it == null || it == "ranges" } && options["ranges"].isNullOrBlank() ->
                    "Enter the page ranges to split"
                tool == ToolId.PROTECT_PDF && (options["password"]?.length ?: 0) < 4 -> "Use a password of at least 4 characters"
                tool == ToolId.SIGN_PDF && signature == null && options["name"].isNullOrBlank() -> "Draw your signature or enter a name"
                else -> null
            }
        }
}

enum class PageEditorKind { ARRANGE, SELECT, PLACE, PREVIEW }

const val KEY_PLACE = "place"

sealed interface ToolAction {
    data class FilesPicked(val uris: List<android.net.Uri>) : ToolAction

    data class RemoveFile(val uri: String) : ToolAction

    data class MoveFile(val from: Int, val to: Int) : ToolAction

    data class OptionChanged(val key: String, val value: String) : ToolAction

    /** Several option values at once (visual editors). */
    data class OptionsChanged(val values: Map<String, String>) : ToolAction

    data class SaveSignature(val strokes: List<List<Float>>, val aspect: Float) : ToolAction

    data object Run : ToolAction

    data object Cancel : ToolAction

    data object Reset : ToolAction

    /** Retry with the same files after an error. */
    data object BackToInput : ToolAction

    data class SaveTo(val target: android.net.Uri, val isFolder: Boolean) : ToolAction
}
