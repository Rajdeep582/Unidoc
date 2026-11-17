package com.rajdeep.unidoc.core.converter.pdf

import com.rajdeep.unidoc.core.converter.JobContext
import com.rajdeep.unidoc.core.converter.PageRanges
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import java.util.UUID

/** Page-structure tools: merge, split, remove, extract, organize, rotate, protect, unlock. */
object PdfOrganize {

    suspend fun merge(ctx: JobContext): List<File> {
        val out = ctx.out("${ctx.firstBase} (merged).pdf")
        val merger = PDFMergerUtility()
        val bookmarks = ctx.flag("bookmarks", true)
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { dest ->
            val items = ArrayList<Pair<String, Int>>()
            ctx.inputs.forEachIndexed { i, f ->
                ctx.progress(i.toFloat() / ctx.inputs.size, "Adding ${ctx.names[i]}")
                PdfSupport.load(f).use { src ->
                    items += ctx.names[i].substringBeforeLast('.') to dest.numberOfPages
                    merger.appendDocument(dest, src)
                }
            }
            if (bookmarks) {
                val outline = PDDocumentOutline()
                items.forEach { (title, start) ->
                    if (start < dest.numberOfPages) {
                        outline.addLast(
                            PDOutlineItem().apply {
                                this.title = title
                                destination = PDPageFitDestination().apply { page = dest.getPage(start) }
                            }
                        )
                    }
                }
                dest.documentCatalog.documentOutline = outline
            }
            ctx.progress(0.95f, "Saving")
            dest.save(out)
        }
        return listOf(out)
    }

    suspend fun split(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { src ->
        val n = src.numberOfPages
        val groups: List<List<Int>> = when (ctx.opt("mode", "ranges")) {
            "each" -> (0 until n).map { listOf(it) }
            "every" -> {
                val size =
                    ctx.opt("ranges", "1").trim().toIntOrNull()?.coerceAtLeast(1)
                        ?: throw IllegalArgumentException("Enter how many pages per file")
                (0 until n).chunked(size)
            }
            else -> {
                val spec = ctx.opt("ranges")
                require(spec.isNotBlank()) { "Enter page ranges, e.g. 1-3, 4-6" }
                spec.split(',').filter { it.isNotBlank() }.map { PageRanges.parse(it, n) }
            }
        }
        groups.mapIndexed { i, pages ->
            ctx.progress(i.toFloat() / groups.size, "Creating part ${i + 1} of ${groups.size}")
            val label = if (pages.size == 1) "p${pages[0] + 1}" else "p${pages.first() + 1}-${pages.last() + 1}"
            val out = ctx.out("${ctx.firstBase} ($label).pdf")
            writeSubset(src, pages, out)
            out
        }
    }

    suspend fun remove(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { src ->
        val drop = PageRanges.parse(ctx.opt("pages"), src.numberOfPages).toSet()
        require(drop.size < src.numberOfPages) { "That would remove every page" }
        ctx.progress(0.5f, "Removing ${drop.size} pages")
        val out = ctx.out("${ctx.firstBase} (edited).pdf")
        drop.sortedDescending().forEach { src.removePage(it) }
        src.save(out)
        listOf(out)
    }

    suspend fun extract(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { src ->
        val pages = PageRanges.parse(ctx.opt("pages"), src.numberOfPages)
        ctx.progress(0.5f, "Extracting ${pages.size} pages")
        val out = ctx.out("${ctx.firstBase} (extract).pdf")
        writeSubset(src, pages, out)
        listOf(out)
    }

    /** New order ("3,1,2,4-") plus optional per-page rotations ("2:90, 5:180"). */
    suspend fun organize(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { src ->
        val order = PageRanges.parse(ctx.opt("order"), src.numberOfPages)
        val rotations = ctx.opt("rotate").split(',').mapNotNull { t ->
            val parts = t.split(':').map { it.trim() }
            if (parts.size == 2) (parts[0].toIntOrNull()?.minus(1) ?: return@mapNotNull null) to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap()
        ctx.progress(0.5f, "Rearranging pages")
        val out = ctx.out("${ctx.firstBase} (organized).pdf")
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { dest ->
            order.forEach { i ->
                val page = dest.importPage(src.getPage(i))
                rotations[i]?.let { page.rotation = ((page.rotation + it) % 360 + 360) % 360 }
            }
            dest.save(out)
        }
        listOf(out)
    }

    suspend fun rotate(ctx: JobContext): List<File> {
        val angle = ctx.opt("angle", "90").toInt()
        return ctx.inputs.mapIndexed { i, f ->
            ctx.progress(i.toFloat() / ctx.inputs.size, "Rotating ${ctx.names[i]}")
            PdfSupport.load(f).use { doc ->
                PageRanges.parse(ctx.opt("pages"), doc.numberOfPages).toSet().forEach { p ->
                    val page = doc.getPage(p)
                    page.rotation = ((page.rotation + angle) % 360 + 360) % 360
                }
                ctx.out("${ctx.names[i].substringBeforeLast('.')} (rotated).pdf").also { doc.save(it) }
            }
        }
    }

    suspend fun protect(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { doc ->
        val password = ctx.opt("password")
        require(password.length >= 4) { "Use a password of at least 4 characters" }
        ctx.progress(0.5f, "Encrypting with AES-256")
        val perms = AccessPermission().apply {
            setCanPrint(ctx.flag("allow_print", true))
            setCanExtractContent(ctx.flag("allow_copy", false))
            setCanModify(false)
        }
        doc.protect(StandardProtectionPolicy(UUID.randomUUID().toString(), password, perms).apply { encryptionKeyLength = 256 })
        ctx.out("${ctx.firstBase} (protected).pdf").also { doc.save(it) }.let(::listOf)
    }

    suspend fun unlock(ctx: JobContext): List<File> {
        val password = ctx.opt("password")
        val doc = try {
            PdfSupport.load(ctx.inputs.first(), password)
        } catch (e: PdfPasswordException) {
            throw IllegalArgumentException("Wrong password")
        }
        return doc.use {
            ctx.progress(0.5f, "Removing protection")
            it.isAllSecurityToBeRemoved = true
            ctx.out("${ctx.firstBase} (unlocked).pdf").also { f -> it.save(f) }.let(::listOf)
        }
    }

    /** Lenient re-parse + full rewrite rebuilds the cross-reference table and drops broken objects. */
    suspend fun repair(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { doc ->
        ctx.progress(0.6f, "Rebuilding structure")
        ctx.out("${ctx.firstBase} (repaired).pdf").also { doc.save(it) }.let(::listOf)
    }

    private fun writeSubset(src: PDDocument, pages: List<Int>, out: File) {
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { dest ->
            pages.forEach { dest.importPage(src.getPage(it)) }
            dest.save(out)
        }
    }
}
