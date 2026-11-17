package com.rajdeep.unidoc.core.imaging

import kotlin.random.Random
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/** Realistic synthetic phone photos of documents with ground-truth corners. */
object Scenes {
    data class Scene(val name: String, val image: Mat, val corners: List<Point>?, val page: Mat? = null)

    private fun noise(img: Mat, sigma: Double) {
        val n = Mat(img.size(), img.type())
        Core.randn(n, 0.0, sigma)
        Core.add(img, n, img)
        n.release()
    }

    private fun jpeg(img: Mat, q: Int = 80): Mat {
        val buf = MatOfByte()
        Imgcodecs.imencode(".jpg", img, buf, MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, q))
        val out = Imgcodecs.imdecode(buf, Imgcodecs.IMREAD_COLOR)
        buf.release()
        return out
    }

    /** Wood-like desk: base colour with long grain stripes. */
    fun wood(w: Int, h: Int, seed: Int): Mat {
        val rnd = Random(seed)
        val bg = Mat(h, w, CvType.CV_8UC3, Scalar(60.0, 105.0, 150.0))
        repeat(90) {
            val y = rnd.nextInt(h)
            val c = rnd.nextDouble(-28.0, 28.0)
            Imgproc.line(
                bg,
                Point(0.0, y.toDouble()),
                Point(w.toDouble(), y + rnd.nextInt(-60, 60).toDouble()),
                Scalar(
                    60 + c,
                    105 + c,
                    150 + c
                ),
                rnd.nextInt(2, 9)
            )
        }
        noise(bg, 8.0)
        return bg
    }

    fun plain(w: Int, h: Int, b: Double, g: Double, r: Double, sigma: Double = 6.0): Mat {
        val bg = Mat(h, w, CvType.CV_8UC3, Scalar(b, g, r))
        noise(bg, sigma)
        return bg
    }

    fun checker(w: Int, h: Int): Mat {
        val bg = Mat(h, w, CvType.CV_8UC3, Scalar(200.0, 200.0, 210.0))
        val s = 70
        for (y in 0 until h step s) {
            for (x in 0 until w step s) {
                if ((x / s + y / s) % 2 == 0) Imgproc.rectangle(bg, Rect(x, y, s, s), Scalar(70.0, 60.0, 160.0), -1)
            }
        }
        noise(bg, 6.0)
        return bg
    }

    /** Bedsheet with bold horizontal bands (dark, white, mustard) — many long straight edges. */
    fun stripes(w: Int, h: Int, seed: Int): Mat {
        val rnd = Random(seed)
        val bg = Mat(h, w, CvType.CV_8UC3, Scalar(70.0, 70.0, 75.0))
        var y = 0
        val palette = listOf(Scalar(212.0, 214.0, 214.0), Scalar(40.0, 40.0, 45.0), Scalar(60.0, 170.0, 200.0), Scalar(120.0, 120.0, 125.0))
        while (y < h) {
            val t = rnd.nextInt(10, 45)
            Imgproc.rectangle(bg, Rect(0, y, w, t), palette[rnd.nextInt(palette.size)], -1)
            y += t + rnd.nextInt(6, 30)
        }
        // A woven "lozenge" motif breaking the bands, like printed fabric.
        repeat(6) {
            Imgproc.ellipse(
                bg,
                Point(rnd.nextInt(w).toDouble(), rnd.nextInt(h).toDouble()),
                org.opencv.core.Size(160.0, 60.0),
                0.0,
                0.0,
                360.0,
                Scalar(150.0, 150.0, 150.0),
                -1
            )
        }
        noise(bg, 9.0)
        return bg
    }

    fun place(page: Mat, bg: Mat, corners: List<Point>): Mat = Fixtures.composite(page, bg, corners)

    private fun pts(vararg v: Double) = v.toList().chunked(2).map { Point(it[0], it[1]) }

    fun photoPage(seed: Int): Mat {
        // Page with a large dark photo block at the top (breaks naive brightness splitting).
        val p = Fixtures.page(seed = seed)
        Imgproc.rectangle(p, Rect(100, 260, 1040, 520), Scalar(40.0, 50.0, 35.0), -1)
        return p
    }

    /** Corners of a page of [aspect] (w/h) seen by a pinhole camera — physically consistent perspective. */
    fun cameraCorners(
        aspect: Double,
        tiltXDeg: Double,
        tiltYDeg: Double,
        rollDeg: Double,
        imgW: Int,
        imgH: Int,
        fFactor: Double = 0.9
    ): List<Point> {
        val f = maxOf(imgW, imgH) * fFactor
        val hM = 0.3
        val wM = hM * aspect
        val tx = Math.toRadians(tiltXDeg)
        val ty = Math.toRadians(tiltYDeg)
        val rz = Math.toRadians(rollDeg)
        val dist = 0.3 * f / (imgH * 0.62) // page fills ~62% of the height
        fun p(x0: Double, y0: Double): Point {
            val x = x0 * Math.cos(rz) - y0 * Math.sin(rz)
            val yy = x0 * Math.sin(rz) + y0 * Math.cos(rz)
            val y1 = yy * Math.cos(tx)
            val z1 = yy * Math.sin(tx)
            val x2 = x * Math.cos(ty) + z1 * Math.sin(ty)
            val z2 = -x * Math.sin(ty) + z1 * Math.cos(ty) + dist
            return Point(imgW / 2.0 + f * x2 / z2, imgH / 2.0 + f * y1 / z2)
        }
        return listOf(p(-wM / 2, -hM / 2), p(wM / 2, -hM / 2), p(wM / 2, hM / 2), p(-wM / 2, hM / 2))
    }

    /** Scenes shot by a simulated camera (for output geometry checks). */
    fun physical(): List<Scene> {
        val list = ArrayList<Scene>()
        listOf(
            Triple("camera: tilted forward 30°", 30.0, 0.0),
            Triple("camera: tilted 20° + 15° sideways", 20.0, 15.0),
            Triple("camera: tilted 35°, rolled 10°", 35.0, -8.0)
        ).forEachIndexed { i, (name, tx, ty) ->
            val page = Fixtures.page(seed = 20 + i)
            val c = cameraCorners(page.cols().toDouble() / page.rows(), tx, ty, if (i == 2) 10.0 else 0.0, 1500, 2000)
            list += Scene(name, place(page, wood(1500, 2000, 30 + i), c), c, page)
        }
        val receipt = Fixtures.page(width = 500, height = 1800, colorHeader = false, seed = 9)
        val rc = cameraCorners(500.0 / 1800, 25.0, 5.0, 0.0, 1500, 2000)
        list += Scene("camera: receipt tilted 25°", place(receipt, wood(1500, 2000, 40), rc), rc, receipt)
        return list
    }

    fun all(): List<Scene> {
        val list = ArrayList<Scene>()
        val c1 = pts(260.0, 300.0, 1240.0, 250.0, 1330.0, 1720.0, 180.0, 1780.0)
        val p1 = Fixtures.page()
        list += Scene("dark desk", place(p1, Fixtures.desk(), c1), c1, p1)

        val c2 = pts(300.0, 220.0, 1180.0, 330.0, 1250.0, 1700.0, 160.0, 1640.0)
        val p2 = Fixtures.page(seed = 3)
        list += Scene("wood grain", place(p2, wood(1500, 2000, 3), c2), c2, p2)

        val c3 = pts(240.0, 260.0, 1260.0, 260.0, 1280.0, 1760.0, 220.0, 1760.0)
        list += Scene("light grey desk (low contrast)", place(Fixtures.page(seed = 4), plain(1500, 2000, 205.0, 208.0, 210.0), c3), c3)

        val c4 = pts(380.0, 200.0, 1300.0, 520.0, 1020.0, 1820.0, 120.0, 1480.0)
        val p4 = Fixtures.page(seed = 5)
        list += Scene("rotated 25°", place(p4, wood(1500, 2000, 9), c4), c4, p4)

        val c5 = pts(300.0, 330.0, 1200.0, 300.0, 1240.0, 1680.0, 260.0, 1700.0)
        list += Scene("checker tablecloth", place(Fixtures.page(seed = 6), checker(1500, 2000), c5), c5)

        val c6 = pts(250.0, 280.0, 1250.0, 240.0, 1300.0, 1750.0, 200.0, 1760.0)
        val clutter = wood(1500, 2000, 11)
        Imgproc.rectangle(clutter, Rect(0, 1820, 1500, 180), Scalar(25.0, 25.0, 30.0), -1) // laptop edge
        Imgproc.circle(clutter, Point(1420.0, 160.0), 120, Scalar(235.0, 235.0, 235.0), -1) // white mug
        list += Scene("clutter (laptop + mug)", place(Fixtures.page(seed = 7), clutter, c6), c6)

        val c7 = pts(270.0, 240.0, 1230.0, 290.0, 1270.0, 1750.0, 200.0, 1700.0)
        val shadowed = Fixtures.addShadow(place(photoPage(8), Fixtures.desk(), c7), 0.6)
        noise(shadowed, 7.0)
        list += Scene("photo page + heavy shadow + noise", jpeg(Fixtures.blur(shadowed, 5.0), 70), c7)

        val c8 = pts(560.0, 120.0, 940.0, 140.0, 920.0, 1880.0, 540.0, 1860.0)
        val receipt = Fixtures.page(width = 500, height = 2000, colorHeader = false, seed = 9)
        list += Scene("long receipt", place(receipt, wood(1500, 2000, 12), c8), c8, receipt)

        val c9 = pts(420.0, 520.0, 1080.0, 500.0, 1100.0, 1450.0, 400.0, 1470.0)
        list += Scene("small document far away", place(Fixtures.page(seed = 10), wood(1500, 2000, 13), c9), c9)

        val c10 = pts(80.0, 90.0, 1420.0, 70.0, 1440.0, 1930.0, 60.0, 1920.0)
        list += Scene("document fills frame", place(Fixtures.page(seed = 11), Fixtures.desk(), c10), c10)

        val c11 = pts(470.0, 380.0, 1030.0, 400.0, 1420.0, 1900.0, 80.0, 1880.0)
        val p11 = Fixtures.page(seed = 12)
        list += Scene("strong perspective", place(p11, wood(1500, 2000, 15), c11), c11, p11)

        val c12 = pts(280.0, 300.0, 1220.0, 280.0, 1260.0, 1700.0, 240.0, 1720.0)
        val dim = place(Fixtures.page(seed = 13), Fixtures.desk(), c12)
        Core.multiply(dim, Scalar(0.35, 0.35, 0.35), dim)
        noise(dim, 6.0)
        list += Scene("dim room + sensor noise", jpeg(dim, 75), c12)

        val card = Mat(540, 856, CvType.CV_8UC3, Scalar(200.0, 160.0, 120.0))
        Imgproc.rectangle(card, Rect(40, 60, 220, 280), Scalar(90.0, 110.0, 140.0), -1)
        repeat(5) { i -> Imgproc.rectangle(card, Rect(320, 80 + i * 70, 420, 22), Scalar(40.0, 40.0, 40.0), -1) }
        val c13 = pts(420.0, 760.0, 1100.0, 720.0, 1130.0, 1150.0, 440.0, 1190.0)
        list += Scene("ID card on wood", place(card, wood(1500, 2000, 16), c13), c13, card)

        val letter = Fixtures.page(width = 900, height = 1240, seed = 17)
        val c14 = pts(560.0, 120.0, 1450.0, 130.0, 1440.0, 1380.0, 570.0, 1385.0)
        list += Scene("letter on striped bedsheet", jpeg(place(letter, stripes(2000, 1500, 17), c14), 85), c14, letter)
        val c15 = pts(330.0, 200.0, 1180.0, 190.0, 1210.0, 1760.0, 300.0, 1780.0)
        list += Scene("page on striped bedsheet (portrait)", jpeg(place(Fixtures.page(seed = 18), stripes(1500, 2000, 18), c15), 85), c15)

        list += Scene("empty desk", wood(1500, 2000, 14), null)
        return list
    }
}

/** Mean/max corner error of a detection against the truth, in % of the image diagonal. */
fun cornerError(found: Quad, truth: List<Point>, w: Int, h: Int): Double {
    val diag = Math.hypot(w.toDouble(), h.toDouble())
    return found.points.zip(truth).maxOf { (a, b) -> Math.hypot(a.x - b.x, a.y - b.y) } / diag * 100
}
