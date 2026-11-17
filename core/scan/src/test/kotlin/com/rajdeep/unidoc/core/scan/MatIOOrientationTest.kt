package com.rajdeep.unidoc.core.scan

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The crop editor and the renderer must see the same upright frame: corners are normalized over
 * it. A camera JPEG is stored sideways with an EXIF orientation tag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MatIOOrientationTest {
    private fun sideways(orientation: Int): File {
        // Sensor frame 400×300; a red block in the sensor's top-left corner.
        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (y in 0 until 60) for (x in 0 until 80) bmp.setPixel(x, y, Color.RED)
        val f = File.createTempFile("cap", ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(f).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return f
    }

    @Test
    fun editorAndRendererShareTheUprightFrame() {
        val f = sideways(ExifInterface.ORIENTATION_ROTATE_90)
        val (w, h) = MatIO.orientedSize(f)
        assertEquals(300 to 400, w to h)
        for (maxSide in listOf(2400, 4000, 200)) {
            val b = MatIO.readBitmap(f, maxSide)
            println("maxSide=$maxSide -> ${b.width}x${b.height}")
            assertEquals(w.toFloat() / h, b.width.toFloat() / b.height, 0.02f)
            // Rotated 90° clockwise: sensor top-left lands at the top-right.
            val px = b.getPixel(b.width - 5, 5)
            println("top-right=${Integer.toHexString(px)} top-left=${Integer.toHexString(b.getPixel(5, 5))}")
            assertEquals(true, Color.red(px) > 200)
            assertEquals(true, Color.green(px) < 80)
        }
    }
}
