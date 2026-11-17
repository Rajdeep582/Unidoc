package com.rajdeep.unidoc.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.motion.pressScale
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/** Primary call-to-action: brand-gradient pill with soft colored shadow and springy press. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
) {
    val colors = UnidocTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val glow = colors.gradientMid
    Row(
        modifier = modifier
            .pressScale(interaction)
            // Soft brand glow under the pill (shadows barely show on the dark theme). Disabled
            // buttons get none: they stay fully legible but clearly secondary.
            .drawBehind {
                if (!enabled) return@drawBehind
                val r = size.height / 2
                for (i in 1..4) {
                    val grow = i * 3.dp.toPx()
                    drawRoundRect(
                        color = glow.copy(alpha = 0.10f - i * 0.018f),
                        topLeft = Offset(-grow, -grow + 5.dp.toPx()),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(r + grow)
                    )
                }
            }
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.background(colors.brandHorizontalBrush)
                } else {
                    Modifier.background(scheme.surfaceContainerHighest).border(1.dp, scheme.outlineVariant, CircleShape)
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button
            ) {
                haptics.tick()
                onClick()
            }
            .defaultMinSize(minHeight = 56.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) colors.onGradient else scheme.onSurfaceVariant) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            // Button labels never wrap mid-word ("Appl/y"); they ellipsize if truly out of room.
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}
