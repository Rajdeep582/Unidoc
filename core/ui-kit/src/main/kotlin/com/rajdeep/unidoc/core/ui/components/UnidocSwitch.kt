package com.rajdeep.unidoc.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/** Visual-only switch (the row owns the toggle semantics/click). Gradient track when on. */
@Composable
fun UnidocSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = UnidocTheme.colors
    val scheme = MaterialTheme.colorScheme
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 0.dp, UnidocMotion.bouncy(), label = "switchThumb")
    val onAlpha by animateFloatAsState(if (checked) 1f else 0f, label = "switchTrack")
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.matchParentSize().alpha(onAlpha).background(colors.brandHorizontalBrush))
        Box(
            Modifier
                .padding(4.dp)
                .offset(x = thumbOffset)
                .size(24.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
