package com.rajdeep.unidoc.core.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

/** App logo tile: brand gradient squircle with the Unidoc mark. */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, size: Dp = 64.dp, contentDescription: String? = null) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(UnidocTheme.colors.brandBrush, shape)
    ) {
        Image(
            imageVector = UnidocMark,
            contentDescription = contentDescription,
            modifier = Modifier.size(size)
        )
    }
}
