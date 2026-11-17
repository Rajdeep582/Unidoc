package com.rajdeep.unidoc.feature.scanner.home

import com.rajdeep.unidoc.core.model.Folder
import com.rajdeep.unidoc.core.model.ScanDocument

/** Representative library content for previews, screenshot tests and the design gallery. */
object SampleLibrary {
    private const val NOW = 1_763_200_000_000L
    private const val HOUR = 3_600_000L

    val folders = listOf(
        Folder(1, "Work", 12),
        Folder(2, "Receipts", 31),
        Folder(3, "ID & Certificates", 6),
        Folder(4, "College", 18)
    )

    val documents = listOf(
        ScanDocument("1", "Offer letter — Northwind", 3, 412_000, NOW - 2 * HOUR, NOW - 2 * HOUR, 1, hasOcrText = true),
        ScanDocument("2", "Electricity bill Oct", 1, 188_000, NOW - 20 * HOUR, NOW - 20 * HOUR, 2, hasOcrText = true),
        ScanDocument("3", "Aadhaar card", 1, 96_000, NOW - 50 * HOUR, NOW - 50 * HOUR, 3),
        ScanDocument("4", "DBMS notes — Unit 4", 14, 2_400_000, NOW - 80 * HOUR, NOW - 80 * HOUR, 4, hasOcrText = true),
        ScanDocument("5", "Rent agreement", 6, 1_100_000, NOW - 120 * HOUR, NOW - 120 * HOUR, 1),
        ScanDocument("6", "Whiteboard — sprint plan", 2, 640_000, NOW - 200 * HOUR, NOW - 200 * HOUR, null)
    )
}
