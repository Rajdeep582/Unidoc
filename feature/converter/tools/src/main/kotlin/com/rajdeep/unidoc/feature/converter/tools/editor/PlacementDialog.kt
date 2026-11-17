package com.rajdeep.unidoc.feature.converter.tools.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.IconButtonStyle
import com.rajdeep.unidoc.core.ui.components.IconButtonSurface
import com.rajdeep.unidoc.core.ui.components.drawSignature
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.converter.tools.preview.PdfInfo
import com.rajdeep.unidoc.feature.converter.tools.preview.PdfPage
import kotlin.math.roundToInt

/** What is being placed. */
sealed interface Stamp {
    /** Signature strokes normalized to a box of [aspect] (w/h). Resizable. */
    data class Signature(val strokes: List<List<Float>>, val aspect: Float) : Stamp

    /** Text at [sizePt] points; width follows the text. */
    data class Label(val text: String, val sizePt: Float) : Stamp
}

/**
 * Pick the page and drag (and for signatures, resize) the stamp where it should go.
 * Coordinates are fractions of the displayed page, so they're independent of preview size.
 */
@Composable
fun PlacementDialog(uri: String, info: PdfInfo, stamp: Stamp, initial: Placement?, onDone: (Placement) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val colors = UnidocTheme.colors
    var page by remember { mutableIntStateOf((initial?.page ?: (info.pageCount - 1)).coerceIn(0, info.pageCount - 1)) }
    val defaultW = if (stamp is Stamp.Signature) 0.3f else 0f
    var x by remember { mutableFloatStateOf(initial?.x ?: 0.6f) }
    var y by remember { mutableFloatStateOf(initial?.y ?: 0.82f) }
    var w by remember { mutableFloatStateOf(initial?.w ?: defaultW) }
    FullScreenDialog(onDismiss) {
        com.rajdeep.unidoc.core.ui.components.UnidocTopBar(
            title = if (stamp is Stamp.Signature) "Place signature" else "Place text",
            subtitle = "Page ${page + 1} of ${info.pageCount} · drag to move",
            onBack = onDismiss
        )
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(Dimens.Gutter),
            contentAlignment = Alignment.Center
        ) {
            val aspect = info.aspect(page)
            val fitW = minOf(maxWidth.value, maxHeight.value * aspect)
            val pageWidthDp = fitW.dp
            val density = LocalDensity.current
            val pageWpx = with(density) { pageWidthDp.toPx() }
            val pageHpx = pageWpx / aspect
            val pagePt = info.pageSizes.getOrNull(page)?.first?.toFloat() ?: 595f
            val pxPerPt = pageWpx / pagePt
            // Stamp box in px.
            val boxW = when (stamp) {
                is Stamp.Signature -> w * pageWpx
                is Stamp.Label -> {
                    val longest = stamp.text.lines().maxOfOrNull { it.length } ?: 1
                    (longest * stamp.sizePt * 0.55f * pxPerPt).coerceAtMost(pageWpx)
                }
            }
            val boxH = when (stamp) {
                is Stamp.Signature -> boxW / stamp.aspect
                is Stamp.Label -> stamp.text.lines().size * stamp.sizePt * 1.25f * pxPerPt
            }
            val currentBox by rememberUpdatedState(boxW to boxH)
            Box(
                Modifier
                    .size(pageWidthDp, pageWidthDp / aspect)
                    .shadow(8.dp, RoundedCornerShape(4.dp))
                    .background(Color.White)
            ) {
                PdfPage(uri, page, Modifier.fillMaxSize())
                val left = (x * pageWpx).coerceIn(0f, (pageWpx - boxW).coerceAtLeast(0f))
                val top = (y * pageHpx).coerceIn(0f, (pageHpx - boxH).coerceAtLeast(0f))
                Box(
                    Modifier
                        .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                        .size(with(density) { boxW.toDp() }, with(density) { boxH.toDp() })
                        .border(1.5.dp, colors.brandBrush, RoundedCornerShape(6.dp))
                        .background(Color(0x125A6E36), RoundedCornerShape(6.dp))
                        .pointerInput(page) {
                            detectDragGestures(onDragStart = { haptics.tick() }) { change, drag ->
                                change.consume()
                                val (bw, bh) = currentBox
                                x = ((x * pageWpx + drag.x) / pageWpx).coerceIn(0f, ((pageWpx - bw) / pageWpx).coerceAtLeast(0f))
                                y = ((y * pageHpx + drag.y) / pageHpx).coerceIn(0f, ((pageHpx - bh) / pageHpx).coerceAtLeast(0f))
                            }
                        }
                ) {
                    when (stamp) {
                        is Stamp.Signature -> Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                            drawSignature(stamp.strokes, Rect(0f, 0f, size.width, size.height), Color(0xFF1E2A12))
                        }
                        is Stamp.Label -> Text(
                            stamp.text,
                            style = TextStyle(
                                fontSize = with(density) { (stamp.sizePt * pxPerPt).toSp() },
                                lineHeight = with(density) { (stamp.sizePt * 1.25f * pxPerPt).toSp() },
                                color = Color(0xFF12121C)
                            )
                        )
                    }
                    if (stamp is Stamp.Signature) {
                        // Corner handle resizes, keeping the signature's aspect.
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(10.dp, 10.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(colors.brandBrush)
                                .pointerInput(page) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        w = (w + drag.x / pageWpx).coerceIn(0.08f, (1f - x).coerceAtLeast(0.08f))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.OpenInFull, "Resize", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButtonSurface(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous page", {
                haptics.tick()
                page--
            }, style = IconButtonStyle.Tonal, enabled = page > 0)
            Text(
                "${page + 1} / ${info.pageCount}",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                modifier = Modifier.padding(horizontal = Dimens.SpaceXl)
            )
            IconButtonSurface(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next page", {
                haptics.tick()
                page++
            }, style = IconButtonStyle.Tonal, enabled = page < info.pageCount - 1)
        }
        BottomBar {
            GradientButton(
                "Place here",
                { onDone(Placement(page, x, y, if (stamp is Stamp.Signature) w else 0f)) },
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
