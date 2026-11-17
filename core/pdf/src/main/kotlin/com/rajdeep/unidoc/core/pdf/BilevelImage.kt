package com.rajdeep.unidoc.core.pdf

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import org.opencv.core.Mat

/** Packs a 0/255 single-channel Mat into 1-bit rows (MSB first, 1 = white) and deflates it. */
object BilevelImage {
    fun packAndDeflate(bin: Mat): ByteArray {
        val w = bin.cols()
        val h = bin.rows()
        val data = ByteArray(w * h)
        bin.get(0, 0, data)
        return packAndDeflate(data, w, h)
    }

    /** [gray] is row-major, one byte per pixel; ≥128 counts as white. */
    fun packAndDeflate(gray: ByteArray, w: Int, h: Int): ByteArray {
        val row = ByteArray(w)
        val rowBytes = (w + 7) / 8
        val packed = ByteArray(rowBytes * h)
        for (y in 0 until h) {
            System.arraycopy(gray, y * w, row, 0, w)
            val base = y * rowBytes
            for (x in 0 until w) {
                if (row[x].toInt() and 0xFF >= 128) {
                    val i = base + (x shr 3)
                    packed[i] = (packed[i].toInt() or (0x80 ushr (x and 7))).toByte()
                }
            }
        }
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(packed)
        deflater.finish()
        val out = ByteArrayOutputStream(packed.size / 8)
        val buf = ByteArray(64 * 1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return out.toByteArray()
    }
}
