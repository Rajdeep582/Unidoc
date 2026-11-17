package com.rajdeep.unidoc.core.converter.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.rajdeep.unidoc.core.converter.JobContext
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDOutputIntent
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.security.auth.x500.X500Principal
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder

/** Sign, redact, compare and PDF/A. */
object PdfSecure {

    // ---------------------------------------------------------------- Sign

    /** Saved hand-drawn signature (shared with the scanner editor): strokes normalized to its box + aspect. */
    fun savedSignature(ctx: JobContext): Pair<List<List<Float>>, Float>? = runCatching {
        val (aspect, body) = File(ctx.android.filesDir, "signature.json").readText().split("\n", limit = 2)
        Json.decodeFromString(ListSerializer(ListSerializer(Float.serializer())), body) to aspect.toFloat()
    }.getOrNull()?.takeIf { it.first.isNotEmpty() }

    /**
     * Draws the saved signature as vector strokes (plus optional name/date line) and, when
     * `certify` is on, adds a cryptographic signature (adbe.pkcs7.detached, ECDSA P-256 key kept
     * in the Android Keystore, self-signed "Unidoc" certificate) so later edits are detectable.
     */
    suspend fun sign(ctx: JobContext): List<File> {
        val signature = savedSignature(ctx)
        val name = ctx.opt("name")
        require(signature != null || name.isNotBlank()) { "Draw a signature first (Scanner → Markup → Sign) or enter your name" }
        val stamped = File(ctx.workspace.root, "signed_visual.pdf")
        PdfSupport.load(ctx.inputs.first()).use { doc ->
            val place = ctx.placement
            val pageIndexes = when {
                ctx.opt("page") == "all" -> (0 until doc.numberOfPages).toList()
                place != null -> listOf(place[0].toInt().coerceIn(0, doc.numberOfPages - 1))
                ctx.opt("page") == "first" -> listOf(0)
                else -> listOf(doc.numberOfPages - 1)
            }
            val font = PdfSupport.font(ctx.android, doc, name)
            val position = ctx.opt("position", "bottom_right")
            val widthFraction = ctx.number("size", 0.28f).coerceIn(0.1f, 0.6f)
            pageIndexes.forEach { i ->
                ctx.progress(0.2f, "Placing signature")
                val page = doc.getPage(i)
                val (w, h) = PdfSupport.displaySize(page)
                val boxW = w * (place?.get(3)?.takeIf { it > 0f } ?: widthFraction)
                val boxH = signature?.let { boxW / it.second } ?: 0f
                val caption = buildList {
                    if (name.isNotBlank()) add(name)
                    if (ctx.flag("date", true)) add(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date()))
                }.joinToString("  ·  ")
                val captionSize = 9f
                val totalH = boxH + if (caption.isNotEmpty()) captionSize * 2f else 0f
                val x = when {
                    place != null -> place[1] * w
                    position.endsWith("left") -> 40f
                    position.endsWith("center") -> (w - boxW) / 2
                    else -> w - 40f - boxW
                }
                val bottom = when {
                    place != null -> (h - place[2] * h - totalH).coerceAtLeast(0f)
                    position.startsWith("top") -> h - 40f - totalH
                    else -> 40f
                }
                PdfSupport.overlay(doc, page).use { cs ->
                    signature?.let { (strokes, _) ->
                        cs.setStrokingColor(0.07f, 0.1f, 0.35f)
                        cs.setLineWidth((boxW * 0.012f).coerceIn(0.8f, 2.4f))
                        cs.setLineCapStyle(1)
                        cs.setLineJoinStyle(1)
                        val top = bottom + totalH
                        strokes.forEach { pts ->
                            if (pts.size < 4) return@forEach
                            cs.moveTo(x + pts[0] * boxW, top - pts[1] * boxH)
                            var k = 2
                            while (k + 1 < pts.size) {
                                cs.lineTo(x + pts[k] * boxW, top - pts[k + 1] * boxH)
                                k += 2
                            }
                            cs.stroke()
                        }
                    }
                    if (caption.isNotEmpty()) {
                        cs.setStrokingColor(0.6f, 0.62f, 0.7f)
                        cs.setLineWidth(0.5f)
                        cs.moveTo(x, bottom + captionSize * 1.6f)
                        cs.lineTo(x + boxW, bottom + captionSize * 1.6f)
                        cs.stroke()
                        if (!PdfSupport.isSimpleLatin(caption)) {
                            val art = PdfSupport.textImage(doc, caption, captionSize, android.graphics.Color.rgb(77, 79, 97))
                            cs.drawImage(art.image, x, bottom, art.width, art.height)
                            return@use
                        }
                        cs.beginText()
                        cs.setFont(font, captionSize)
                        cs.setNonStrokingColor(0.3f, 0.31f, 0.38f)
                        cs.newLineAtOffset(x, bottom + captionSize * 0.3f)
                        cs.showText(PdfSupport.encodable(font, caption))
                        cs.endText()
                    }
                }
            }
            doc.save(stamped)
        }
        val out = ctx.out("${ctx.firstBase} (signed).pdf")
        if (!ctx.flag("certify", true)) {
            if (!stamped.renameTo(out)) stamped.copyTo(out, overwrite = true)
            return listOf(out)
        }
        ctx.progress(0.7f, "Applying digital signature")
        val certified = runCatching { certify(stamped, out, name) }
        if (certified.isFailure) {
            // No hardware keystore / crypto provider on this device: still deliver the signed page.
            out.delete()
            stamped.copyTo(out, overwrite = true)
        }
        stamped.delete()
        return listOf(out)
    }

    private fun certify(stamped: File, out: File, name: String) {
        PdfSupport.load(stamped).use { doc ->
            val (key, cert) = signingKey()
            val sig = PDSignature().apply {
                setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                this.name = name.ifBlank { "Unidoc user" }
                reason = "Signed with Unidoc"
                signDate = Calendar.getInstance()
            }
            val signer = SignatureInterface { content: InputStream ->
                val gen = CMSSignedDataGenerator()
                val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(key)
                gen.addSignerInfoGenerator(
                    JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build()).build(contentSigner, cert)
                )
                gen.addCertificates(JcaCertStore(listOf(cert)))
                gen.generate(CMSProcessableByteArray(content.readBytes()), false).encoded
            }
            doc.addSignature(sig, signer)
            out.outputStream().use { doc.saveIncremental(it) }
        }
    }

    private const val KEY_ALIAS = "unidoc_pdf_signing"

    private fun signingKey(): Pair<PrivateKey, X509Certificate> {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 25) }
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setCertificateSubject(X500Principal("CN=Unidoc user, O=Unidoc"))
                        .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                        .setCertificateNotBefore(start.time)
                        .setCertificateNotAfter(end.time)
                        .build()
                )
                generateKeyPair()
            }
        }
        val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey to entry.certificate as X509Certificate
    }

    // ---------------------------------------------------------------- Redact

    internal val PATTERNS = listOf(
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
        Regex("(?<!\\d)(?:\\+?\\d{1,3}[ -]?)?(?:\\d[ -]?){9,11}\\d(?!\\d)"),
        Regex("(?<!\\d)(?:\\d{4}[ -]?){3}\\d{4}(?!\\d)")
    )

    /** Character runs to black out on one page, in display points (top-left origin). */
    internal fun findHits(text: String, terms: List<String>, patterns: Boolean): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        terms.filter { it.isNotBlank() }.forEach { term ->
            Regex(Regex.escape(term.trim()), RegexOption.IGNORE_CASE).findAll(text).forEach { ranges += it.range }
        }
        if (patterns) PATTERNS.forEach { p -> p.findAll(text).forEach { ranges += it.range } }
        return ranges
    }

    /**
     * True redaction: pages with matches are replaced by a flattened image with black boxes, so
     * the hidden text no longer exists in the file. Pages without matches stay vector.
     */
    suspend fun redact(ctx: JobContext): List<File> {
        val terms = ctx.opt("terms").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val patterns = ctx.flag("patterns", true)
        require(terms.isNotEmpty() || patterns) { "Enter words to redact" }
        val file = ctx.inputs.first()
        var total = 0
        return PdfSupport.load(file).use { doc ->
            val boxesByPage = HashMap<Int, List<FloatArray>>()
            for (p in 0 until doc.numberOfPages) {
                ctx.progress(p.toFloat() / doc.numberOfPages * 0.5f, "Searching page ${p + 1}")
                val collector = PositionStripper().apply {
                    startPage = p + 1
                    endPage = p + 1
                }
                collector.getText(doc)
                val hits = findHits(collector.text.toString(), terms, patterns)
                if (hits.isEmpty()) continue
                total += hits.size
                boxesByPage[p] = hits.flatMap { r -> collector.boxes(r) }
            }
            require(total > 0) { "Nothing matched — no text was redacted" }
            PdfSupport.render(file) { renderer ->
                boxesByPage.entries.forEachIndexed { n, (p, boxes) ->
                    ctx.progress(0.5f + n.toFloat() / boxesByPage.size * 0.45f, "Redacting page ${p + 1}")
                    val old = doc.getPage(p)
                    val (w, h) = PdfSupport.displaySize(old)
                    val bmp = PdfSupport.renderPage(renderer, p, 200)
                    val canvas = Canvas(bmp)
                    val sx = bmp.width / w
                    val sy = bmp.height / h
                    val paint = Paint().apply { color = Color.BLACK }
                    boxes.forEach { b -> canvas.drawRect(b[0] * sx, b[1] * sy, b[2] * sx, b[3] * sy, paint) }
                    val replacement = PDPage(PDRectangle(w, h))
                    val image = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                    bmp.recycle()
                    PDPageContentStream(doc, replacement).use { cs -> cs.drawImage(image, 0f, 0f, w, h) }
                    doc.pages.insertBefore(replacement, old)
                    doc.pages.remove(old)
                }
            }
            doc.documentInformation = com.tom_roush.pdfbox.pdmodel.PDDocumentInformation()
            ctx.out("${ctx.firstBase} (redacted).pdf").also { doc.save(it) }.let(::listOf)
        }
    }

    /** Captures each character's display-space box while stripping. */
    private class PositionStripper : PDFTextStripper() {
        val text = StringBuilder()
        private val positions = ArrayList<TextPosition?>()

        init {
            sortByPosition = true
        }

        override fun writeString(string: String, textPositions: MutableList<TextPosition>) {
            // Keep 1:1 char↔position mapping (ligatures may map one position to many chars).
            textPositions.forEach { tp ->
                val u = tp.unicode ?: return@forEach
                u.forEach { c ->
                    text.append(c)
                    positions += tp
                }
            }
        }

        override fun writeWordSeparator() {
            text.append(' ')
            positions += null
        }

        override fun writeLineSeparator() {
            text.append('\n')
            positions += null
        }

        fun boxes(range: IntRange): List<FloatArray> = range.mapNotNull { positions.getOrNull(it) }.distinct().map { tp ->
            val top = tp.y - tp.height * 1.15f
            floatArrayOf(tp.x - 0.5f, top, tp.x + tp.width + 0.5f, tp.y + tp.height * 0.3f)
        }
    }

    // ---------------------------------------------------------------- Compare

    suspend fun compare(ctx: JobContext): List<File> = if (ctx.opt("mode", "text") == "visual") compareVisual(ctx) else compareText(ctx)

    private suspend fun compareText(ctx: JobContext): List<File> {
        val (a, b) = ctx.inputs.map { f ->
            PdfSupport.load(f).use { PDFTextStripper().apply { sortByPosition = true }.getText(it) }
        }
        ctx.progress(0.4f, "Finding differences")
        val ta = tokens(a)
        val tb = tokens(b)
        val ops = TextDiff.diff(ta, tb)
        val changes = ops.count { it.kind != TextDiff.Kind.SAME }
        ctx.progress(0.7f, "Writing report")
        val out = ctx.out("Comparison - ${ctx.firstBase}.pdf")
        PDDocument().use { doc ->
            ReportWriter(ctx, doc, ctx.names[0], ctx.names[1], changes).write(ops)
            doc.save(out)
        }
        return listOf(out)
    }

    internal fun tokens(text: String): List<String> = Regex("\\S+|\\n").findAll(text.replace(Regex("\\n{2,}"), "\n\n")).map {
        it.value
    }.toList()

    private suspend fun compareVisual(ctx: JobContext): List<File> {
        val out = ctx.out("Visual comparison - ${ctx.firstBase}.pdf")
        PdfSupport.render(ctx.inputs[0]) { ra ->
            PdfSupport.render(ctx.inputs[1]) { rb ->
                PDDocument().use { doc ->
                    val n = maxOf(ra.pageCount, rb.pageCount)
                    for (i in 0 until n) {
                        ctx.progress(i.toFloat() / n, "Comparing page ${i + 1} of $n")
                        val ba = if (i < ra.pageCount) PdfSupport.renderPage(ra, i, 100) else null
                        val bb = if (i < rb.pageCount) PdfSupport.renderPage(rb, i, 100) else null
                        val diff = visualDiff(ba, bb)
                        ba?.recycle()
                        bb?.recycle()
                        val page = PDPage(PDRectangle(diff.width * 0.72f, diff.height * 0.72f))
                        doc.addPage(page)
                        val img = LosslessFactory.createFromImage(doc, diff)
                        PDPageContentStream(doc, page).use { it.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
                        diff.recycle()
                    }
                    doc.save(out)
                }
            }
        }
        return listOf(out)
    }

    /** Faded unchanged content; red = only in first file, green = only in second. */
    internal fun visualDiff(a: Bitmap?, b: Bitmap?): Bitmap {
        val w = maxOf(a?.width ?: 0, b?.width ?: 0)
        val h = maxOf(a?.height ?: 0, b?.height ?: 0)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val ra = IntArray(w)
        val rb = IntArray(w)
        val ro = IntArray(w)
        fun lum(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        for (y in 0 until h) {
            if (a != null &&
                y < a.height
            ) {
                a.getPixels(ra, 0, a.width, 0, y, a.width, 1).also { ra.fill(Color.WHITE, a.width, w) }
            } else {
                ra.fill(Color.WHITE)
            }
            if (b != null &&
                y < b.height
            ) {
                b.getPixels(rb, 0, b.width, 0, y, b.width, 1).also { rb.fill(Color.WHITE, b.width, w) }
            } else {
                rb.fill(Color.WHITE)
            }
            for (x in 0 until w) {
                val la = lum(ra[x])
                val lb = lum(rb[x])
                val inkA = la < 160
                val inkB = lb < 160
                ro[x] = when {
                    inkA && !inkB && lb - la > 60 -> Color.rgb(220, 38, 38)
                    inkB && !inkA && la - lb > 60 -> Color.rgb(22, 163, 74)
                    else -> {
                        val g = 255 - (255 - minOf(la, lb)) / 4
                        Color.rgb(g, g, g)
                    }
                }
            }
            out.setPixels(ro, 0, w, 0, y, w, 1)
        }
        return out
    }

    // ---------------------------------------------------------------- PDF/A

    /**
     * On-device PDF/A best effort: XMP identification, sRGB output intent, metadata sync and no
     * encryption. Full conformance (font embedding, transparency flattening) needs Cloud (Ghostscript).
     */
    suspend fun pdfA(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { doc ->
        ctx.progress(0.3f, "Adding PDF/A metadata")
        val level = ctx.opt("level", "2b")
        val part = level.take(1)
        doc.isAllSecurityToBeRemoved = true
        val docInfo = doc.documentInformation
        val title = docInfo.title ?: ctx.firstBase
        val now = Calendar.getInstance()
        docInfo.title = title
        docInfo.producer = "Unidoc"
        if (docInfo.creationDate == null) docInfo.creationDate = now
        docInfo.modificationDate = now
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = TimeZone.getDefault() }
        val created = iso.format(docInfo.creationDate.time)
        val modified = iso.format(now.time)
        val xmp = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
<rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"><pdfaid:part>$part</pdfaid:part><pdfaid:conformance>B</pdfaid:conformance></rdf:Description>
<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title><rdf:Alt><rdf:li xml:lang="x-default">${xmlEscape(
            title
        )}</rdf:li></rdf:Alt></dc:title></rdf:Description>
<rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/"><xmp:CreateDate>$created</xmp:CreateDate><xmp:ModifyDate>$modified</xmp:ModifyDate><xmp:CreatorTool>Unidoc</xmp:CreatorTool></rdf:Description>
<rdf:Description rdf:about="" xmlns:pdf="http://ns.adobe.com/pdf/1.3/"><pdf:Producer>Unidoc</pdf:Producer></rdf:Description>
</rdf:RDF></x:xmpmeta>
<?xpacket end="w"?>"""
        val metadata = PDMetadata(doc)
        metadata.importXMPMetadata(xmp.toByteArray(Charsets.UTF_8))
        doc.documentCatalog.metadata = metadata
        if (doc.documentCatalog.outputIntents.isEmpty()) {
            ctx.android.assets.open("icc/sRGB-v2-micro.icc").use { icc ->
                val intent = PDOutputIntent(doc, icc).apply {
                    info = "sRGB IEC61966-2.1"
                    outputCondition = "sRGB IEC61966-2.1"
                    outputConditionIdentifier = "sRGB IEC61966-2.1"
                    registryName = "http://www.color.org"
                }
                doc.documentCatalog.addOutputIntent(intent)
            }
        }
        doc.documentCatalog.cosObject.removeItem(COSName.getPDFName("JavaScript"))
        doc.documentCatalog.cosObject.removeItem(COSName.OPEN_ACTION)
        ctx.progress(0.9f, "Saving")
        ctx.out("${ctx.firstBase} (PDF-A).pdf").also { doc.save(it) }.let(::listOf)
    }

    private fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
