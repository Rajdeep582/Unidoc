package com.rajdeep.unidoc.core.model

enum class ToolCategory {
    ORGANIZE,
    OPTIMIZE,
    CONVERT_TO_PDF,
    CONVERT_FROM_PDF,
    EDIT,
    SECURITY,
    EXTRAS
}

/** What a tool accepts as input. MIME patterns are used for the system file picker. */
enum class ToolInput(val mimeTypes: List<String>) {
    PDF(listOf("application/pdf")),
    IMAGE(listOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")),
    WORD(
        listOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.oasis.opendocument.text",
            "application/rtf",
            "text/rtf"
        )
    ),
    POWERPOINT(
        listOf(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/vnd.oasis.opendocument.presentation"
        )
    ),
    EXCEL(
        listOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.oasis.opendocument.spreadsheet",
            "text/csv",
            "text/comma-separated-values"
        )
    ),
    HTML(listOf("text/html")),
    TEXT(listOf("text/plain")),

    /** No file: tool takes typed input (e.g. Text → Image, URL → PDF). */
    NONE(emptyList())
}

/** Where the work runs by default (see Phase 0 §6). */
enum class ToolExecution {
    ON_DEVICE,
    ON_DEVICE_WITH_CLOUD_BOOST,
    CLOUD_WITH_OFFLINE_FALLBACK
}

/**
 * Full Uni Converter catalog. Order inside a category is display order.
 * [minFiles]/[maxFiles] bound the picker; [maxFiles] = Int.MAX_VALUE means batch.
 */
enum class ToolId(
    val category: ToolCategory,
    val input: ToolInput,
    val minFiles: Int = 1,
    val maxFiles: Int = 1,
    val execution: ToolExecution = ToolExecution.ON_DEVICE
) {
    // Organize
    MERGE_PDF(ToolCategory.ORGANIZE, ToolInput.PDF, minFiles = 2, maxFiles = Int.MAX_VALUE),
    SPLIT_PDF(ToolCategory.ORGANIZE, ToolInput.PDF),
    REMOVE_PAGES(ToolCategory.ORGANIZE, ToolInput.PDF),
    EXTRACT_PAGES(ToolCategory.ORGANIZE, ToolInput.PDF),
    ORGANIZE_PDF(ToolCategory.ORGANIZE, ToolInput.PDF),
    ROTATE_PDF(ToolCategory.ORGANIZE, ToolInput.PDF, maxFiles = Int.MAX_VALUE),
    SCAN_TO_PDF(ToolCategory.ORGANIZE, ToolInput.NONE, minFiles = 0, maxFiles = 0),

    // Optimize
    COMPRESS_PDF(
        ToolCategory.OPTIMIZE,
        ToolInput.PDF,
        maxFiles = Int.MAX_VALUE,
        execution = ToolExecution.ON_DEVICE_WITH_CLOUD_BOOST
    ),
    REPAIR_PDF(ToolCategory.OPTIMIZE, ToolInput.PDF, execution = ToolExecution.CLOUD_WITH_OFFLINE_FALLBACK),
    OCR_PDF(ToolCategory.OPTIMIZE, ToolInput.PDF, execution = ToolExecution.ON_DEVICE_WITH_CLOUD_BOOST),

    // Convert to PDF
    IMAGE_TO_PDF(ToolCategory.CONVERT_TO_PDF, ToolInput.IMAGE, maxFiles = Int.MAX_VALUE),
    WORD_TO_PDF(
        ToolCategory.CONVERT_TO_PDF,
        ToolInput.WORD,
        maxFiles = Int.MAX_VALUE,
        execution = ToolExecution.CLOUD_WITH_OFFLINE_FALLBACK
    ),
    POWERPOINT_TO_PDF(
        ToolCategory.CONVERT_TO_PDF,
        ToolInput.POWERPOINT,
        maxFiles = Int.MAX_VALUE,
        execution = ToolExecution.CLOUD_WITH_OFFLINE_FALLBACK
    ),
    EXCEL_TO_PDF(
        ToolCategory.CONVERT_TO_PDF,
        ToolInput.EXCEL,
        maxFiles = Int.MAX_VALUE,
        execution = ToolExecution.CLOUD_WITH_OFFLINE_FALLBACK
    ),
    HTML_TO_PDF(
        ToolCategory.CONVERT_TO_PDF,
        ToolInput.HTML,
        minFiles = 0,
        execution = ToolExecution.ON_DEVICE_WITH_CLOUD_BOOST
    ),
    TEXT_TO_PDF(ToolCategory.CONVERT_TO_PDF, ToolInput.TEXT, maxFiles = Int.MAX_VALUE),

    // Convert from PDF
    PDF_TO_IMAGE(ToolCategory.CONVERT_FROM_PDF, ToolInput.PDF),
    PDF_TO_WORD(ToolCategory.CONVERT_FROM_PDF, ToolInput.PDF, execution = ToolExecution.CLOUD_WITH_OFFLINE_FALLBACK),
    PDF_TO_POWERPOINT(
        ToolCategory.CONVERT_FROM_PDF,
        ToolInput.PDF,
        execution = ToolExecution.CLOUD_WITH_OFFLINE_FALLBACK
    ),
    PDF_TO_EXCEL(ToolCategory.CONVERT_FROM_PDF, ToolInput.PDF, execution = ToolExecution.CLOUD_WITH_OFFLINE_FALLBACK),
    PDF_TO_PDFA(ToolCategory.CONVERT_FROM_PDF, ToolInput.PDF, execution = ToolExecution.CLOUD_WITH_OFFLINE_FALLBACK),
    PDF_TO_TEXT(ToolCategory.CONVERT_FROM_PDF, ToolInput.PDF),

    // Edit
    PAGE_NUMBERS(ToolCategory.EDIT, ToolInput.PDF),
    WATERMARK(ToolCategory.EDIT, ToolInput.PDF),
    EDIT_PDF(ToolCategory.EDIT, ToolInput.PDF),
    CROP_PDF(ToolCategory.EDIT, ToolInput.PDF),
    SIGN_PDF(ToolCategory.EDIT, ToolInput.PDF),

    // Security
    PROTECT_PDF(ToolCategory.SECURITY, ToolInput.PDF),
    UNLOCK_PDF(ToolCategory.SECURITY, ToolInput.PDF),
    REDACT_PDF(ToolCategory.SECURITY, ToolInput.PDF),

    // Extras
    COMPARE_PDF(ToolCategory.EXTRAS, ToolInput.PDF, minFiles = 2, maxFiles = 2),
    IMAGE_TO_TEXT(ToolCategory.EXTRAS, ToolInput.IMAGE, maxFiles = Int.MAX_VALUE),
    TEXT_TO_IMAGE(ToolCategory.EXTRAS, ToolInput.NONE, minFiles = 0, maxFiles = 0),
    IMAGE_CONVERT(ToolCategory.EXTRAS, ToolInput.IMAGE, maxFiles = Int.MAX_VALUE),
    IMAGE_COMPRESS(ToolCategory.EXTRAS, ToolInput.IMAGE, maxFiles = Int.MAX_VALUE)
    ;

    val isBatch: Boolean get() = maxFiles > 1
    val usesCloud: Boolean get() = execution != ToolExecution.ON_DEVICE

    companion object {
        fun byCategory(): Map<ToolCategory, List<ToolId>> = entries.groupBy { it.category }
    }
}
