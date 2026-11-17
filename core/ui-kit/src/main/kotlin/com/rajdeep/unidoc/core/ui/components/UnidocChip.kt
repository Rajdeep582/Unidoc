package com.rajdeep.unidoc.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.motion.pressScale
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/** Filter/selection chip. Selected = brand gradient fill. */
@Composable
fun UnidocChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val colors = UnidocTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val content by animateColorAsState(
        if (selected) colors.onGradient else scheme.onSurfaceVariant,
        label = "chipContent"
    )
    Row(
        modifier = modifier
            .pressScale(interaction)
            .height(38.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(colors.brandHorizontalBrush)
                } else {
                    Modifier
                        .background(scheme.surfaceContainerHigh)
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.7f), CircleShape)
                }
            )
            .then(
                if (onLongClick == null) {
                    Modifier.selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Checkbox) {
                        haptics.tick()
                        onClick()
                    }
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Checkbox,
                        onLongClick = {
                            haptics.longPress()
                            onLongClick()
                        }
                    ) {
                        haptics.tick()
                        onClick()
                    }
                }
            )
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
