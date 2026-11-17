package com.rajdeep.unidoc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.scanner.editor.CropScreen
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, sdk = [35])
class ScannerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** A "photo": tilted white page with text lines on a wooden desk. */
    private fun photo(): Bitmap {
        val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF8A6A45.toInt())
        val grain = Paint().apply {
            color = 0xFF7A5B39.toInt()
            strokeWidth = 6f
        }
        for (y in 0 until 1200 step 37) c.drawLine(0f, y.toFloat(), 900f, y + 20f, grain)
        val page = Path().apply {
            moveTo(150f, 170f)
            lineTo(760f, 120f)
            lineTo(800f, 1060f)
            lineTo(110f, 1090f)
            close()
        }
        c.drawPath(
            page,
            Paint().apply {
                color = 0xFFF7F6F1.toInt()
                isAntiAlias = true
            }
        )
        val ink = Paint().apply {
            color = 0xFF2A2A2A.toInt()
            strokeWidth = 9f
        }
        for (i in 0 until 22) {
            val y = 260f + i * 34
            c.drawLine(200f, y - i * 1.2f, 640f + (i % 3) * 30, y - 4f - i * 1.2f, ink)
        }
        return bmp
    }

    @Test
    fun cropEditorShowsPhotoAndDragsWithLoupe() {
        val image = photo().asImageBitmap()
        var committed: List<Offset>? = null
        composeRule.setContent {
            UnidocTheme {
                var corners by remember {
                    mutableStateOf(listOf(Offset(0.17f, 0.14f), Offset(0.84f, 0.10f), Offset(0.89f, 0.88f), Offset(0.12f, 0.91f)))
                }
                CropScreen(
                    corners = corners,
                    onCornersChange = {
                        corners = it
                        committed = it
                    },
                    onAutoDetect = {},
                    onNoCrop = {},
                    onDone = {},
                    onBack = {},
                    imageAspectRatio = 0.75f,
                    image = image
                )
            }
        }
        captureScreenRoboImage("src/test/screenshots/scanner_crop_idle.png")
        // Find the image area: the canvas fills the aspect box; drag the TL corner a bit and hold.
        val root = composeRule.onRoot()
        root.performTouchInput {
            // TL corner of the outline in root coordinates (image box is centred; probe by geometry).
            val boxW = width - 2 * 32.dp.toPx()
            val boxH = boxW / 0.75f
            val top = (height - boxH) / 2f
            val left = 32.dp.toPx()
            val start = Offset(left + boxW * 0.17f, top + boxH * 0.14f)
            down(start)
            moveTo(start + Offset(-20f, -15f))
            moveTo(start + Offset(-40f, -30f))
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/scanner_crop_dragging.png")
        root.performTouchInput { up() }
        composeRule.waitForIdle()
        val c = committed
        assertTrue("drag committed", c != null)
        assertNotEquals(0.17f, c!![0].x)
    }

    private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
}
