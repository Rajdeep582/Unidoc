package com.rajdeep.unidoc.core.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Single-line label that never wraps mid-word: shrinks (down to [minSize]) to fit its width,
 * then ellipsizes. Use for short labels in tight, fixed-width slots (nav items, tiles, badges)
 * where large system font sizes would otherwise break words across lines.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minSize: TextUnit = 10.sp,
    textAlign: TextAlign = TextAlign.Unspecified
) {
    val resolvedColor = if (color !=
        Color.Unspecified
    ) {
        color
    } else {
        style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    }
    val max = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = resolvedColor, textAlign = textAlign)),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minOf(minSize.value, max.value).sp, maxFontSize = max, stepSize = 0.5.sp)
    )
}
