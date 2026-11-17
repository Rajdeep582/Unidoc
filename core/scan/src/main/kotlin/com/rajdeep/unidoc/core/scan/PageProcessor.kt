package com.rajdeep.unidoc.core.scan

import com.rajdeep.unidoc.core.imaging.BookSplitter
import com.rajdeep.unidoc.core.imaging.DocumentDetector
import com.rajdeep.unidoc.core.imaging.IdCardComposer
import com.rajdeep.unidoc.core.imaging.ImageEncoder
import com.rajdeep.unidoc.core.imaging.ImageFilters
import com.rajdeep.unidoc.core.imaging.PageClassifier
import com.rajdeep.unidoc.core.imaging.PerspectiveWarper
import com.rajdeep.unidoc.core.imaging.Quad
import com.rajdeep.unidoc.core.model.ScanMode
import javax.inject.Inject
import org.opencv.core.Mat

/**
 * Renders one session page: original → (detect) → perspective warp → book half / ID composite
 * → rotation → filter → working image + thumbnail on disk. Pure function of the page's edit
 * state, so any edit is just "change state, re-render" (non-destructive).
 */
class PageProcessor @Inject constructor(private val store: ScanSessionStore) {
    private val detector = DocumentDetector()

    /** Detects corners for an original if the page doesn't have them yet. Returns normalized quad or null. */
    fun detectCorners(sessionId: String, fileName: String): Quad? {
        val mat = MatIO.readBgr(store.file(sessionId, fileName), maxSide = 1600)
        return try {
            detector.detect(mat)?.quad?.normalized(mat.cols(), mat.rows())
        } finally {
            mat.release()
        }
    }

    fun render(session: ScanSession, page: SessionPage): SessionPage {
        OpenCv.ensureLoaded()
        val mats = ArrayList<Mat>()

        // Not named t(): org.opencv.core.Mat.t() is a member (transpose) and members win over
        // extensions — that silently transposed every page and mis-applied the crop corners.
        fun <M : Mat> M.tracked(): M = also { mats += it }
        try {
            var page = page
            val front = MatIO.readBgr(store.file(session.id, page.original)).tracked()
            val focal35 = focal35mm(store.file(session.id, page.original))
            val (frontWarped, frontCorners) = rectify(front, page.corners, page.warp, session.mode, focal35).let {
                it.first.tracked() to
                    it.second
            }
            page = page.copy(corners = frontCorners)

            var composed: Mat = frontWarped
            if (session.mode == ScanMode.ID_CARD) {
                val back = page.backOriginal?.let { name ->
                    val b = MatIO.readBgr(store.file(session.id, name)).tracked()
                    val (bw, bc) = rectify(b, page.backCorners, true, session.mode, focal35mm(store.file(session.id, name)))
                    page = page.copy(backCorners = bc)
                    bw.tracked()
                }
                composed = IdCardComposer.compose(frontWarped, back).tracked()
            } else if (page.region != PageRegion.FULL) {
                val halves = BookSplitter.split(frontWarped).onEach { it.tracked() }
                composed = if (halves.size == 2) halves[if (page.region == PageRegion.LEFT) 0 else 1] else halves[0]
            }

            var rotated = PerspectiveWarper.rotate(composed, page.rotation).tracked()
            if (page.flipHorizontal) rotated = PerspectiveWarper.flip(rotated).tracked()
            if (page.tilt != 0f) rotated = PerspectiveWarper.straighten(rotated, page.tilt.toDouble()).tracked()
            var filtered = ImageFilters.apply(rotated, page.filter).tracked()
            if (page.hasAdjustments) {
                filtered = ImageFilters.adjust(filtered, page.brightness, page.contrast, page.saturation, page.sharpness).tracked()
            }
            val kind = PageClassifier.classify(filtered, page.filter)

            val version = page.version + 1
            val (bytes, ext) = ImageEncoder.encodeWorking(filtered)
            // Unique per render: a name is never reused for different pixels, so no image cache
            // (Coil, aspect cache) can show an older crop under a current name.
            val tag = java.util.UUID.randomUUID().toString().take(6)
            val processedName = "p_${page.id}_v${version}_$tag.$ext"
            store.file(session.id, processedName).writeBytes(bytes)
            val thumbName = "t_${page.id}_v${version}_$tag.jpg"
            store.file(session.id, thumbName).writeBytes(ImageEncoder.thumbnail(filtered))
            return page.copy(
                processed = processedName,
                thumbnail = thumbName,
                kind = kind,
                version = version,
                renderedStamp = page.editStamp
            )
        } finally {
            mats.forEach { it.release() }
        }
    }

    /** 35 mm-equivalent focal length from the photo's EXIF (helps undo perspective exactly). */
    private fun focal35mm(file: java.io.File): Double? = runCatching {
        androidx.exifinterface.media.ExifInterface(
            file
        ).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
            .takeIf { it in 10..300 }?.toDouble()
    }.getOrNull()

    /** Returns the warped image and the (normalized) corners actually used. */
    private fun rectify(src: Mat, stored: List<Double>?, warp: Boolean, mode: ScanMode, focal35: Double? = null): Pair<Mat, List<Double>> {
        if (!warp || stored?.isEmpty() == true) return src.clone() to emptyList()
        val quad = stored?.toQuad()
            ?: detector.detect(src)?.quad?.normalized(src.cols(), src.rows())
            ?: return src.clone() to emptyList()
        val aspect = if (mode == ScanMode.ID_CARD) IdCardComposer.CARD_ASPECT else null
        val focalPx = focal35?.let { PerspectiveWarper.focalPxFrom35mm(it, src.cols(), src.rows()) }
        val warped = PerspectiveWarper.warp(src, quad.denormalized(src.cols(), src.rows()), aspect, focalPx = focalPx)
        return warped to quad.toList()
    }
}
