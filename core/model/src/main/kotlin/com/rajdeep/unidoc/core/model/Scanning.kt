package com.rajdeep.unidoc.core.model

/** Capture modes. Each tunes detection, aspect ratio and default filter. */
enum class ScanMode {
    DOCUMENT,
    ID_CARD,
    BOOK,
    RECEIPT,
    WHITEBOARD,
    QR
}

enum class ScanFilter {
    ORIGINAL,
    AUTO,
    BLACK_WHITE,
    GRAYSCALE,
    ENHANCED,
    LIGHTEN,
    WHITEBOARD
}

enum class FlashMode {
    OFF,
    AUTO,
    ON,
    TORCH
}

/** Target output quality. [dpi] drives resampling; see Phase 0 §5. */
enum class QualityPreset(val dpi: Int) {
    HIGH(300),
    BALANCED(200),
    SMALL(150)
}

enum class ExportFormat {
    PDF,
    JPG,
    PNG
}

enum class PageSize {
    AUTO,
    A4,
    LETTER,
    LEGAL
}

enum class PageOrientation {
    AUTO,
    PORTRAIT,
    LANDSCAPE
}

enum class PageMargin {
    NONE,
    SMALL,
    LARGE
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val pageSize: PageSize = PageSize.AUTO,
    val orientation: PageOrientation = PageOrientation.AUTO,
    val margin: PageMargin = PageMargin.NONE,
    val searchable: Boolean = true,
    val password: String? = null
)
