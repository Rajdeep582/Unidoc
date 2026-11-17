package com.rajdeep.unidoc.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.motion.pressScale
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/** Base surface: soft container, hairline border, springy press when clickable. */
@Composable
fun UnidocCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val colors = UnidocTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = when {
        onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick,
            onClick = { onClick?.invoke() }
        )
        onClick != null -> Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
        else -> Modifier
    }
    val border: Brush = if (selected) {
        colors.brandBrush
    } else {
        Brush.linearGradient(
            listOf(scheme.outlineVariant, scheme.outlineVariant)
        )
    }
    Box(
        modifier = modifier
            .then(if (onClick != null || onLongClick != null) Modifier.pressScale(interaction, 0.97f) else Modifier)
            .clip(shape)
            .background(scheme.surfaceContainerLow)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .then(clickModifier),
        content = content
    )
}
