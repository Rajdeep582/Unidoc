package com.rajdeep.unidoc.feature.scanner.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Grid3x3
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.model.FlashMode
import com.rajdeep.unidoc.core.model.ScanMode
import com.rajdeep.unidoc.core.ui.components.DocumentThumbnail
import com.rajdeep.unidoc.core.ui.components.IconButtonStyle
import com.rajdeep.unidoc.core.ui.components.IconButtonSurface
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.motion.pressScale
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

private val ModeLabels = mapOf(
    ScanMode.ID_CARD to "ID card",
    ScanMode.BOOK to "Book",
    ScanMode.DOCUMENT to "Document",
    ScanMode.RECEIPT to "Receipt",
    ScanMode.WHITEBOARD to "Whiteboard",
    ScanMode.QR to "QR code"
)
private val ModeOrder =
    listOf(ScanMode.ID_CARD, ScanMode.BOOK, ScanMode.DOCUMENT, ScanMode.RECEIPT, ScanMode.WHITEBOARD, ScanMode.QR)

/**
 * Capture screen chrome. [preview] hosts the CameraX surface (Phase 3); the detected-quad overlay,
 * guidance and controls are drawn on top. Always dark, independent of app theme.
 */
@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onAction: (CaptureAction) -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable BoxScope.() -> Unit = { ViewfinderPlaceholder() }
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 3:4 viewfinder matching the camera streams, so the overlay maps 1:1 without crop math.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 60.dp)
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(28.dp))
        ) {
            preview()
            if (state.gridVisible) GridOverlay()
            state.quad?.let { DetectionOverlay(it, state.stability, locked = state.hint == CaptureHint.CAPTURING) }
            if (state.mode != ScanMode.QR) {
                HintPill(state.hint, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
            } else {
                QrFrame()
            }
            CaptureFlash(state.capturing)
        }
        CaptureTopBar(state, onAction, Modifier.align(Alignment.TopCenter))
        CaptureBottomControls(state, onAction, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CaptureTopBar(state: CaptureUiState, onAction: (CaptureAction) -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButtonSurface(Icons.Rounded.Close, "Close camera", {
            onAction(CaptureAction.Close)
        }, style = IconButtonStyle.OnDark)
        Spacer(Modifier.weight(1f))
        val flashIcon = when (state.flash) {
            FlashMode.OFF -> Icons.Rounded.FlashOff
            FlashMode.AUTO -> Icons.Rounded.FlashAuto
            FlashMode.ON -> Icons.Rounded.FlashOn
            FlashMode.TORCH -> Icons.Rounded.FlashlightOn
        }
        IconButtonSurface(flashIcon, "Flash ${state.flash.name.lowercase()}", {
            onAction(CaptureAction.CycleFlash)
        }, style = IconButtonStyle.OnDark)
        IconButtonSurface(
            if (state.gridVisible) Icons.Rounded.Grid3x3 else Icons.Rounded.GridOff,
            if (state.gridVisible) "Hide grid" else "Show grid",
            { onAction(CaptureAction.ToggleGrid) },
            style = IconButtonStyle.OnDark
        )
        TogglePill("AUTO", state.autoCapture) { onAction(CaptureAction.ToggleAutoCapture) }
        Spacer(Modifier.width(Dimens.SpaceXs))
        TogglePill(if (state.batch) "BATCH" else "SINGLE", state.batch) { onAction(CaptureAction.ToggleBatch) }
        Spacer(Modifier.width(Dimens.SpaceS))
    }
}

@Composable
private fun TogglePill(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = UnidocTheme.colors
    val haptics = rememberHaptics()
    Box(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .then(
                if (on) {
                    Modifier.background(
                        colors.brandHorizontalBrush
                    )
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.14f))
                }
            )
            .clickable(role = Role.Switch) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun HintPill(hint: CaptureHint, modifier: Modifier) {
    val text = when (hint) {
        CaptureHint.SEARCHING -> "Looking for a document…"
        CaptureHint.MOVE_CLOSER -> "Move closer"
        CaptureHint.HOLD_STEADY -> "Hold steady"
        CaptureHint.CAPTURING -> "Capturing"
        CaptureHint.TOO_DARK -> "Too dark — turn on flash"
        CaptureHint.FLIP_CARD -> "Now flip the card"
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn() + slideInVertically { -it / 2 }) togetherWith
                (fadeOut() + slideOutVertically { it / 2 })
        },
        label = "captureHint",
        modifier = modifier
    ) { label ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** Animated quad with glowing corners; the stroke fills with gradient as stability rises. */
@Composable
private fun DetectionOverlay(quad: List<Offset>, stability: Float, locked: Boolean) {
    val colors = UnidocTheme.colors
    val pulse by rememberInfiniteTransition(label = "quadPulse").animateFloat(
        0.35f,
        0.6f,
        infiniteRepeatable(tween(900, easing = UnidocMotion.Standard), RepeatMode.Reverse),
        label = "quadPulseAlpha"
    )
    val progress by animateFloatAsState(stability, UnidocMotion.effects(), label = "quadStability")
    val strokeColor by animateColorAsState(if (locked) colors.gradientEnd else Color.White, label = "quadColor")
    Canvas(Modifier.fillMaxSize()) {
        val pts = quad.map { Offset(it.x * size.width, it.y * size.height) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, Brush.linearGradient(colors.brandColors.map { it.copy(alpha = 0.08f + 0.08f * progress) }))
        drawPath(
            path,
            strokeColor.copy(alpha = pulse + 0.4f * progress),
            style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round)
        )
        val arm = 22.dp.toPx()
        pts.forEachIndexed { i, p ->
            val prev = pts[(i + 3) % 4]
            val next = pts[(i + 1) % 4]
            fun toward(t: Offset): Offset {
                val d = t - p
                val len = d.getDistance().coerceAtLeast(1f)
                return p + d * (arm / len)
            }
            val cornerStroke = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
            val cornerPath = Path().apply {
                moveTo(toward(prev).x, toward(prev).y)
                lineTo(p.x, p.y)
                lineTo(toward(next).x, toward(next).y)
            }
            drawPath(cornerPath, Brush.linearGradient(colors.brandColors), style = cornerStroke)
            drawCircle(colors.gradientEnd.copy(alpha = 0.35f), radius = 10.dp.toPx(), center = p)
        }
    }
}

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.22f)
        for (i in 1..2) {
            drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.dp.toPx())
            drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.dp.toPx())
        }
    }
}

@Composable
private fun BoxScope.QrFrame() {
    val colors = UnidocTheme.colors
    Canvas(Modifier.matchParentSize()) {
        val side = size.minDimension * 0.62f
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        val arm = side * 0.18f
        val stroke = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
        val brush = Brush.linearGradient(colors.brandColors)
        listOf(
            Offset(left, top) to listOf(Offset(left + arm, top), Offset(left, top + arm)),
            Offset(left + side, top) to listOf(Offset(left + side - arm, top), Offset(left + side, top + arm)),
            Offset(left + side, top + side) to listOf(Offset(left + side - arm, top + side), Offset(left + side, top + side - arm)),
            Offset(left, top + side) to listOf(Offset(left + arm, top + side), Offset(left, top + side - arm))
        ).forEach { (corner, ends) ->
            val path = Path().apply {
                moveTo(ends[0].x, ends[0].y)
                lineTo(corner.x, corner.y)
                lineTo(ends[1].x, ends[1].y)
            }
            drawPath(path, brush, style = stroke)
        }
    }
    Text(
        "Point at a QR or barcode",
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** Brief white flash when a photo is taken. */
@Composable
private fun BoxScope.CaptureFlash(capturing: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (capturing) 0.55f else 0f,
        animationSpec = if (capturing) tween(60) else tween(320),
        label = "captureFlash"
    )
    if (alpha > 0f) Box(Modifier.matchParentSize().background(Color.White.copy(alpha = alpha)))
}

/** Shown until the camera surface is attached: a calm desk scene. */
@Composable
fun BoxScope.ViewfinderPlaceholder() {
    Canvas(Modifier.matchParentSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2A2F24), Color(0xFF151912))))
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x33FFF3D6), Color.Transparent),
                center = Offset(
                    size.width * 0.3f,
                    size.height * 0.2f
                ),
                radius = size.width
            ),
            radius = size.width,
            center = Offset(size.width * 0.3f, size.height * 0.2f)
        )
    }
}

@Composable
private fun CaptureBottomControls(state: CaptureUiState, onAction: (CaptureAction) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = Dimens.SpaceL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ModeSelector(state.mode) { onAction(CaptureAction.ModeChanged(it)) }
        Spacer(Modifier.height(Dimens.SpaceL))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceXl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(state.pageCount > 0, label = "captureTray") { hasPages ->
                    if (hasPages) {
                        PageStack(state.pageCount) { onAction(CaptureAction.Done) }
                    } else {
                        IconButtonSurface(Icons.Rounded.PhotoLibrary, "Import from gallery", {
                            onAction(CaptureAction.OpenGallery)
                        }, style = IconButtonStyle.OnDark, size = 52.dp)
                    }
                }
            }
            if (state.mode == ScanMode.QR) {
                Spacer(Modifier.size(84.dp))
            } else {
                ShutterButton(state.stability, state.autoCapture, state.capturing) { onAction(CaptureAction.Shutter) }
            }
            Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
                AnimatedContent(
                    targetState = state.pageCount > 0,
                    transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                    label = "captureDone"
                ) { hasPages ->
                    if (hasPages) DoneButton { onAction(CaptureAction.Done) } else Spacer(Modifier.size(44.dp))
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(selected: ScanMode, onSelect: (ScanMode) -> Unit) {
    val listState = rememberLazyListState()
    val index = ModeOrder.indexOf(selected)
    // Keep the selected mode centred on any screen width (was a fixed pixel offset).
    LaunchedEffect(index) {
        val info = listState.layoutInfo
        val itemWidth = info.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: info.visibleItemsInfo.firstOrNull()?.size ?: 0
        listState.animateScrollToItem(index, scrollOffset = -(info.viewportSize.width - itemWidth) / 2)
    }
    val haptics = rememberHaptics()
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(ModeOrder) { _, mode ->
            val isSelected = mode == selected
            val color by animateColorAsState(
                if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                label = "modeColor"
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .then(if (isSelected) Modifier.background(Color.White.copy(alpha = 0.16f)) else Modifier)
                    .clickable(role = Role.Tab) {
                        haptics.tick()
                        onSelect(mode)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(ModeLabels.getValue(mode), style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
    }
}

@Composable
private fun ShutterButton(stability: Float, auto: Boolean, capturing: Boolean, onClick: () -> Unit) {
    val colors = UnidocTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val progress by animateFloatAsState(if (auto) stability else 0f, UnidocMotion.effects(), label = "shutterProgress")
    val innerScale by animateFloatAsState(if (capturing) 0.82f else 1f, UnidocMotion.bouncy(), label = "shutterInner")
    Box(
        Modifier
            .size(84.dp)
            .pressScale(interaction, 0.9f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.confirm()
                onClick()
            }
            .semantics { contentDescription = "Capture page" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White, style = Stroke(4.dp.toPx()))
            if (progress > 0f) {
                drawArc(
                    Brush.sweepGradient(colors.brandColors + colors.gradientStart),
                    -90f,
                    360f * progress,
                    false,
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Box(
            Modifier
                .size(66.dp)
                .graphicsLayer {
                    scaleX = innerScale
                    scaleY = innerScale
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color.White, Color(0xFFE7EDD8))))
        )
    }
}

@Composable
private fun PageStack(count: Int, onClick: () -> Unit) {
    val colors = UnidocTheme.colors
    Box(
        Modifier.clickable(role = Role.Button, onClick = onClick).semantics {
            contentDescription =
                "$count pages captured, review"
        }
    ) {
        DocumentThumbnail(pageCount = count, seed = count.toLong(), modifier = Modifier.width(46.dp))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-8).dp)
                .size(24.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.brandBrush),
            contentAlignment = Alignment.Center
        ) {
            Text("$count", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

@Composable
private fun DoneButton(onClick: () -> Unit) {
    val colors = UnidocTheme.colors
    Box(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(colors.brandHorizontalBrush)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        com.rajdeep.unidoc.core.ui.components.FitText(
            "Done",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            minSize = 11.sp
        )
    }
}

object SampleCapture {
    val detecting = CaptureUiState(
        mode = ScanMode.DOCUMENT,
        pageCount = 3,
        hint = CaptureHint.HOLD_STEADY,
        stability = 0.65f,
        quad = listOf(Offset(0.17f, 0.2f), Offset(0.84f, 0.17f), Offset(0.88f, 0.8f), Offset(0.13f, 0.83f))
    )
    val idle = CaptureUiState(hint = CaptureHint.SEARCHING, gridVisible = true)
}
