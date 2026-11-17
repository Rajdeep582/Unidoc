package com.rajdeep.unidoc.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.ui.theme.Dimens

/**
 * Compact top bar for detail screens. Transparent at rest; fills with the surface color
 * and shows a hairline once content scrolls beneath it ([scrolled]).
 */
@Composable
fun UnidocTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrolled: Boolean = false,
    backContentDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (scrolled) scheme.surfaceContainer.copy(alpha = 0.97f) else Color.Transparent,
        animationSpec = tween(220),
        label = "topBarContainer"
    )
    Column(modifier.fillMaxWidth().background(container)) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButtonSurface(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = backContentDescription,
                    onClick = onBack,
                    style = IconButtonStyle.Ghost
                )
            } else {
                Spacer(Modifier.width(Dimens.SpaceM))
            }
            Column(Modifier.weight(1f).padding(horizontal = Dimens.SpaceXs)) {
                // Shrink before truncating: a short title next to several actions ("351…") read as broken.
                FitText(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    minSize = 15.sp,
                    modifier = Modifier.semantics { heading() }
                )
                if (subtitle != null) {
                    FitText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        minSize = 10.sp
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXxs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
        Box(Modifier.fillMaxWidth()) {
            if (scrolled) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
        }
    }
}
