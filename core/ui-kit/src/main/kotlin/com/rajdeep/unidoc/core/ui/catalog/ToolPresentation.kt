package com.rajdeep.unidoc.core.ui.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rajdeep.unidoc.core.model.ToolCategory
import com.rajdeep.unidoc.core.model.ToolId

data class ToolPresentation(val title: String, val description: String, val icon: ImageVector)

data class CategoryPresentation(val title: String, val accent: List<Color>)

/** Harmonious accent pairs per category, all within the calm indigo–cyan family plus warm accents. */
fun ToolCategory.presentation(): CategoryPresentation = when (this) {
    ToolCategory.ORGANIZE -> CategoryPresentation("Organize", listOf(Color(0xFF4A5B2C), Color(0xFF6E8443)))
    ToolCategory.OPTIMIZE -> CategoryPresentation("Optimize", listOf(Color(0xFF5E7A3A), Color(0xFF8FA65A)))
    ToolCategory.CONVERT_TO_PDF -> CategoryPresentation("Convert to PDF", listOf(Color(0xFF7D7438), Color(0xFFA39556)))
    ToolCategory.CONVERT_FROM_PDF -> CategoryPresentation(
        "Convert from PDF",
        listOf(Color(0xFF46623F), Color(0xFF7E9A6B))
    )
    ToolCategory.EDIT -> CategoryPresentation("Edit", listOf(Color(0xFF6B6A33), Color(0xFF9A9A55)))
    ToolCategory.SECURITY -> CategoryPresentation("Security", listOf(Color(0xFF3F4A35), Color(0xFF66745A)))
    ToolCategory.EXTRAS -> CategoryPresentation("Extras", listOf(Color(0xFF8A7A3E), Color(0xFFB4A15E)))
}

fun ToolId.presentation(): ToolPresentation = when (this) {
    ToolId.MERGE_PDF -> ToolPresentation("Merge PDF", "Combine PDFs in the order you want", Icons.AutoMirrored.Rounded.MergeType)
    ToolId.SPLIT_PDF -> ToolPresentation("Split PDF", "Split by ranges or every page", Icons.AutoMirrored.Rounded.CallSplit)
    ToolId.REMOVE_PAGES -> ToolPresentation(
        "Remove pages",
        "Delete pages you don't need",
        Icons.Rounded.RemoveCircleOutline
    )
    ToolId.EXTRACT_PAGES -> ToolPresentation(
        "Extract pages",
        "Pull selected pages into a new PDF",
        Icons.Rounded.FileOpen
    )
    ToolId.ORGANIZE_PDF -> ToolPresentation(
        "Organize PDF",
        "Reorder, rotate and delete visually",
        Icons.Rounded.ViewModule
    )
    ToolId.ROTATE_PDF -> ToolPresentation("Rotate PDF", "Rotate all or some pages", Icons.AutoMirrored.Rounded.RotateRight)
    ToolId.SCAN_TO_PDF -> ToolPresentation(
        "Scan to PDF",
        "Capture paper straight into a PDF",
        Icons.Rounded.DocumentScanner
    )
    ToolId.COMPRESS_PDF -> ToolPresentation("Compress PDF", "Shrink size, keep text sharp", Icons.Rounded.Compress)
    ToolId.REPAIR_PDF -> ToolPresentation("Repair PDF", "Recover damaged or broken PDFs", Icons.Rounded.Build)
    ToolId.OCR_PDF -> ToolPresentation("OCR PDF", "Make scanned PDFs searchable", Icons.Rounded.ImageSearch)
    ToolId.IMAGE_TO_PDF -> ToolPresentation("Images to PDF", "JPG, PNG, WebP, HEIC into one PDF", Icons.Rounded.Image)
    ToolId.WORD_TO_PDF -> ToolPresentation("Word to PDF", "DOC, DOCX, ODT, RTF", Icons.Rounded.Description)
    ToolId.POWERPOINT_TO_PDF -> ToolPresentation("PowerPoint to PDF", "PPT, PPTX, ODP", Icons.Rounded.Slideshow)
    ToolId.EXCEL_TO_PDF -> ToolPresentation("Excel to PDF", "XLS, XLSX, ODS, CSV", Icons.Rounded.TableChart)
    ToolId.HTML_TO_PDF -> ToolPresentation("HTML to PDF", "Web page or HTML file", Icons.Rounded.Html)
    ToolId.TEXT_TO_PDF -> ToolPresentation("Text to PDF", "Plain text into clean pages", Icons.AutoMirrored.Rounded.TextSnippet)
    ToolId.PDF_TO_IMAGE -> ToolPresentation("PDF to images", "Every page as JPG or PNG", Icons.Rounded.Photo)
    ToolId.PDF_TO_WORD -> ToolPresentation("PDF to Word", "Editable Word document", Icons.Rounded.Description)
    ToolId.PDF_TO_POWERPOINT -> ToolPresentation(
        "PDF to PowerPoint",
        "One slide per page",
        Icons.Rounded.Slideshow
    )
    ToolId.PDF_TO_EXCEL -> ToolPresentation("PDF to Excel", "Tables into spreadsheets", Icons.Rounded.TableChart)
    ToolId.PDF_TO_PDFA -> ToolPresentation("PDF to PDF/A", "Long-term archive format", Icons.Rounded.Verified)
    ToolId.PDF_TO_TEXT -> ToolPresentation("PDF to text", "Extract all text as TXT", Icons.Rounded.TextFields)
    ToolId.PAGE_NUMBERS -> ToolPresentation(
        "Page numbers",
        "Position, style and start number",
        Icons.Rounded.FormatListNumbered
    )
    ToolId.WATERMARK -> ToolPresentation("Watermark", "Text stamp with opacity", Icons.Rounded.WaterDrop)
    ToolId.EDIT_PDF -> ToolPresentation("Edit PDF", "Place text anywhere on a page", Icons.Rounded.EditNote)
    ToolId.CROP_PDF -> ToolPresentation("Crop PDF", "Trim margins or custom area", Icons.Rounded.Crop)
    ToolId.SIGN_PDF -> ToolPresentation("Sign PDF", "Draw, type or certify", Icons.Rounded.Draw)
    ToolId.PROTECT_PDF -> ToolPresentation("Protect PDF", "AES-256 password", Icons.Rounded.Lock)
    ToolId.UNLOCK_PDF -> ToolPresentation("Unlock PDF", "Remove a password you know", Icons.Rounded.LockOpen)
    ToolId.REDACT_PDF -> ToolPresentation("Redact PDF", "Permanently black out content", Icons.Rounded.VisibilityOff)
    ToolId.COMPARE_PDF -> ToolPresentation("Compare PDFs", "Spot text and visual changes", Icons.Rounded.Compare)
    ToolId.IMAGE_TO_TEXT -> ToolPresentation("Image to text", "English and 14 Indian languages", Icons.Rounded.AutoFixHigh)
    ToolId.TEXT_TO_IMAGE -> ToolPresentation(
        "Text to image",
        "Render text as a shareable image",
        Icons.Rounded.BorderStyle
    )
    ToolId.IMAGE_CONVERT -> ToolPresentation("Convert images", "JPG ⇄ PNG ⇄ WebP", Icons.Rounded.SwapHoriz)
    ToolId.IMAGE_COMPRESS -> ToolPresentation(
        "Compress images",
        "Resize and reduce size",
        Icons.Rounded.PhotoSizeSelectLarge
    )
}
