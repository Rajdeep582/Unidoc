package com.rajdeep.unidoc.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.SwapHorizontalCircle
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.SwapHorizontalCircle
import androidx.compose.ui.graphics.vector.ImageVector
import com.rajdeep.unidoc.R
import com.rajdeep.unidoc.feature.converter.hub.navigation.ConverterHubRoute
import com.rajdeep.unidoc.feature.scanner.home.navigation.ScannerHomeRoute
import kotlin.reflect.KClass

/** The two bottom-navigation tabs. Order here is the order in the bar. */
enum class TopLevelDestination(
    val route: KClass<*>,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SCANNER(
        route = ScannerHomeRoute::class,
        labelRes = R.string.tab_scanner,
        selectedIcon = Icons.Rounded.DocumentScanner,
        unselectedIcon = Icons.Outlined.DocumentScanner
    ),
    CONVERTER(
        route = ConverterHubRoute::class,
        labelRes = R.string.tab_converter,
        selectedIcon = Icons.Rounded.SwapHorizontalCircle,
        unselectedIcon = Icons.Outlined.SwapHorizontalCircle
    )
}
