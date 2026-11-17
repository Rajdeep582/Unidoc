package com.rajdeep.unidoc.feature.scanner.editor

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {
    private val w = 600f
    private val h = 800f
    private val rect = listOf(Offset(100f, 100f), Offset(500f, 100f), Offset(500f, 700f), Offset(100f, 700f))

    @Test
    fun picksCornerOverNearbySideHandle() {
        // Tiny outline: corner and side handle are close; corner should win unless clearly farther.
        val small = listOf(Offset(100f, 100f), Offset(160f, 100f), Offset(160f, 160f), Offset(100f, 160f))
        assertEquals(0, CropGeometry.pick(small, Offset(108f, 104f), 60f))
        assertEquals(4, CropGeometry.pick(rect, Offset(300f, 110f), 60f))
        assertEquals(CropGeometry.NONE, CropGeometry.pick(rect, Offset(300f, 400f), 60f))
    }

    @Test
    fun cornerMovesAndClampsToImage() {
        val moved = CropGeometry.move(rect, 2, Offset(900f, 900f), w, h)!!
        assertEquals(Offset(600f, 800f), moved[2])
        assertEquals(rect[0], moved[0])
    }

    @Test
    fun sideHandleSlidesWholeSideAlongNormal() {
        // Drag the top side's handle down by 50 and sideways by 80: only the normal part counts.
        val moved = CropGeometry.move(rect, 4, Offset(380f, 150f), w, h)!!
        assertEquals(Offset(100f, 150f), moved[0])
        assertEquals(Offset(500f, 150f), moved[1])
        assertEquals(rect[2], moved[2])
        // Sliding out of the image stops at the border.
        val out = CropGeometry.move(rect, 4, Offset(300f, -300f), w, h)!!
        assertEquals(0f, out[0].y, 0.01f)
    }

    @Test
    fun refusesCrossedOrCollapsedOutlines() {
        // TL dragged past TR → self-crossing.
        assertNull(CropGeometry.move(rect, 0, Offset(560f, 120f), w, h))
        // Right side pushed onto the left side → collapsed.
        assertNull(CropGeometry.move(rect, 5, Offset(102f, 400f), w, h))
        assertNotNull(CropGeometry.move(rect, 0, Offset(150f, 140f), w, h))
    }

    @Test
    fun oddStartingOutlineStillEditable() {
        val twisted = listOf(rect[1], rect[0], rect[2], rect[3])
        assertTrue(!CropGeometry.isUsable(twisted, 10f))
        assertNotNull(CropGeometry.move(twisted, 0, Offset(520f, 90f), w, h))
    }
}
