package com.rajdeep.unidoc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.brand.LocalBackdropPhase
import com.rajdeep.unidoc.core.ui.brand.rememberBackdropPhase
import com.rajdeep.unidoc.core.ui.components.UnidocNavItem
import com.rajdeep.unidoc.core.ui.components.UnidocNavigationBar
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.navigation.TopLevelDestination
import com.rajdeep.unidoc.navigation.UnidocNavHost

const val NAV_BAR_TEST_TAG = "unidoc:navbar"

@Composable
fun UnidocApp(modifier: Modifier = Modifier, appState: UnidocAppState = rememberUnidocAppState(), versionName: String = "") {
    val background = MaterialTheme.colorScheme.background
    val currentTab = appState.currentTopLevelDestination
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(
        bottom = systemBottom + Dimens.NavBarHeight + Dimens.NavBarBottomMargin * 2
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Screens paint their own opaque surface (ScreenSurface); they share this drift phase.
        CompositionLocalProvider(LocalBackdropPhase provides rememberBackdropPhase()) {
            UnidocNavHost(appState = appState, contentPadding = contentPadding, versionName = versionName)
        }

        // Soft fade so content dissolves under the floating bar instead of hard-clipping. Only on the
        // two tab screens: elsewhere it sat over editor tools and Convert buttons and dimmed them.
        AnimatedVisibility(
            visible = currentTab != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(120))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(systemBottom + 112.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, background.copy(alpha = 0.94f))))
            )
        }

        appState.exportRequest?.let { (id, quick) ->
            com.rajdeep.unidoc.feature.scanner.export.ExportSheetRoute(
                sessionId = id,
                quickShare = quick,
                onDismiss = appState::dismissExport
            )
        }

        SnackbarHost(
            hostState = appState.snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = if (currentTab !=
                        null
                    ) {
                        contentPadding.calculateBottomPadding()
                    } else {
                        systemBottom + 16.dp
                    }
                )
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.padding(horizontal = Dimens.NavBarHorizontalMargin)
            )
        }

        AnimatedVisibility(
            visible = currentTab != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            // Timed with page transitions (no spring overshoot lingering after the page has changed).
            enter = slideInVertically(tween(260, easing = UnidocMotion.EmphasizedDecelerate)) { it / 2 } + fadeIn(tween(200)),
            exit =
            slideOutVertically(tween(110, easing = LinearOutSlowInEasing)) { it / 2 } +
                fadeOut(tween(110, easing = LinearOutSlowInEasing))
        ) {
            val items = TopLevelDestination.entries.map { tab ->
                UnidocNavItem(
                    label = stringResource(tab.labelRes),
                    selectedIcon = tab.selectedIcon,
                    icon = tab.unselectedIcon
                )
            }
            UnidocNavigationBar(
                items = items,
                selectedIndex = (currentTab ?: TopLevelDestination.SCANNER).ordinal,
                onItemSelected = { index ->
                    appState.navigateToTopLevelDestination(TopLevelDestination.entries[index])
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.NavBarHorizontalMargin, vertical = Dimens.NavBarBottomMargin)
                    .testTag(NAV_BAR_TEST_TAG)
            )
        }
    }
}
