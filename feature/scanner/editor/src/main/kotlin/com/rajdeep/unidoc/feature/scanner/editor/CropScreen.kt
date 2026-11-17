package com.rajdeep.unidoc.feature.scanner.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.view.ViewCompat
import com.rajdeep.unidoc.core.ui.components.DocumentThumbnail
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.components.UnidocTopBar
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import kotlin.math.roundToInt

private const val LOUPE_ZOOM = 2.6f

/**
 * Manual crop with 8 handles: 4 corners + the middle of each side (slides that side).
 *
 * Touches respond on contact (no drag slop), the handle keeps its offset from the finger (no
 * jump), outlines that would cross or collapse are refused, and a magnifier shows the exact
 * pixels under the handle. While dragging, edits stay local and are committed on release, so
 * the outline never flickers between the finger and a lagging state round-trip.
 *
 * [corners] are normalized (0..1) TL, TR, BR, BL over the upright photo. [image] is that photo
 * (any resolution with the same aspect); [placeholder] is shown while it loads.
 */
@Composable
fun CropScreen(
    corners: List<Offset>,
    onCornersChange: (List<Offset>) -> Unit,
    onAutoDetect: () -> Unit,
    onNoCrop: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    imageAspectRatio: Float = 1f / 1.414f,
    image: ImageBitmap? = null,
    applying: Boolean = false,
    placeholder: @Composable () -> Unit = { DocumentThumbnail(seed = 11, modifier = Modifier.fillMaxSize()) }
) {
    val colors = UnidocTheme.colors
    val haptics = rememberHaptics()
    var local by remember { mutableStateOf(corners) }
    var dragging by remember { mutableIntStateOf(CropGeometry.NONE) }
    LaunchedEffect(corners) { if (dragging == CropGeometry.NONE) local = corners }
    val currentLocal by rememberUpdatedState(local)
    val commit by rememberUpdatedState(onCornersChange)
    val accent = colors.gradientStart
    val accentEnd = colors.gradientEnd

    UnidocTheme(darkTheme = true) {
        Column(modifier.fillMaxSize().background(Color(0xFF0E110C))) {
            UnidocTopBar(title = "Adjust edges", subtitle = "Drag a corner or the middle of a side", onBack = onBack)
            // Touches are taken by the whole editor area (not just the photo), so a corner on the
            // photo edge is grabbable from outside too, and the system back-swipe is switched off
            // around the handles so edge corners respond on the first touch.
            var areaOrigin by remember { mutableStateOf(Offset.Zero) }
            var photoInRoot by remember { mutableStateOf<Rect?>(null) }
            val photoRect = photoInRoot?.translate(-areaOrigin)
            val currentRect by rememberUpdatedState(photoRect)
            val density = LocalDensity.current
            val radius = with(density) { 44.dp.toPx() }
            val excl = with(density) { 30.dp.toPx() }
            val view = LocalView.current
            // Corners + left/right side handles: 6 × 60 dp, within Android's 200 dp per-edge budget.
            LaunchedEffect(local, photoInRoot) {
                val r = photoInRoot ?: return@LaunchedEffect
                val pts = CropGeometry.handles(CropGeometry.toPx(local, r.width, r.height))
                val rects = listOf(0, 1, 2, 3, 5, 7).map { i ->
                    val c = pts[i] + r.topLeft
                    android.graphics.Rect(
                        (c.x - excl).roundToInt(),
                        (c.y - excl).roundToInt(),
                        (c.x + excl).roundToInt(),
                        (c.y + excl).roundToInt()
                    )
                }
                ViewCompat.setSystemGestureExclusionRects(view, rects)
            }
            DisposableEffect(view) { onDispose { ViewCompat.setSystemGestureExclusionRects(view, emptyList()) } }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onPlaced { areaOrigin = it.positionInRoot() }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val rect = currentRect ?: return@awaitEachGesture
                            val w = rect.width
                            val h = rect.height
                            val startPx = CropGeometry.toPx(currentLocal, w, h)
                            val handle = CropGeometry.pick(startPx, down.position - rect.topLeft, radius)
                            if (handle == CropGeometry.NONE) return@awaitEachGesture
                            down.consume()
                            dragging = handle
                            haptics.tick()
                            // Keep the handle where it was relative to the finger.
                            val grab = CropGeometry.handles(startPx)[handle] - (down.position - rect.topLeft)
                            var last = startPx
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                val moved = CropGeometry.move(startPx, handle, change.position - rect.topLeft + grab, w, h)
                                if (moved != null) {
                                    last = moved
                                    local = CropGeometry.toNorm(moved, w, h)
                                }
                            }
                            dragging = CropGeometry.NONE
                            commit(CropGeometry.toNorm(last, w, h))
                        }
                    }
                    .padding(horizontal = 32.dp, vertical = Dimens.SpaceL),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(
                    Modifier
                        .aspectRatio(imageAspectRatio, matchHeightConstraintsFirst = true)
                        .onPlaced { photoInRoot = Rect(it.positionInRoot(), it.size.toSize()) }
                ) {
                    if (image == null) placeholder()
                    Canvas(
                        Modifier.fillMaxSize()
                    ) {
                        image?.let {
                            drawImage(
                                it,
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                filterQuality = FilterQuality.Medium
                            )
                        }
                        val pts = CropGeometry.toPx(local, size.width, size.height)
                        drawOutline(pts, dragging, accent, accentEnd, scrim = true)
                    }
                    if (dragging != CropGeometry.NONE) {
                        val pts = CropGeometry.toPx(local, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                        Loupe(
                            focus = CropGeometry.handles(pts)[dragging],
                            corners = pts,
                            active = dragging,
                            areaW = constraints.maxWidth.toFloat(),
                            areaH = constraints.maxHeight.toFloat(),
                            image = image,
                            accent = accent,
                            accentEnd = accentEnd
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Dimens.Gutter),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UnidocChip("Auto", false, onAutoDetect, leadingIcon = Icons.Rounded.AutoFixHigh)
                UnidocChip("Full", false, onNoCrop, leadingIcon = Icons.Rounded.CropFree)
                Box(Modifier.weight(1f))
                GradientButton(
                    if (applying) "Applying…" else "Apply",
                    onDone,
                    enabled = !applying,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                )
            }
        }
    }
}

/** Outline, optional scrim outside it, corner dots and side pills. */
private fun DrawScope.drawOutline(pts: List<Offset>, active: Int, accent: Color, accentEnd: Color, scrim: Boolean, scale: Float = 1f) {
    val outline = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until 4) lineTo(pts[i].x, pts[i].y)
        close()
    }
    if (scrim) {
        val mask = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addPath(outline)
        }
        drawPath(mask, Color.Black.copy(alpha = 0.5f))
    }
    drawPath(outline, Brush.linearGradient(listOf(accent, accentEnd)), style = Stroke(2.dp.toPx() * scale))
    pts.forEachIndexed { i, p ->
        val r = (if (i == active) 13.dp else 10.dp).toPx() * scale
        drawCircle(Color.White, radius = r, center = p)
        drawCircle(accent, radius = r * 0.5f, center = p)
    }
    CropGeometry.midpoints(pts).forEachIndexed { side, m ->
        val a = pts[side]
        val b = pts[(side + 1) % 4]
        val len = (b - a).getDistance().coerceAtLeast(1f)
        val dir = (b - a) / len
        val half = (if (active == side + 4) 15.dp else 12.dp).toPx() * scale
        drawLine(Color.White, m - dir * half, m + dir * half, strokeWidth = 9.dp.toPx() * scale, cap = StrokeCap.Round)
        drawLine(
            accentEnd,
            m - dir * (half - 4.dp.toPx() * scale),
            m + dir * (half - 4.dp.toPx() * scale),
            strokeWidth =
            3.5f.dp.toPx() * scale,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Magnifier drawn straight from the photo bitmap (sharp, no extra image load), parked in the top
 * corner away from the finger so it never sits under it.
 */
@Composable
private fun Loupe(
    focus: Offset,
    corners: List<Offset>,
    active: Int,
    areaW: Float,
    areaH: Float,
    image: ImageBitmap?,
    accent: Color,
    accentEnd: Color
) {
    val density = LocalDensity.current
    val sizeDp = 124.dp
    val sizePx = with(density) { sizeDp.toPx() }
    val margin = with(density) { 4.dp.toPx() }
    // Float above the finger (the image box isn't clipped, so it may rise into the margin);
    // near the top, sit beside the finger instead, on the side with more room.
    val gap = sizePx * 0.35f
    val (x, y) = if (focus.y - gap - sizePx >= -sizePx * 0.9f) {
        (focus.x - sizePx / 2).coerceIn(-margin * 6, areaW - sizePx + margin * 6) to focus.y - gap - sizePx
    } else {
        val toRight = focus.x < areaW / 2
        (if (toRight) focus.x + gap else focus.x - gap - sizePx) to (focus.y - sizePx / 2).coerceAtLeast(-sizePx * 0.9f)
    }
    Canvas(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(sizeDp)
            .shadow(14.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF1A1E16))
    ) {
        val circle = Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) }
        clipPath(circle) {
            // Map display coords → loupe: centre on focus, zoom.
            val ox = size.width / 2 - focus.x * LOUPE_ZOOM
            val oy = size.height / 2 - focus.y * LOUPE_ZOOM
            if (image != null) {
                val sx = image.width / areaW
                val sy = image.height / areaH
                val halfW = size.width / 2 / LOUPE_ZOOM
                val halfH = size.height / 2 / LOUPE_ZOOM
                val left = ((focus.x - halfW) * sx).roundToInt()
                val top = ((focus.y - halfH) * sy).roundToInt()
                val srcW = (halfW * 2 * sx).roundToInt().coerceAtLeast(1)
                val srcH = (halfH * 2 * sy).roundToInt().coerceAtLeast(1)
                // Clip the source rect to the bitmap and place it where it belongs in the loupe.
                val cl = left.coerceIn(0, image.width - 1)
                val ct = top.coerceIn(0, image.height - 1)
                val cr = (left + srcW).coerceIn(cl + 1, image.width)
                val cb = (top + srcH).coerceIn(ct + 1, image.height)
                val dstX = ((cl - left) / sx * LOUPE_ZOOM)
                val dstY = ((ct - top) / sy * LOUPE_ZOOM)
                drawImage(
                    image,
                    srcOffset = IntOffset(cl, ct),
                    srcSize = IntSize(cr - cl, cb - ct),
                    dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
                    dstSize = IntSize(((cr - cl) / sx * LOUPE_ZOOM).roundToInt(), ((cb - ct) / sy * LOUPE_ZOOM).roundToInt()),
                    filterQuality = FilterQuality.High
                )
            }
            translate(ox, oy) {
                val zoomed = corners.map { it * LOUPE_ZOOM }
                val path = Path().apply {
                    moveTo(zoomed[0].x, zoomed[0].y)
                    for (i in 1 until 4) lineTo(zoomed[i].x, zoomed[i].y)
                    close()
                }
                drawPath(path, accentEnd, style = Stroke(1.5.dp.toPx()))
            }
            // Crosshair at the exact handle position.
            val c = Offset(size.width / 2, size.height / 2)
            val arm = 10.dp.toPx()
            drawLine(Color.White, c - Offset(arm, 0f), c + Offset(arm, 0f), 3.dp.toPx())
            drawLine(Color.White, c - Offset(0f, arm), c + Offset(0f, arm), 3.dp.toPx())
            drawLine(accent, c - Offset(arm, 0f), c + Offset(arm, 0f), 1.5.dp.toPx())
            drawLine(accent, c - Offset(0f, arm), c + Offset(0f, arm), 1.5.dp.toPx())
        }
        drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.5.dp.toPx()))
    }
}

object SampleCrop {
    val corners = listOf(Offset(0.06f, 0.05f), Offset(0.95f, 0.04f), Offset(0.93f, 0.95f), Offset(0.05f, 0.96f))
}
