package com.rajdeep.unidoc.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.SwapHorizontalCircle
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.SwapHorizontalCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.brand.BrandBackdrop
import com.rajdeep.unidoc.core.ui.brand.BrandLogo
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme

internal val PreviewNavItems = listOf(
    UnidocNavItem("Scanner", Icons.Rounded.DocumentScanner, Icons.Outlined.DocumentScanner),
    UnidocNavItem("Converter", Icons.Rounded.SwapHorizontalCircle, Icons.Outlined.SwapHorizontalCircle)
)

@PreviewLightDark
@Composable
private fun DesignSystemPreview() {
    UnidocTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box {
                BrandBackdrop(animated = false, modifier = Modifier.height(420.dp))
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    BrandLogo(size = 72.dp)
                    ScreenHeader(title = "Documents", subtitle = "Scan, organize and export")
                    GradientButton(text = "Scan document", icon = Icons.Rounded.CameraAlt, onClick = {})
                    UnidocNavigationBar(
                        items = PreviewNavItems,
                        selectedIndex = 0,
                        onItemSelected = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
