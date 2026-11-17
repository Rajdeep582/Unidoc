@file:Suppress("ktlint:standard:max-line-length") // OOXML namespace/part strings

package com.rajdeep.unidoc.core.converter.office

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun String.xml(): String = buildString(length) {
    for (c in this@xml) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> if (c.code >= 0x20 || c == '\n' || c == '\t') append(c)
        }
    }
}

internal fun ZipOutputStream.entry(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

internal fun ZipOutputStream.entry(name: String, file: File) {
    putNextEntry(ZipEntry(name))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}

private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

/** Minimal valid DOCX: paragraphs per page with page breaks between pages. */
object DocxWriter {
    fun write(out: File, pages: List<List<String>>) {
        val body = StringBuilder()
        pages.forEachIndexed { i, paras ->
            paras.forEach { p -> body.append("<w:p><w:r><w:t xml:space=\"preserve\">${p.xml()}</w:t></w:r></w:p>") }
            if (i < pages.lastIndex) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        }
        ZipOutputStream(out.outputStream()).use { z ->
            z.entry(
                "[Content_Types].xml",
                "$XML_HEAD<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                    "</Types>"
            )
            z.entry(
                "_rels/.rels",
                "$XML_HEAD<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                    "</Relationships>"
            )
            z.entry(
                "word/document.xml",
                "$XML_HEAD<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body" +
                    "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>" +
                    "</w:body></w:document>"
            )
        }
    }
}

/** Minimal valid XLSX with inline strings; numbers stored as numbers. */
object XlsxWriter {
    fun write(out: File, sheets: List<Pair<String, List<List<String>>>>) {
        ZipOutputStream(out.outputStream()).use { z ->
            z.entry(
                "[Content_Types].xml",
                "$XML_HEAD<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    sheets.indices.joinToString("") {
                        "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    } + "</Types>"
            )
            z.entry(
                "_rels/.rels",
                "$XML_HEAD<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>"
            )
            z.entry(
                "xl/workbook.xml",
                "$XML_HEAD<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
                    sheets.mapIndexed { i, (name, _) ->
                        "<sheet name=\"${name.take(31).xml()}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
                    }.joinToString("") +
                    "</sheets></workbook>"
            )
            z.entry(
                "xl/_rels/workbook.xml.rels",
                "$XML_HEAD<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    sheets.indices.joinToString("") {
                        "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>"
                    } + "</Relationships>"
            )
            sheets.forEachIndexed { i, (_, rows) ->
                val data = rows.mapIndexed { r, cells ->
                    "<row r=\"${r + 1}\">" + cells.mapIndexed { c, v ->
                        val ref = column(c) + (r + 1)
                        val num = v.replace(",", "").toDoubleOrNull()
                        if (num != null &&
                            v.isNotBlank()
                        ) {
                            "<c r=\"$ref\"><v>$num</v></c>"
                        } else {
                            "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${v.xml()}</t></is></c>"
                        }
                    }.joinToString("") + "</row>"
                }.joinToString("")
                z.entry(
                    "xl/worksheets/sheet${i + 1}.xml",
                    "$XML_HEAD<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>$data</sheetData></worksheet>"
                )
            }
        }
    }

    fun column(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }
}

/** Minimal valid PPTX: one full-bleed picture per slide. */
object PptxWriter {
    class Slide(val image: File, val width: Int, val height: Int)

    private const val EMU_PER_PX = 9525L

    fun write(out: File, slides: List<Slide>) {
        require(slides.isNotEmpty())
        // Slide size from the first page (fit 10 inch long side).
        val first = slides.first()
        val longEmu = 9_144_000L
        val cx = if (first.width >= first.height) longEmu else longEmu * first.width / first.height
        val cy = if (first.width >= first.height) longEmu * first.height / first.width else longEmu
        val ns = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
            "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
        ZipOutputStream(out.outputStream()).use { z ->
            z.entry(
                "[Content_Types].xml",
                "$XML_HEAD<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>" +
                    "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>" +
                    "<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>" +
                    "<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>" +
                    "<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>" +
                    slides.indices.joinToString("") {
                        "<Override PartName=\"/ppt/slides/slide${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>"
                    } + "</Types>"
            )
            z.entry(
                "_rels/.rels",
                "$XML_HEAD<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/></Relationships>"
            )
            z.entry(
                "ppt/presentation.xml",
                "$XML_HEAD<p:presentation $ns><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>" +
                    slides.indices.joinToString("") { "<p:sldId id=\"${256 + it}\" r:id=\"rId${it + 2}\"/>" } +
                    "</p:sldIdLst><p:sldSz cx=\"$cx\" cy=\"$cy\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>"
            )
            z.entry(
                "ppt/_rels/presentation.xml.rels",
                "$XML_HEAD<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>" +
                    slides.indices.joinToString("") {
                        "<Relationship Id=\"rId${it + 2}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide${it + 1}.xml\"/>"
                    } +
                    "<Relationship Id=\"rId${slides.size + 2}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>" +
                    "</Relationships>"
            )
            val emptyTree = "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>"
            z.entry(
                "ppt/slideMasters/slideMaster1.xml",
                "$XML_HEAD<p:sldMaster $ns>$emptyTree<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" " +
                    "accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
                    "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst></p:sldMaster>"
            )
            z.entry(
                "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                "$XML_HEAD<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
                    "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/></Relationships>"
            )
            z.entry("ppt/slideLayouts/slideLayout1.xml", "$XML_HEAD<p:sldLayout $ns type=\"blank\">$emptyTree</p:sldLayout>")
            z.entry(
                "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                "$XML_HEAD<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>"
            )
            z.entry("ppt/theme/theme1.xml", THEME)
            slides.forEachIndexed { i, s ->
                // Fit picture inside the slide, centered.
                val scale = minOf(cx.toDouble() / (s.width * EMU_PER_PX), cy.toDouble() / (s.height * EMU_PER_PX))
                val w = (s.width * EMU_PER_PX * scale).toLong()
                val h = (s.height * EMU_PER_PX * scale).toLong()
                val x = (cx - w) / 2
                val y = (cy - h) / 2
                z.entry(
                    "ppt/slides/slide${i + 1}.xml",
                    "$XML_HEAD<p:sld $ns><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>" +
                        "<p:pic><p:nvPicPr><p:cNvPr id=\"2\" name=\"Page ${i + 1}\"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>" +
                        "<p:blipFill><a:blip r:embed=\"rId2\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                        "<p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$w\" cy=\"$h\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>" +
                        "</p:spTree></p:cSld></p:sld>"
                )
                z.entry(
                    "ppt/slides/_rels/slide${i + 1}.xml.rels",
                    "$XML_HEAD<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
                        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image${i + 1}.jpg\"/></Relationships>"
                )
                z.entry("ppt/media/image${i + 1}.jpg", s.image)
            }
        }
    }

    private val THEME =
        "$XML_HEAD<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Unidoc\"><a:themeElements>" +
            "<a:clrScheme name=\"Unidoc\"><a:dk1><a:srgbClr val=\"000000\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1>" +
            "<a:dk2><a:srgbClr val=\"1F2033\"/></a:dk2><a:lt2><a:srgbClr val=\"F7F7FB\"/></a:lt2><a:accent1><a:srgbClr val=\"4B3BFF\"/></a:accent1>" +
            "<a:accent2><a:srgbClr val=\"8B3DFF\"/></a:accent2><a:accent3><a:srgbClr val=\"22D3EE\"/></a:accent3><a:accent4><a:srgbClr val=\"12A671\"/></a:accent4>" +
            "<a:accent5><a:srgbClr val=\"E59A0B\"/></a:accent5><a:accent6><a:srgbClr val=\"D93A4A\"/></a:accent6><a:hlink><a:srgbClr val=\"4B3BFF\"/></a:hlink>" +
            "<a:folHlink><a:srgbClr val=\"8B3DFF\"/></a:folHlink></a:clrScheme><a:fontScheme name=\"Unidoc\"><a:majorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>" +
            "<a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>" +
            "<a:fmtScheme name=\"Unidoc\"><a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst>" +
            "<a:lnStyleLst><a:ln w=\"6350\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln><a:ln w=\"12700\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln><a:ln w=\"19050\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln></a:lnStyleLst>" +
            "<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>" +
            "<a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:bgFillStyleLst>" +
            "</a:fmtScheme></a:themeElements></a:theme>"
}
