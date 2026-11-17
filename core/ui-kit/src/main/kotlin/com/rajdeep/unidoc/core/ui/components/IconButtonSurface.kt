package com.rajdeep.unidoc.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.motion.pressScale
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

enum class IconButtonStyle { Tonal, Gradient, Ghost, OnDark }

/** Round icon button with press spring. 48dp touch target regardless of visual [size]. */
@Composable
fun IconButtonSurface(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconButtonStyle = IconButtonStyle.Tonal,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified
) {
    val colors = UnidocTheme.colors
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val base = Modifier
        .size(size)
        .clip(CircleShape)
    val styled = when (style) {
        IconButtonStyle.Tonal ->
            base
                .background(scheme.surfaceContainerHigh)
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
        IconButtonStyle.Gradient -> base.background(colors.brandBrush)
        IconButtonStyle.Ghost -> base
        IconButtonStyle.OnDark -> base.background(Color.White.copy(alpha = 0.14f))
    }
    val iconTint = when {
        tint != Color.Unspecified -> tint
        style == IconButtonStyle.Gradient || style == IconButtonStyle.OnDark -> Color.White
        else -> scheme.onSurface
    }
    Box(
        modifier = modifier
            .size(maxOf(size, 48.dp))
            .pressScale(interaction, 0.9f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button
            ) {
                haptics.tick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(styled, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f),
                modifier = Modifier.size(size * 0.48f)
            )
        }
    }
}
