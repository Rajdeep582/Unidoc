package com.rajdeep.unidoc.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.rajdeep.unidoc.feature.converter.hub.navigation.navigateToConverterHub
import com.rajdeep.unidoc.feature.scanner.home.navigation.navigateToScannerHome
import com.rajdeep.unidoc.navigation.TopLevelDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberUnidocAppState(navController: NavHostController = rememberNavController()): UnidocAppState {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    return remember(navController) { UnidocAppState(navController, snackbar, scope) }
}

@Stable
class UnidocAppState(val navController: NavHostController, val snackbarHostState: SnackbarHostState, private val scope: CoroutineScope) {

    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    /** Non-null only while a tab root is showing; the bottom bar hides on deeper screens. */
    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() {
            val destination = currentDestination ?: return null
            return TopLevelDestination.entries.firstOrNull { tab ->
                destination.hierarchy.any { it.hasRoute(tab.route) }
            }
        }

    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        val options = navOptions {
            // Single back stack per tab: keep state, avoid duplicates, return to start on back.
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        when (destination) {
            TopLevelDestination.SCANNER -> navController.navigateToScannerHome(options)
            TopLevelDestination.CONVERTER -> navController.navigateToConverterHub(options)
        }
    }

    /** Export sheet request: document id + whether to skip options and share immediately. */
    var exportRequest by mutableStateOf<Pair<String, Boolean>?>(null)
        private set

    fun requestExport(sessionId: String, quickShare: Boolean) {
        exportRequest = sessionId to quickShare
    }

    fun dismissExport() {
        exportRequest = null
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }
}
