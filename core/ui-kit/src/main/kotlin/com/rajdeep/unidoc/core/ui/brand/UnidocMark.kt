package com.rajdeep.unidoc.core.ui.brand

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Unidoc mark on a 108×108 adaptive-icon grid (content inside the 66dp safe zone):
 * scanner viewfinder corners + folded page + "U" monogram + cyan scan beam.
 * Shared geometry with res/drawable/ic_launcher_foreground.xml.
 */
object UnidocMarkPaths {
    const val CORNERS =
        "M24,34V28a4,4 0,0 1,4 -4H34 M74,24H80a4,4 0,0 1,4 4V34 M24,74V80a4,4 0,0 0,4 4H34 M84,74V80a4,4 0,0 1,-4 4H74"
    const val PAGE = "M42,32H60L70,42V72a4,4 0,0 1,-4 4H42a4,4 0,0 1,-4 -4V36a4,4 0,0 1,4 -4Z"
    const val FOLD = "M60,32V38a4,4 0,0 0,4 4H70Z"
    const val BEAM = "M31,54H77"
    const val MONOGRAM = "M47,46V57a7,7 0,0 0,14 0V46"
}

val UnidocMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "UnidocMark",
        defaultWidth = 108.dp,
        defaultHeight = 108.dp,
        viewportWidth = 108f,
        viewportHeight = 108f
    ).apply {
        addPath(
            pathData = addPathNodes(UnidocMarkPaths.CORNERS),
            stroke = SolidColor(Color.White),
            strokeAlpha = 0.78f,
            strokeLineWidth = 3f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
        addPath(pathData = addPathNodes(UnidocMarkPaths.PAGE), fill = SolidColor(Color.White))
        addPath(pathData = addPathNodes(UnidocMarkPaths.FOLD), fill = SolidColor(Color(0xFFD0DAB4)))
        addPath(
            pathData = addPathNodes(UnidocMarkPaths.BEAM),
            stroke = Brush.horizontalGradient(
                0f to Color(0x00E7EDD8),
                0.2f to Color(0xFFE7EDD8),
                0.8f to Color(0xFFE7EDD8),
                1f to Color(0x00E7EDD8),
                startX = 31f,
                endX = 77f
            ),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round
        )
        addPath(
            pathData = addPathNodes(UnidocMarkPaths.MONOGRAM),
            stroke = Brush.linearGradient(
                colors = listOf(Color(0xFF4A5B2C), Color(0xFF8A9E5C)),
                start = Offset(47f, 46f),
                end = Offset(61f, 64f)
            ),
            strokeLineWidth = 4.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

/** Single-color version for monochrome contexts (tinted by the caller). */
val UnidocMarkMonochrome: ImageVector by lazy {
    ImageVector.Builder(
        name = "UnidocMarkMonochrome",
        defaultWidth = 108.dp,
        defaultHeight = 108.dp,
        viewportWidth = 108f,
        viewportHeight = 108f
    ).apply {
        addPath(
            pathData = addPathNodes(UnidocMarkPaths.CORNERS),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 3f,
            strokeLineCap = StrokeCap.Round
        )
        addPath(
            pathData = addPathNodes(UnidocMarkPaths.PAGE),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 3f,
            strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = addPathNodes(UnidocMarkPaths.MONOGRAM),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 4.5f,
            strokeLineCap = StrokeCap.Round
        )
    }.build()
}
