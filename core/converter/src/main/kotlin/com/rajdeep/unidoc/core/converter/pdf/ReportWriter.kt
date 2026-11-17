package com.rajdeep.unidoc.core.converter.pdf

import com.rajdeep.unidoc.core.converter.JobContext
import com.tom_roush.pdfbox.pdmodel.PDDocument

/** Writes the Compare PDF report: summary header then the merged text with marked changes. */
internal class ReportWriter(
    private val ctx: JobContext,
    private val doc: PDDocument,
    private val first: String,
    private val second: String,
    private val changes: Int
) {
    fun write(ops: List<TextDiff.Op>) {
        val font = PdfSupport.font(ctx.android, doc, ops.joinToString(" ") { it.token }.take(4000))
        val flow = FlowWriter(doc, font, 10f)
        flow.heading("Comparison report", 18f)
        flow.word("Original:", FlowWriter.Style(color = floatArrayOf(0.4f, 0.4f, 0.48f)))
        flow.word(first)
        flow.newLine()
        flow.word("Changed:", FlowWriter.Style(color = floatArrayOf(0.4f, 0.4f, 0.48f)))
        flow.word(second)
        flow.newLine()
        val added = ops.count { it.kind == TextDiff.Kind.ADDED && it.token != "\n" }
        val removed = ops.count { it.kind == TextDiff.Kind.REMOVED && it.token != "\n" }
        flow.word(if (changes == 0) "No text differences found." else "$added words added · $removed words removed")
        flow.newLine(10f)
        val removedStyle = FlowWriter.Style(
            highlight = floatArrayOf(1f, 0.86f, 0.86f),
            strike = true,
            color = floatArrayOf(0.7f, 0.1f, 0.1f)
        )
        val addedStyle = FlowWriter.Style(highlight = floatArrayOf(0.84f, 0.96f, 0.86f), color = floatArrayOf(0.05f, 0.45f, 0.2f))
        ops.forEach { op ->
            when (op.kind) {
                TextDiff.Kind.SAME -> flow.word(op.token)
                TextDiff.Kind.ADDED -> if (op.token != "\n") flow.word(op.token, addedStyle)
                TextDiff.Kind.REMOVED -> if (op.token != "\n") flow.word(op.token, removedStyle)
            }
        }
        flow.close()
    }
}
