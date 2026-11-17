package com.rajdeep.unidoc.core.scan

import androidx.test.core.app.ApplicationProvider
import com.rajdeep.unidoc.core.imaging.Pt
import com.rajdeep.unidoc.core.imaging.Quad
import com.rajdeep.unidoc.core.model.ScanFilter
import com.rajdeep.unidoc.core.model.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanSessionStoreTest {
    private fun store() = ScanSessionStore(ApplicationProvider.getApplicationContext())

    @Test
    fun persistsAcrossInstances() {
        val a = store()
        val id = a.newId()
        a.create(ScanSession(id, 1L, ScanMode.BOOK, "Book"))
        a.update(id) { it.copy(pages = listOf(SessionPage("p1", "o_1.jpg", filter = ScanFilter.BLACK_WHITE, region = PageRegion.LEFT))) }
        // A fresh store (e.g. after process death) reads the same state from disk.
        val restored = store().get(id)!!
        assertEquals(ScanMode.BOOK, restored.mode)
        assertEquals(PageRegion.LEFT, restored.pages.single().region)
        assertEquals(ScanFilter.BLACK_WHITE, restored.pages.single().filter)
    }

    @Test
    fun observeEmitsUpdatesAndDeletion() {
        val s = store()
        val id = s.newId()
        s.create(ScanSession(id, 1L, ScanMode.DOCUMENT, "Scan"))
        val flow = s.observe(id)
        s.update(id) { it.copy(title = "Renamed") }
        assertEquals("Renamed", flow.value?.title)
        s.delete(id)
        assertNull(flow.value)
        assertFalse(s.dir(id).listFiles().orEmpty().any { it.name == "session.json" })
    }

    @Test
    fun pruneRemovesUnreferencedFiles() {
        val s = store()
        val id = s.newId()
        s.create(ScanSession(id, 1L, ScanMode.DOCUMENT, "Scan", listOf(SessionPage("p", "o_a.jpg", processed = "p_p_v2.jpg"))))
        listOf("o_a.jpg", "p_p_v1.jpg", "p_p_v2.jpg").forEach { s.file(id, it).writeText("x") }
        s.prune(id)
        assertTrue(s.file(id, "o_a.jpg").exists())
        assertTrue(s.file(id, "p_p_v2.jpg").exists())
        assertFalse(s.file(id, "p_p_v1.jpg").exists())
    }

    @Test
    fun cornersRoundTrip() {
        val q = Quad(Pt(0.1, 0.2), Pt(0.9, 0.1), Pt(0.95, 0.9), Pt(0.05, 0.85))
        val page = SessionPage("p", "o.jpg", corners = q.toList())
        assertEquals(q, page.quad)
        assertTrue(page.isRendering)
        assertFalse(page.copy(processed = "x", renderedStamp = 0).isRendering)
    }
}
