package com.rajdeep.unidoc.feature.converter.tools

import com.rajdeep.unidoc.core.model.OcrLanguage
import com.rajdeep.unidoc.core.model.ToolId

/** Declarative option spec; values are stored as strings keyed by [key] (saved-state friendly). */
sealed interface ToolOption {
    val key: String
    val label: String
    val default: String

    data class Choice(
        override val key: String,
        override val label: String,
        val choices: List<Pair<String, String>>,
        override val default: String = choices.first().first,
        /** Shown greyed out with a "Soon" tag; not selectable. */
        val comingSoon: List<String> = emptyList()
    ) : ToolOption

    data class Toggle(
        override val key: String,
        override val label: String,
        val description: String? = null,
        val defaultOn: Boolean = false
    ) : ToolOption {
        override val default: String get() = defaultOn.toString()
    }

    data class Slider(
        override val key: String,
        override val label: String,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int = 0,
        val defaultValue: Float,
        val unit: String = ""
    ) : ToolOption {
        override val default: String get() = defaultValue.toString()
    }

    data class Text(
        override val key: String,
        override val label: String,
        val placeholder: String = "",
        val password: Boolean = false,
        val multiline: Boolean = false,
        val required: Boolean = false,
        override val default: String = ""
    ) : ToolOption
}

private val pageSizes = listOf("fit" to "Fit", "a4" to "A4", "letter" to "Letter")
private val ocrLangs = OcrLanguage.entries.filter { it.engine != com.rajdeep.unidoc.core.model.OcrEngine.CLOUD }.map {
    it.tag to
        if (it.englishName == it.nativeName) it.englishName else "${it.englishName} · ${it.nativeName}"
}
private val ocrSoon = OcrLanguage.entries.filter { it.engine == com.rajdeep.unidoc.core.model.OcrEngine.CLOUD }.map { it.englishName }
private val positions = listOf(
    "bottom_center" to "Bottom center",
    "bottom_right" to "Bottom right",
    "bottom_left" to "Bottom left",
    "top_center" to "Top center",
    "top_right" to "Top right",
    "top_left" to "Top left"
)

object ToolOptions {
    fun forTool(tool: ToolId): List<ToolOption> = when (tool) {
        ToolId.MERGE_PDF -> listOf(ToolOption.Toggle("bookmarks", "Add bookmark per file", defaultOn = true))
        ToolId.SPLIT_PDF -> listOf(
            ToolOption.Choice(
                "mode",
                "Split by",
                listOf(
                    "ranges" to "Page ranges",
                    "every" to "Every N pages",
                    "each" to "Each page"
                )
            ),
            ToolOption.Text("ranges", "Page ranges", placeholder = "e.g. 1-3, 5, 8-10")
        )
        ToolId.REMOVE_PAGES -> listOf(
            ToolOption.Text("pages", "Pages to remove", placeholder = "e.g. 2, 4-6", required = true)
        )
        ToolId.EXTRACT_PAGES -> listOf(
            ToolOption.Text("pages", "Pages to extract", placeholder = "e.g. 1-3, 7", required = true)
        )
        ToolId.ORGANIZE_PDF -> listOf(
            ToolOption.Text("order", "New page order", placeholder = "e.g. 3, 1-2, 5 (blank keeps order)"),
            ToolOption.Text("rotate", "Rotate pages", placeholder = "page:angle, e.g. 2:90, 4:180")
        )
        ToolId.EDIT_PDF -> listOf(
            ToolOption.Text("text", "Text to add", multiline = true, required = true),
            ToolOption.Choice("position", "Position", positions.reversed()),
            ToolOption.Slider("size", "Text size", 8f..36f, defaultValue = 14f, unit = "pt"),
            ToolOption.Text("pages", "Pages", placeholder = "All pages (or e.g. 1, 3-4)")
        )
        ToolId.SIGN_PDF -> listOf(
            ToolOption.Text("name", "Name under signature", placeholder = "Optional"),
            ToolOption.Choice("page", "Page", listOf("last" to "Last page", "first" to "First page", "all" to "Every page")),
            ToolOption.Choice(
                "position",
                "Position",
                listOf(
                    "bottom_right" to "Bottom right",
                    "bottom_left" to "Bottom left",
                    "bottom_center" to "Bottom center",
                    "top_right" to "Top right"
                )
            ),
            ToolOption.Slider("size", "Signature width", 0.15f..0.5f, defaultValue = 0.28f),
            ToolOption.Toggle("date", "Add date", defaultOn = true),
            ToolOption.Toggle("certify", "Digitally certify", "Detects any later change to the PDF", defaultOn = true)
        )
        ToolId.REPAIR_PDF, ToolId.SCAN_TO_PDF -> emptyList()
        ToolId.ROTATE_PDF -> listOf(
            ToolOption.Choice("angle", "Rotate", listOf("90" to "90° right", "180" to "180°", "270" to "90° left")),
            ToolOption.Text("pages", "Pages", placeholder = "All pages (or e.g. 1, 3-4)")
        )
        ToolId.COMPRESS_PDF -> listOf(
            ToolOption.Choice(
                "level",
                "Compression",
                listOf(
                    "balanced" to "Balanced",
                    "high" to "High quality",
                    "small" to "Smallest"
                ),
                default = "balanced"
            ),
            ToolOption.Toggle("cloud_boost", "Extra compression", "Usually 10–30% smaller (Unidoc Cloud)")
        )
        ToolId.OCR_PDF -> listOf(
            ToolOption.Choice("lang", "Language", ocrLangs, comingSoon = ocrSoon),
            ToolOption.Toggle("skip_text", "Skip pages that already have text", defaultOn = true)
        )
        ToolId.IMAGE_TO_PDF -> listOf(
            ToolOption.Choice("page_size", "Page size", pageSizes),
            ToolOption.Choice("margin", "Margins", listOf("none" to "None", "small" to "Small", "large" to "Large")),
            ToolOption.Toggle("single", "Merge into one PDF", defaultOn = true)
        )
        ToolId.WORD_TO_PDF, ToolId.POWERPOINT_TO_PDF, ToolId.EXCEL_TO_PDF -> emptyList()
        ToolId.HTML_TO_PDF -> listOf(
            ToolOption.Text("url", "Web address", placeholder = "https://… or pick an HTML file"),
            ToolOption.Choice("page_size", "Page size", pageSizes.drop(1))
        )
        ToolId.TEXT_TO_PDF -> listOf(
            ToolOption.Choice("page_size", "Page size", pageSizes.drop(1)),
            ToolOption.Slider("font_size", "Font size", 8f..20f, steps = 11, defaultValue = 11f, unit = "pt")
        )
        ToolId.PDF_TO_IMAGE -> listOf(
            ToolOption.Choice("format", "Format", listOf("jpg" to "JPG", "png" to "PNG")),
            ToolOption.Slider("dpi", "Resolution", 72f..300f, steps = 0, defaultValue = 150f, unit = "dpi")
        )
        ToolId.PDF_TO_WORD, ToolId.PDF_TO_EXCEL -> listOf(
            ToolOption.Toggle("ocr", "Recognize text in scanned pages", defaultOn = true)
        )
        ToolId.PDF_TO_POWERPOINT -> emptyList()
        ToolId.PDF_TO_PDFA -> listOf(
            ToolOption.Choice(
                "level",
                "Conformance",
                listOf(
                    "2b" to "PDF/A-2b (recommended)",
                    "1b" to "PDF/A-1b",
                    "3b" to "PDF/A-3b"
                )
            )
        )
        ToolId.PDF_TO_TEXT -> listOf(ToolOption.Toggle("layout", "Preserve layout", defaultOn = false))
        ToolId.PAGE_NUMBERS -> listOf(
            ToolOption.Choice("position", "Position", positions),
            ToolOption.Choice("format", "Style", listOf("n" to "1", "page_n" to "Page 1", "n_of_total" to "1 of 12")),
            ToolOption.Slider("start", "Start at", 1f..100f, steps = 0, defaultValue = 1f)
        )
        ToolId.WATERMARK -> listOf(
            ToolOption.Text("text", "Watermark text", placeholder = "CONFIDENTIAL", required = true),
            ToolOption.Choice(
                "layout",
                "Layout",
                listOf(
                    "diagonal" to "Diagonal",
                    "center" to "Center",
                    "tile" to "Tiled"
                )
            ),
            ToolOption.Slider("opacity", "Opacity", 0.05f..0.8f, defaultValue = 0.2f)
        )
        ToolId.CROP_PDF -> listOf(
            ToolOption.Choice(
                "mode",
                "Mode",
                listOf("auto" to "Auto-trim white margins", "custom" to "Custom margins")
            ),
            ToolOption.Slider("margin", "Margin", 0f..50f, defaultValue = 10f, unit = "mm")
        )
        ToolId.PROTECT_PDF -> listOf(
            ToolOption.Text("password", "Password", password = true, required = true),
            ToolOption.Toggle("allow_print", "Allow printing", defaultOn = true),
            ToolOption.Toggle("allow_copy", "Allow copying text", defaultOn = false)
        )
        ToolId.UNLOCK_PDF -> listOf(ToolOption.Text("password", "Current password", password = true, required = true))
        ToolId.REDACT_PDF -> listOf(
            ToolOption.Text("terms", "Words to redact", placeholder = "Comma separated"),
            ToolOption.Toggle("patterns", "Also redact emails, phone & card numbers", defaultOn = true)
        )
        ToolId.COMPARE_PDF -> listOf(
            ToolOption.Choice(
                "mode",
                "Compare",
                listOf(
                    "text" to "Text changes",
                    "visual" to "Visual overlay"
                )
            )
        )
        ToolId.IMAGE_TO_TEXT -> listOf(ToolOption.Choice("lang", "Language", ocrLangs, comingSoon = ocrSoon))
        ToolId.TEXT_TO_IMAGE -> listOf(
            ToolOption.Text("text", "Text", multiline = true, required = true),
            ToolOption.Choice(
                "theme",
                "Style",
                listOf("light" to "Light paper", "dark" to "Dark", "brand" to "Gradient")
            ),
            ToolOption.Choice("format", "Format", listOf("png" to "PNG", "jpg" to "JPG"))
        )
        ToolId.IMAGE_CONVERT -> listOf(
            ToolOption.Choice("format", "Convert to", listOf("jpg" to "JPG", "png" to "PNG", "webp" to "WebP")),
            ToolOption.Slider("quality", "Quality", 40f..100f, defaultValue = 85f, unit = "%")
        )
        ToolId.IMAGE_COMPRESS -> listOf(
            ToolOption.Slider("quality", "Quality", 30f..95f, defaultValue = 75f, unit = "%"),
            ToolOption.Slider("max_side", "Longest side", 800f..4000f, defaultValue = 2000f, unit = "px")
        )
    }

    fun defaults(tool: ToolId): Map<String, String> = forTool(tool).associate { it.key to it.default }
}
