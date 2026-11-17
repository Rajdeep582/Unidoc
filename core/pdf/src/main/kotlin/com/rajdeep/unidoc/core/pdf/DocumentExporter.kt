package com.rajdeep.unidoc.core.pdf

import android.content.Context
import com.rajdeep.unidoc.core.common.di.Dispatcher
import com.rajdeep.unidoc.core.common.di.UnidocDispatchers
import com.rajdeep.unidoc.core.imaging.ImageEncoder
import com.rajdeep.unidoc.core.imaging.PageKind
import com.rajdeep.unidoc.core.model.ExportFormat
import com.rajdeep.unidoc.core.model.ExportOptions
import com.rajdeep.unidoc.core.model.QualityPreset
import com.rajdeep.unidoc.core.scan.OpenCv
import com.rajdeep.unidoc.core.scan.ScanRepository
import com.rajdeep.unidoc.core.scan.ScanSession
import com.rajdeep.unidoc.core.scan.ocr.OcrResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs

data class ExportProgress(val done: Int, val total: Int, val stage: String)

/**
 * Exports a scan session to PDF (image per page, 1-bit Flate for text pages, DCT for the rest,
 * optional invisible OCR text layer and AES-256 password) or to JPG/PNG files.
 */
@Singleton
class DocumentExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scans: ScanRepository,
    @param:Dispatcher(UnidocDispatchers.Default) private val cpu: CoroutineDispatcher
) {
    private val exportDir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    init {
        PDFBoxResourceLoader.init(context)
    }

    /** Returns the exported files (one PDF, or one image per page). */
    suspend fun export(sessionId: String, fileName: String, options: ExportOptions, onProgress: (ExportProgress) -> Unit = {}): List<File> =
        withContext(cpu) {
            OpenCv.ensureLoaded()
            val session = scans.observe(sessionId).value ?: error("Document not found")
            require(session.pages.isNotEmpty()) { "Nothing to export" }
            require(session.pages.none { it.isRendering }) { "Pages are still processing — try again in a moment" }
            val base = sanitize(fileName.ifBlank { session.title })
            val out = File(exportDir, UUID.randomUUID().toString().take(8)).apply { mkdirs() }
            when (options.format) {
                ExportFormat.PDF -> listOf(writePdf(session, File(out, "$base.pdf"), options, onProgress))
                ExportFormat.JPG, ExportFormat.PNG -> writeImages(session, out, base, options, onProgress)
            }
        }

    private suspend fun writePdf(session: ScanSession, file: File, options: ExportOptions, onProgress: (ExportProgress) -> Unit): File {
        val total = session.pages.size
        val ocr: Map<String, OcrResult> = if (options.searchable) {
            session.pages.mapIndexed { i, p ->
                onProgress(ExportProgress(i, total, "Recognizing text · page ${i + 1} of $total"))
                coroutineContext.ensureActive()
                p.id to runCatching { scans.recognizeText(session.id, p.id) }.getOrNull()
            }.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
        } else {
            emptyMap()
        }
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val textLayer = if (ocr.isNotEmpty()) PdfTextLayer(context, doc) else null
            session.pages.forEachIndexed { i, page ->
                coroutineContext.ensureActive()
                onProgress(ExportProgress(i, total, "Building page ${i + 1} of $total"))
                val raster = PageRasterizer.rasterize(scans.file(session.id, page.processed!!), page)
                try {
                    val image = encodeForPdf(doc, raster, options.quality)
                    val layout = PageGeometry.layout(
                        image.width,
                        image.height,
                        options.quality.dpi,
                        options.pageSize,
                        options.orientation,
                        options.margin
                    )
                    val pdPage = PDPage(PDRectangle(layout.pageWidth, layout.pageHeight))
                    doc.addPage(pdPage)
                    PDPageContentStream(doc, pdPage).use { cs ->
                        cs.drawImage(image, layout.image.x, layout.image.y, layout.image.width, layout.image.height)
                        val result = ocr[page.id]
                        if (textLayer != null && result != null) textLayer.write(cs, result, layout.image)
                    }
                } finally {
                    raster.release()
                }
            }
            doc.documentInformation.apply {
                title = session.title
                creator = "Unidoc"
                producer = "Unidoc"
            }
            options.password?.takeIf { it.isNotEmpty() }?.let { pwd ->
                val policy = StandardProtectionPolicy(UUID.randomUUID().toString(), pwd, AccessPermission())
                policy.encryptionKeyLength = 256
                doc.protect(policy)
            }
            onProgress(ExportProgress(total, total, "Saving"))
            doc.save(file)
        }
        return file
    }

    private fun encodeForPdf(doc: PDDocument, raster: RasterPage, preset: QualityPreset): PDImageXObject {
        if (raster.kind == PageKind.TEXT_BW) {
            val bin = ImageEncoder.bilevel(raster.mat, preset)
            try {
                val data = BilevelImage.packAndDeflate(bin)
                return PDImageXObject(
                    doc,
                    ByteArrayInputStream(data),
                    COSName.FLATE_DECODE,
                    bin.cols(),
                    bin.rows(),
                    1,
                    PDDeviceGray.INSTANCE
                )
            } finally {
                bin.release()
            }
        }
        val enc = ImageEncoder.encode(raster.mat, preset, raster.kind)
        val space = if (raster.kind == PageKind.GRAY) PDDeviceGray.INSTANCE else PDDeviceRGB.INSTANCE
        return PDImageXObject(doc, ByteArrayInputStream(enc.bytes), COSName.DCT_DECODE, enc.width, enc.height, 8, space)
    }

    private suspend fun writeImages(
        session: ScanSession,
        dir: File,
        base: String,
        options: ExportOptions,
        onProgress: (ExportProgress) -> Unit
    ): List<File> {
        val total = session.pages.size
        return session.pages.mapIndexed { i, page ->
            coroutineContext.ensureActive()
            onProgress(ExportProgress(i, total, "Saving page ${i + 1} of $total"))
            val raster = PageRasterizer.rasterize(scans.file(session.id, page.processed!!), page)
            try {
                val suffix = if (total > 1) " - ${(i + 1).toString().padStart(2, '0')}" else ""
                if (options.format == ExportFormat.JPG) {
                    val kind = if (raster.kind == PageKind.TEXT_BW) PageKind.GRAY else raster.kind
                    File(dir, "$base$suffix.jpg").apply { writeBytes(ImageEncoder.encode(raster.mat, options.quality, kind).bytes) }
                } else {
                    File(dir, "$base$suffix.png").apply { writeBytes(encodePng(raster, options.quality)) }
                }
            } finally {
                raster.release()
            }
        }
    }

    private fun encodePng(raster: RasterPage, preset: QualityPreset): ByteArray {
        if (raster.kind == PageKind.TEXT_BW) return ImageEncoder.encode(raster.mat, preset, PageKind.TEXT_BW).bytes
        val bytes = MatOfByte()
        val resized = org.opencv.core.Mat()
        val target = ImageEncoder.targetLongSide(preset)
        val scale = minOf(1.0, target.toDouble() / maxOf(raster.mat.cols(), raster.mat.rows()))
        org.opencv.imgproc.Imgproc.resize(
            raster.mat,
            resized,
            org.opencv.core.Size(raster.mat.cols() * scale, raster.mat.rows() * scale),
            0.0,
            0.0,
            org.opencv.imgproc.Imgproc.INTER_AREA
        )
        Imgcodecs.imencode(".png", resized, bytes, MatOfInt(Imgcodecs.IMWRITE_PNG_COMPRESSION, 6))
        resized.release()
        return bytes.toArray().also { bytes.release() }
    }

    /**
     * Estimated PDF size per preset: encodes up to three evenly spaced pages exactly as export
     * would and extrapolates. Text layer/fonts add a small fixed amount.
     */
    suspend fun estimatePdfSizes(sessionId: String, searchable: Boolean): Map<QualityPreset, Long> = withContext(cpu) {
        OpenCv.ensureLoaded()
        val session = scans.observe(sessionId).value ?: return@withContext emptyMap()
        val pages = session.pages.filter { it.processed != null }
        if (pages.isEmpty()) return@withContext emptyMap()
        val sample = if (pages.size <= 3) pages else listOf(pages.first(), pages[pages.size / 2], pages.last())
        val rasters = sample.map { PageRasterizer.rasterize(scans.file(sessionId, it.processed!!), it) }
        try {
            QualityPreset.entries.associateWith { preset ->
                coroutineContext.ensureActive()
                val avg = rasters.map { r ->
                    if (r.kind == PageKind.TEXT_BW) {
                        val bin = ImageEncoder.bilevel(r.mat, preset)
                        BilevelImage.packAndDeflate(bin).size.toLong().also { bin.release() }
                    } else {
                        ImageEncoder.encode(r.mat, preset, r.kind).bytes.size.toLong()
                    }
                }.average()
                (avg * session.pages.size + session.pages.size * 1_200 + 2_000 + if (searchable) 45_000 else 0).toLong()
            }
        } finally {
            rasters.forEach { it.release() }
        }
    }

    fun clearOldExports() {
        exportDir.listFiles()?.filter {
            System.currentTimeMillis() - it.lastModified() > 24 * 3_600_000L
        }?.forEach { it.deleteRecursively() }
    }

    companion object {
        fun sanitize(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().take(80).ifEmpty { "Unidoc scan" }
    }
}
