package com.rajdeep.unidoc.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryDaoTest {
    private lateinit var db: UnidocDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                UnidocDatabase::class.java
            ).allowMainThreadQueries().build()
        dao = db.library()
    }

    @After
    fun tearDown() = db.close()

    private fun doc(id: String, updated: Long, folder: Long? = null) =
        DocumentEntity(id, "Doc $id", folder, 1, updated, 2, 100, null, false)

    @Test
    fun documentsOrderedByRecencyAndFolderCounts() = runTest {
        val folder = dao.upsertFolder(FolderEntity(name = "Work", createdAt = 0))
        dao.upsert(doc("a", 10, folder))
        dao.upsert(doc("b", 20))
        assertEquals(listOf("b", "a"), dao.observeDocuments().first().map { it.sessionId })
        assertEquals(1, dao.observeFolders().first().single().documentCount)
        dao.deleteFolder(folder)
        assertNull(dao.document("a")!!.folderId)
    }

    @Test
    fun fullTextSearchFindsWordsInsidePages() = runTest {
        dao.upsert(doc("a", 1))
        dao.upsert(doc("b", 2))
        dao.insertText("a", "p1", "Electricity bill for October 2026")
        dao.insertText("b", "p1", "Offer letter Northwind")
        assertEquals(listOf("a"), dao.search("electr*").first())
        assertEquals(listOf("b"), dao.search("northwind").first())
        dao.pruneText("a", emptyList())
        assertEquals(emptyList<String>(), dao.search("electr*").first())
    }
}
