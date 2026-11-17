package com.rajdeep.unidoc.core.converter

import android.content.Context
import com.rajdeep.unidoc.core.converter.cloud.CloudClient
import com.rajdeep.unidoc.core.converter.cloud.CloudRejectedException
import com.rajdeep.unidoc.core.converter.cloud.CloudUnavailableException
import com.rajdeep.unidoc.core.converter.image.ImageTools
import com.rajdeep.unidoc.core.converter.office.HtmlPrinter
import com.rajdeep.unidoc.core.converter.office.OfficeHtml
import com.rajdeep.unidoc.core.converter.pdf.PdfContent
import com.rajdeep.unidoc.core.converter.pdf.PdfOrganize
import com.rajdeep.unidoc.core.converter.pdf.PdfPasswordException
import com.rajdeep.unidoc.core.converter.pdf.PdfSecure
import com.rajdeep.unidoc.core.converter.pdf.PdfStamp
import com.rajdeep.unidoc.core.model.OcrLanguage
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.model.ToolProgress
import com.rajdeep.unidoc.core.model.ToolRequest
import com.rajdeep.unidoc.core.model.ToolRunner
import com.rajdeep.unidoc.core.scan.ocr.OcrEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Runs converter tools. On-device first; Cloud (the user's free Hugging Face Space) is used for
 * Office fidelity, Indian-script OCR, Ghostscript compression and PDF/A — and silently falls back
 * to the on-device path when the server is unreachable.
 */
@Singleton
class ConverterEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cloud: CloudClient,
    private val ocr: OcrEngine
) : ToolRunner {

    override fun run(request: ToolRequest): Flow<ToolProgress> = channelFlow {
        val workspace = Workspace(context, UUID.randomUUID().toString().take(12))
        try {
            send(ToolProgress.Running(null, if (request.inputs.isEmpty()) "Starting" else "Preparing files"))
            val inputs = request.inputs.mapIndexed { i, f -> workspace.copyIn(f, i) }
            val ctx = JobContext(context, workspace, inputs, request.inputs.map { it.name }, request.options) { fraction, stage ->
                trySend(ToolProgress.Running(fraction?.coerceIn(0f, 1f), stage))
            }
            val (files, note) = execute(request.tool, ctx)
            require(files.isNotEmpty()) { "Nothing was produced" }
            workspace.inDir.deleteRecursively()
            send(ToolProgress.Completed(workspace.toOutputs(files), request.inputs.sumOf { it.sizeBytes.coerceAtLeast(0) }, note))
        } catch (e: CancellationException) {
            workspace.root.deleteRecursively()
            throw e
        } catch (e: Throwable) {
            workspace.root.deleteRecursively()
            send(failure(e))
        }
        awaitClose()
    }.buffer(16, BufferOverflow.DROP_OLDEST).flowOn(Dispatchers.IO)

    private fun failure(e: Throwable): ToolProgress.Failed = when (e) {
        is OutOfMemoryError -> ToolProgress.Failed("Not enough memory for this file — try a lower resolution or fewer pages")
        is PdfPasswordException -> ToolProgress.Failed(e.message!!, retryable = false)
        is OfficeHtml.UnsupportedFormatException -> ToolProgress.Failed(e.message!!, retryable = false)
        is CloudRejectedException -> ToolProgress.Failed(e.message ?: "Cloud refused the file", retryable = false)
        is IllegalArgumentException -> ToolProgress.Failed(e.message ?: "Invalid input", retryable = false)
        is IOException -> ToolProgress.Failed(e.message?.let { "Couldn't process the file: $it" } ?: "Couldn't read or write the file")
        else -> ToolProgress.Failed(e.message ?: "Something went wrong")
    }

    private suspend fun cloudConfig(ctx: JobContext, key: String = "cloud", default: Boolean = true): CloudClient.Config? =
        if (ctx.flag(key, default)) cloud.config() else null

    /** Runs [tool] on the Space (one request per input file unless [together]). */
    private suspend fun viaCloud(
        ctx: JobContext,
        config: CloudClient.Config,
        tool: String,
        outExt: String,
        together: Boolean = false
    ): List<File> {
        ctx.progress(null, "Converting")
        cloud.wake(config) { s -> ctx.status(null, "Starting converter… ${s}s") }
        val groups = if (together) listOf(ctx.inputs.indices.toList()) else ctx.inputs.indices.map { listOf(it) }
        return groups.mapIndexed { n, idx ->
            ctx.progress(
                n.toFloat() / groups.size,
                if (groups.size >
                    1
                ) {
                    "Converting ${n + 1} of ${groups.size}"
                } else {
                    "Converting"
                }
            )
            val name = "${Workspace.baseName(ctx.names[idx.first()])}.$outExt"
            cloud.convert(config, tool, idx.map { ctx.inputs[it] }, ctx.options, ctx.out(name))
        }
    }

    /** Cloud with on-device fallback; returns the files and an optional user-facing note. */
    private suspend fun cloudOr(
        ctx: JobContext,
        tool: String,
        outExt: String,
        config: CloudClient.Config?,
        offline: suspend () -> List<File>
    ): Pair<List<File>, String?> {
        if (config == null) return offline() to null
        return try {
            viaCloud(ctx, config, tool, outExt) to null
        } catch (e: CloudUnavailableException) {
            ctx.progress(null, "Converting")
            offline() to null
        }
    }

    private suspend fun execute(tool: ToolId, ctx: JobContext): Pair<List<File>, String?> = when (tool) {
        ToolId.MERGE_PDF -> PdfOrganize.merge(ctx) to null
        ToolId.SPLIT_PDF -> PdfOrganize.split(ctx) to null
        ToolId.REMOVE_PAGES -> PdfOrganize.remove(ctx) to null
        ToolId.EXTRACT_PAGES -> PdfOrganize.extract(ctx) to null
        ToolId.ORGANIZE_PDF -> PdfOrganize.organize(ctx) to null
        ToolId.ROTATE_PDF -> PdfOrganize.rotate(ctx) to null
        ToolId.PROTECT_PDF -> PdfOrganize.protect(ctx) to null
        ToolId.UNLOCK_PDF -> PdfOrganize.unlock(ctx) to null
        ToolId.REPAIR_PDF -> cloudOr(ctx, "repair", "pdf", cloud.config()) { PdfOrganize.repair(ctx) }

        ToolId.COMPRESS_PDF -> {
            val local = PdfContent.compress(ctx)
            val config = cloudConfig(ctx, "cloud_boost", default = false)
            if (config == null) {
                local to null
            } else {
                try {
                    // Ghostscript pass; keep whichever result is smaller per file.
                    val boosted = viaCloud(ctx, config, "compress", "pdf")
                    local.zip(boosted).map { (a, b) ->
                        if (b.length() in 1 until a.length()) b.also { if (a != b) a.delete() } else a.also { if (a != b) b.delete() }
                    } to null
                } catch (e: CloudUnavailableException) {
                    local to null
                }
            }
        }

        ToolId.OCR_PDF -> {
            val lang = OcrLanguage.entries.firstOrNull { it.tag == ctx.opt("lang", "en") } ?: OcrLanguage.ENGLISH
            if (lang.engine == com.rajdeep.unidoc.core.model.OcrEngine.CLOUD) {
                val config =
                    cloud.config() ?: throw IllegalArgumentException("${lang.englishName} text recognition is coming soon")
                viaCloud(ctx, config, "ocr", "pdf") to null
            } else {
                PdfContent.ocr(ctx, ocr) to null
            }
        }

        ToolId.IMAGE_TO_PDF -> ImageTools.imagesToPdf(ctx) to null
        ToolId.TEXT_TO_PDF -> ImageTools.textToPdf(ctx) to null
        ToolId.WORD_TO_PDF, ToolId.POWERPOINT_TO_PDF, ToolId.EXCEL_TO_PDF ->
            cloudOr(ctx, "office", "pdf", cloudConfig(ctx)) { officeOffline(ctx) }
        ToolId.HTML_TO_PDF -> htmlToPdf(ctx) to null

        ToolId.PDF_TO_IMAGE -> PdfContent.toImages(ctx) to null
        ToolId.PDF_TO_TEXT -> PdfContent.toText(ctx) to null
        ToolId.PDF_TO_WORD -> cloudOr(ctx, "pdf2docx", "docx", cloudConfig(ctx)) { PdfContent.toWord(ctx) }
        ToolId.PDF_TO_EXCEL -> cloudOr(ctx, "pdf2xlsx", "xlsx", cloudConfig(ctx)) { PdfContent.toExcel(ctx) }
        ToolId.PDF_TO_POWERPOINT -> cloudOr(ctx, "pdf2pptx", "pptx", cloudConfig(ctx)) { PdfContent.toPowerPoint(ctx) }
        ToolId.PDF_TO_PDFA -> cloudOr(ctx, "pdfa", "pdf", cloud.config()) { PdfSecure.pdfA(ctx) }

        ToolId.PAGE_NUMBERS -> PdfStamp.pageNumbers(ctx) to null
        ToolId.WATERMARK -> PdfStamp.watermark(ctx) to null
        ToolId.EDIT_PDF -> PdfStamp.addText(ctx) to null
        ToolId.CROP_PDF -> PdfStamp.crop(ctx) to null
        ToolId.SIGN_PDF -> PdfSecure.sign(ctx) to null
        ToolId.REDACT_PDF -> PdfSecure.redact(ctx) to null
        ToolId.COMPARE_PDF -> PdfSecure.compare(ctx) to null

        ToolId.IMAGE_TO_TEXT -> {
            val lang = OcrLanguage.entries.firstOrNull { it.tag == ctx.opt("lang", "en") } ?: OcrLanguage.ENGLISH
            if (lang.engine ==
                com.rajdeep.unidoc.core.model.OcrEngine.CLOUD
            ) {
                imageTextViaCloud(ctx, lang) to null
            } else {
                ImageTools.toText(ctx, ocr) to null
            }
        }
        ToolId.TEXT_TO_IMAGE -> ImageTools.textToImage(ctx) to null
        ToolId.IMAGE_CONVERT -> ImageTools.convert(ctx) to null
        ToolId.IMAGE_COMPRESS -> ImageTools.compress(ctx) to null

        ToolId.SCAN_TO_PDF -> throw IllegalArgumentException("Scan to PDF opens the camera")
    }

    /**
     * Scripts ML Kit can't read on-device (Bengali, Tamil, …): pack the photos into a PDF, run
     * Tesseract OCR on the Space, and pull the recognised text back out.
     */
    private suspend fun imageTextViaCloud(ctx: JobContext, lang: OcrLanguage): List<File> {
        val config =
            cloud.config()
                ?: throw IllegalArgumentException("${lang.englishName} text recognition is coming soon")
        ctx.progress(0.1f, "Preparing images")
        val pdfCtx = JobContext(context, ctx.workspace, ctx.inputs, ctx.names, mapOf("page_size" to "fit", "single" to "true")) { _, _ -> }
        val pdf = ImageTools.imagesToPdf(pdfCtx).single()
        cloud.wake(config) { s -> ctx.status(null, "Starting converter… ${s}s") }
        ctx.progress(0.3f, "Reading text")
        val searchable = cloud.convert(
            config,
            "ocr",
            listOf(pdf),
            mapOf("lang" to lang.tag, "skip_text" to "false"),
            File(ctx.workspace.root, "ocr_result.pdf")
        )
        val text = com.rajdeep.unidoc.core.converter.pdf.PdfSupport.load(searchable).use { doc ->
            com.tom_roush.pdfbox.text.PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }
        pdf.delete()
        searchable.delete()
        require(text.isNotBlank()) { "No text found in the image" }
        return listOf(ctx.out("${ctx.firstBase}.txt").also { it.writeText(text.trim() + "\n") })
    }

    private suspend fun officeOffline(ctx: JobContext): List<File> = ctx.inputs.mapIndexed { i, file ->
        ctx.progress(i.toFloat() / ctx.inputs.size, "Converting ${ctx.names[i]}")
        val ext = OfficeHtml.sniff(file, ctx.names[i].substringAfterLast('.', ""))
        val html = OfficeHtml.toHtml(file, ext)
        HtmlPrinter.print(context, ctx.out("${Workspace.baseName(ctx.names[i])}.pdf"), html = html, landscape = ext.equals("pptx", true))
    }

    private suspend fun htmlToPdf(ctx: JobContext): List<File> {
        val letter = ctx.opt("page_size", "a4") == "letter"
        val url = ctx.opt("url").trim()
        if (url.isNotEmpty()) {
            val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            ctx.progress(null, "Loading page")
            val host = android.net.Uri.parse(normalized).host?.removePrefix("www.") ?: "Web page"
            return listOf(HtmlPrinter.print(context, ctx.out("$host.pdf"), url = normalized, letter = letter))
        }
        require(ctx.inputs.isNotEmpty()) { "Enter a web address or choose an HTML file" }
        return ctx.inputs.mapIndexed { i, file ->
            ctx.progress(i.toFloat() / ctx.inputs.size, "Rendering ${ctx.names[i]}")
            HtmlPrinter.print(
                context,
                ctx.out("${Workspace.baseName(ctx.names[i])}.pdf"),
                html = ImageTools.readText(file),
                baseDir = file.parentFile,
                letter = letter
            )
        }
    }
}
