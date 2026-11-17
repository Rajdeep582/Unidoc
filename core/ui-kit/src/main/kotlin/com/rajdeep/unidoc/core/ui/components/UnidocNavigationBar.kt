package com.rajdeep.unidoc.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.motion.pressScale
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

@Immutable
data class UnidocNavItem(val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

private val BarShape = RoundedCornerShape(28.dp)
private val IndicatorShape = RoundedCornerShape(22.dp)
private const val LEAD_STIFFNESS = 900f
private const val TRAIL_STIFFNESS = 320f

/**
 * Floating pill navigation bar with an elastic gradient indicator: the leading edge
 * springs ahead of the trailing edge, so the indicator stretches and settles while travelling.
 */
@Composable
fun UnidocNavigationBar(items: List<UnidocNavItem>, selectedIndex: Int, onItemSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    require(items.isNotEmpty()) { "UnidocNavigationBar needs at least one item" }
    val colors = UnidocTheme.colors
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberHaptics()

    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    val movingRight = selectedIndex >= previousIndex
    if (previousIndex != selectedIndex) previousIndex = selectedIndex

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.NavBarHeight)
            .shadow(
                elevation = 20.dp,
                shape = BarShape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = colors.gradientMid.copy(alpha = 0.45f)
            )
            .clip(BarShape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = if (colors.isDark) 0.9f else 0.6f), BarShape)
            .padding(6.dp)
    ) {
        val itemWidth = maxWidth / items.size
        val targetStart = itemWidth * selectedIndex
        val targetEnd = targetStart + itemWidth
        val start by animateDpAsState(
            targetValue = targetStart,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = if (movingRight) TRAIL_STIFFNESS else LEAD_STIFFNESS
            ),
            label = "indicatorStart"
        )
        val end by animateDpAsState(
            targetValue = targetEnd,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = if (movingRight) LEAD_STIFFNESS else TRAIL_STIFFNESS
            ),
            label = "indicatorEnd"
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(start.roundToPx(), 0) }
                .width((end - start).coerceAtLeast(0.dp))
                .fillMaxHeight()
                .shadow(10.dp, IndicatorShape, ambientColor = colors.gradientMid, spotColor = colors.gradientMid)
                .clip(IndicatorShape)
                .background(colors.brandHorizontalBrush)
        )

        Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
            items.forEachIndexed { index, item ->
                NavBarItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = {
                        if (index != selectedIndex) haptics.tick()
                        onItemSelected(index)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(item: UnidocNavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = UnidocTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onGradient else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(UnidocMotion.DURATION_MEDIUM, easing = UnidocMotion.Standard),
        label = "navItemColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = UnidocMotion.bouncy(),
        label = "navItemIconScale"
    )
    Row(
        modifier = modifier
            .clip(IndicatorShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null
            )
            .pressScale(interaction, pressedScale = 0.94f)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = tween(UnidocMotion.DURATION_SHORT),
            label = "navItemIcon"
        ) { isSelected ->
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
        // Full word at any font scale: shrinks instead of truncating ("Conver…").
        FitText(
            text = item.label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            minSize = 10.sp,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
