package com.rajdeep.unidoc.feature.scanner.home.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.rajdeep.unidoc.core.ui.brand.ScreenSurface
import com.rajdeep.unidoc.feature.scanner.home.ScannerHomeCallbacks
import com.rajdeep.unidoc.feature.scanner.home.ScannerHomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object ScannerHomeRoute

fun NavController.navigateToScannerHome(navOptions: NavOptions? = null) = navigate(ScannerHomeRoute, navOptions)

fun NavGraphBuilder.scannerHomeScreen(contentPadding: PaddingValues, callbacks: ScannerHomeCallbacks) {
    composable<ScannerHomeRoute> {
        ScreenSurface {
            ScannerHomeScreen(contentPadding = contentPadding, callbacks = callbacks)
        }
    }
}
