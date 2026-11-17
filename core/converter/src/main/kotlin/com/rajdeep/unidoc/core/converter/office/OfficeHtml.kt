package com.rajdeep.unidoc.core.converter.office

import android.util.Base64
import android.util.Xml
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

/**
 * Offline fallback renderer for OOXML/CSV: converts the document to clean HTML that the WebView
 * printer turns into a PDF. Keeps text, headings, bold/italic/underline, lists, tables and inline
 * images; complex layout (columns, floating shapes, charts) needs the Cloud (LibreOffice) path.
 */
object OfficeHtml {

    class UnsupportedFormatException(ext: String) :
        IllegalArgumentException("“.$ext” files need Unidoc Cloud (LibreOffice) — turn on Cloud in Settings")

    private const val CSS = """
        @page { margin: 18mm 16mm; }
        body { font-family: 'Noto Sans', sans-serif; font-size: 11pt; line-height: 1.45; color: #15161f; }
        h1 { font-size: 20pt; margin: 0 0 8pt; } h2 { font-size: 16pt; margin: 14pt 0 6pt; } h3 { font-size: 13pt; margin: 12pt 0 4pt; }
        p { margin: 0 0 7pt; } table { border-collapse: collapse; margin: 6pt 0 10pt; width: 100%; font-size: 9.5pt; }
        td, th { border: 0.6pt solid #c9cbd8; padding: 3pt 5pt; vertical-align: top; } th { background: #f0f1f7; }
        img { max-width: 100%; } .slide { page-break-after: always; border: 0.8pt solid #d7d9e4; border-radius: 6pt; padding: 18pt 22pt; min-height: 150mm; }
        .slide h2 { margin-top: 0; } .sheet { page-break-after: always; } li { margin-bottom: 3pt; } .num { text-align: right; }
    """

    /** Real format of [file] from its content (picked files don't always keep their extension). */
    fun sniff(file: File, ext: String): String {
        val known = ext.lowercase()
        if (known in setOf("docx", "xlsx", "pptx", "csv", "txt")) return known
        return runCatching {
            ZipFile(file).use { z ->
                when {
                    z.getEntry("word/document.xml") != null -> "docx"
                    z.getEntry("xl/workbook.xml") != null -> "xlsx"
                    z.getEntry("ppt/presentation.xml") != null -> "pptx"
                    else -> known
                }
            }
        }.getOrDefault(known)
    }

    fun toHtml(file: File, ext: String): String = when (sniff(file, ext)) {
        "docx" -> docx(file)
        "xlsx" -> xlsx(file)
        "pptx" -> pptx(file)
        "csv" -> page(table(csvRows(file.readText()), header = true))
        "txt" -> page("<pre style=\"white-space:pre-wrap;font-family:inherit\">${file.readText().xml()}</pre>")
        else -> throw UnsupportedFormatException(ext)
    }

    private fun page(body: String) =
        "<!doctype html><html><head><meta charset=\"utf-8\"><style>$CSS</style></head><body>$body</body></html>"

    private fun parser(input: InputStream): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(input, "UTF-8")
    }

    private fun relationships(zip: ZipFile, path: String): Map<String, String> {
        val entry = zip.getEntry(path) ?: return emptyMap()
        val map = HashMap<String, String>()
        zip.getInputStream(entry).use { s ->
            val p = parser(s)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                    map[p.getAttributeValue(null, "Id")] = p.getAttributeValue(null, "Target")
                }
            }
        }
        return map
    }

    private fun dataUri(zip: ZipFile, path: String): String? {
        val entry = zip.getEntry(path.removePrefix("/")) ?: return null
        if (entry.size > 8_000_000) return null
        val mime = when (path.substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            else -> return null
        }
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ------------------------------------------------------------------ DOCX

    private fun docx(file: File): String = ZipFile(file).use { zip ->
        val rels = relationships(zip, "word/_rels/document.xml.rels")
        val numbering = HashSet<String>() // numIds that are ordered lists
        zip.getEntry("word/numbering.xml")?.let { e ->
            zip.getInputStream(e).use { s ->
                val p = parser(s)
                val abstractOrdered = HashSet<String>()
                var currentAbstract: String? = null
                var level0 = false
                val numToAbstract = HashMap<String, String>()
                var currentNum: String? = null
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType != XmlPullParser.START_TAG) continue
                    when (p.name) {
                        "w:abstractNum" -> currentAbstract = p.getAttributeValue(null, "w:abstractNumId")
                        "w:lvl" -> level0 = p.getAttributeValue(null, "w:ilvl") == "0"
                        "w:numFmt" -> if (level0 &&
                            p.getAttributeValue(null, "w:val") != "bullet"
                        ) {
                            currentAbstract?.let(abstractOrdered::add)
                        }
                        "w:num" -> currentNum = p.getAttributeValue(null, "w:numId")
                        "w:abstractNumId" -> currentNum?.let { numToAbstract[it] = p.getAttributeValue(null, "w:val") }
                    }
                }
                numToAbstract.forEach { (num, abs) -> if (abs in abstractOrdered) numbering += num }
            }
        }
        val body = StringBuilder()
        val entry = zip.getEntry("word/document.xml") ?: throw IllegalArgumentException("Not a valid Word document")
        zip.getInputStream(entry).use { s ->
            val p = parser(s)
            val para = StringBuilder()
            var style = ""
            var numId: String? = null
            var bold = false
            var italic = false
            var underline = false
            var inRunProps = false
            var openList: String? = null
            var tableDepth = 0
            val cells = ArrayList<String>()
            val rows = ArrayList<List<String>>()
            val tableStack = ArrayList<Pair<ArrayList<List<String>>, ArrayList<String>>>()
            fun closeList() {
                openList?.let { body.append("</$it>") }
                openList = null
            }
            fun emitParagraph() {
                val content = para.toString()
                para.clear()
                if (tableDepth > 0) {
                    cells += (cells.removeLastOrNull() ?: "") + (if (content.isBlank()) "" else "<p>$content</p>")
                    return
                }
                if (numId != null) {
                    val tag = if (numId in numbering) "ol" else "ul"
                    if (openList != tag) {
                        closeList()
                        body.append("<$tag>")
                        openList = tag
                    }
                    body.append("<li>$content</li>")
                    return
                }
                closeList()
                val tag = when {
                    style.equals("Title", true) || style.endsWith("Heading1", true) || style.equals("Heading1", true) -> "h1"
                    style.contains("Heading2", true) || style.equals("Subtitle", true) -> "h2"
                    style.contains("Heading", true) -> "h3"
                    else -> "p"
                }
                body.append(if (content.isBlank() && tag == "p") "<p>&nbsp;</p>" else "<$tag>$content</$tag>")
            }
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "w:p" -> {
                            style = ""
                            numId = null
                        }
                        "w:pStyle" -> style = p.getAttributeValue(null, "w:val").orEmpty()
                        "w:numId" -> numId = p.getAttributeValue(null, "w:val")?.takeIf { it != "0" }
                        "w:r" -> {
                            bold = false
                            italic = false
                            underline = false
                        }
                        "w:rPr" -> inRunProps = true
                        "w:b" -> if (inRunProps) bold = p.getAttributeValue(null, "w:val") != "0"
                        "w:i" -> if (inRunProps) italic = p.getAttributeValue(null, "w:val") != "0"
                        "w:u" -> if (inRunProps) underline = p.getAttributeValue(null, "w:val") != "none"
                        "w:t" -> {
                            var t = p.nextText().xml()
                            if (bold) t = "<b>$t</b>"
                            if (italic) t = "<i>$t</i>"
                            if (underline) t = "<u>$t</u>"
                            para.append(t)
                        }
                        "w:tab" -> para.append("&emsp;")
                        "w:br" -> if (p.getAttributeValue(null, "w:type") ==
                            "page"
                        ) {
                            para.append("<div style=\"page-break-after:always\"></div>")
                        } else {
                            para.append("<br>")
                        }
                        "a:blip" -> p.getAttributeValue(null, "r:embed")?.let { rels[it] }?.let { target ->
                            dataUri(zip, if (target.startsWith("/")) target else "word/$target")?.let { para.append("<img src=\"$it\">") }
                        }
                        "w:tbl" -> {
                            if (tableDepth == 0) closeList()
                            if (tableDepth > 0) tableStack += ArrayList(rows) to ArrayList(cells)
                            rows.clear()
                            cells.clear()
                            tableDepth++
                        }
                        "w:tr" -> cells.clear()
                        "w:tc" -> cells += ""
                    }
                    XmlPullParser.END_TAG -> when (p.name) {
                        "w:rPr" -> inRunProps = false
                        "w:p" -> emitParagraph()
                        "w:tr" -> rows += ArrayList(cells)
                        "w:tbl" -> {
                            val html =
                                "<table>" + rows.joinToString("") { r -> "<tr>" + r.joinToString("") { "<td>$it</td>" } + "</tr>" } +
                                    "</table>"
                            tableDepth--
                            if (tableDepth > 0) {
                                val (r, c) = tableStack.removeAt(tableStack.lastIndex)
                                rows.clear()
                                rows += r
                                cells.clear()
                                cells += c
                                cells += (cells.removeLastOrNull() ?: "") + html
                            } else {
                                body.append(html)
                                rows.clear()
                            }
                        }
                    }
                }
            }
            closeList()
        }
        page(body.toString())
    }

    // ------------------------------------------------------------------ XLSX

    private fun xlsx(file: File): String = ZipFile(file).use { zip ->
        val shared = ArrayList<String>()
        zip.getEntry("xl/sharedStrings.xml")?.let { e ->
            zip.getInputStream(e).use { s ->
                val p = parser(s)
                val sb = StringBuilder()
                var inSi = false
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    when {
                        p.eventType == XmlPullParser.START_TAG && p.name == "si" -> {
                            inSi = true
                            sb.clear()
                        }
                        p.eventType == XmlPullParser.START_TAG && p.name == "t" && inSi -> sb.append(p.nextText())
                        p.eventType == XmlPullParser.END_TAG && p.name == "si" -> {
                            shared += sb.toString()
                            inSi = false
                        }
                    }
                }
            }
        }
        val rels = relationships(zip, "xl/_rels/workbook.xml.rels")
        val sheets = ArrayList<Pair<String, String>>()
        zip.getInputStream(zip.getEntry("xl/workbook.xml") ?: throw IllegalArgumentException("Not a valid Excel workbook")).use { s ->
            val p = parser(s)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                    val target = rels[p.getAttributeValue(null, "r:id")] ?: continue
                    sheets += p.getAttributeValue(null, "name").orEmpty() to (if (target.startsWith("/")) target.drop(1) else "xl/$target")
                }
            }
        }
        val body = StringBuilder()
        sheets.forEach { (name, path) ->
            val entry = zip.getEntry(path) ?: return@forEach
            val grid = ArrayList<MutableList<Pair<String, Boolean>>>()
            zip.getInputStream(entry).use { s ->
                val p = parser(s)
                var row: MutableList<Pair<String, Boolean>>? = null
                var type = ""
                var col = 0
                var value = StringBuilder()
                var inCell = false
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    when (p.eventType) {
                        XmlPullParser.START_TAG -> when (p.name) {
                            "row" -> row = ArrayList<Pair<String, Boolean>>().also { grid += it }
                            "c" -> {
                                type = p.getAttributeValue(null, "t").orEmpty()
                                col = colIndex(p.getAttributeValue(null, "r").orEmpty()).takeIf { it >= 0 } ?: (row?.size ?: 0)
                                value = StringBuilder()
                                inCell = true
                            }
                            "v", "t" -> if (inCell) value.append(p.nextText())
                        }
                        XmlPullParser.END_TAG -> if (p.name == "c" && row != null) {
                            val raw = value.toString()
                            val text = when (type) {
                                "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw.toDoubleOrNull()?.let { d ->
                                    if (d == Math.floor(d) &&
                                        kotlin.math.abs(d) < 1e15
                                    ) {
                                        d.toLong().toString()
                                    } else {
                                        "%.6g".format(d).trimEnd('0').trimEnd('.')
                                    }
                                }
                                    ?: raw
                            }
                            while (row.size < col) row += "" to false
                            row += text to (type != "s" && type != "inlineStr" && type != "str" && raw.toDoubleOrNull() != null)
                            inCell = false
                        }
                    }
                }
            }
            // Drop trailing empty rows/columns.
            while (grid.isNotEmpty() && grid.last().all { it.first.isBlank() }) grid.removeAt(grid.lastIndex)
            val cols = grid.maxOfOrNull { r -> r.indexOfLast { it.first.isNotBlank() } + 1 } ?: 0
            if (cols == 0) return@forEach
            body.append("<div class=\"sheet\"><h2>${name.xml()}</h2><table>")
            grid.take(5000).forEach { r ->
                body.append("<tr>")
                (0 until cols).forEach { c ->
                    val (t, num) = r.getOrNull(c) ?: ("" to false)
                    body.append(if (num) "<td class=\"num\">" else "<td>").append(t.xml()).append("</td>")
                }
                body.append("</tr>")
            }
            body.append("</table></div>")
        }
        require(body.isNotEmpty()) { "The workbook is empty" }
        page(body.toString())
    }

    internal fun colIndex(ref: String): Int {
        var n = 0
        var any = false
        for (c in ref) {
            if (c !in 'A'..'Z') break
            n = n * 26 + (c - 'A' + 1)
            any = true
        }
        return if (any) n - 1 else -1
    }

    // ------------------------------------------------------------------ PPTX

    private fun pptx(file: File): String = ZipFile(file).use { zip ->
        val slides = zip.entries().asSequence().map { it.name }
            .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { it.filter(Char::isDigit).toInt() }
            .toList()
        require(slides.isNotEmpty()) { "Not a valid PowerPoint file" }
        val body = StringBuilder()
        slides.forEach { path ->
            val rels = relationships(zip, path.replace("slides/", "slides/_rels/") + ".rels")
            val paragraphs = ArrayList<Pair<String, Boolean>>() // text, isTitle
            val images = ArrayList<String>()
            zip.getInputStream(zip.getEntry(path)).use { s ->
                val p = parser(s)
                var isTitle = false
                val para = StringBuilder()
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    when (p.eventType) {
                        XmlPullParser.START_TAG -> when (p.name) {
                            "p:sp" -> isTitle = false
                            "p:ph" -> p.getAttributeValue(null, "type")?.let { if (it == "title" || it == "ctrTitle") isTitle = true }
                            "a:t" -> para.append(p.nextText())
                            "a:blip" -> p.getAttributeValue(null, "r:embed")?.let { rels[it] }?.let { t ->
                                val resolved = if (t.startsWith("../")) "ppt/" + t.removePrefix("../") else "ppt/slides/$t"
                                dataUri(zip, resolved)?.let(images::add)
                            }
                        }
                        XmlPullParser.END_TAG -> if (p.name == "a:p") {
                            if (para.isNotBlank()) paragraphs += para.toString() to isTitle
                            para.clear()
                        }
                    }
                }
            }
            body.append("<div class=\"slide\">")
            paragraphs.forEach { (t, title) -> body.append(if (title) "<h2>${t.xml()}</h2>" else "<p>${t.xml()}</p>") }
            images.forEach { body.append("<p><img src=\"$it\"></p>") }
            body.append("</div>")
        }
        page(body.toString())
    }

    // ------------------------------------------------------------------ CSV

    internal fun csvRows(text: String): List<List<String>> {
        val delimiter = listOf(',', ';', '\t').maxBy { d -> text.lineSequence().take(5).sumOf { line -> line.count { it == d } } }
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> {
                    row += cell.toString()
                    cell.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString()
                    cell.clear()
                    rows += row
                    row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private fun table(rows: List<List<String>>, header: Boolean): String {
        val cols = rows.maxOfOrNull { it.size } ?: 0
        return "<table>" + rows.mapIndexed { i, r ->
            val tag = if (header && i == 0) "th" else "td"
            "<tr>" + (0 until cols).joinToString("") { c -> "<$tag>${r.getOrElse(c) { "" }.xml()}</$tag>" } + "</tr>"
        }.joinToString("") + "</table>"
    }
}
