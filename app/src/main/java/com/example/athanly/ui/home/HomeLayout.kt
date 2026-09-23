package com.example.athanly.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.athanly.R
import com.example.athanly.ui._navigation.navGraphs.SettingsDetailTransitionDurationMillis
import com.example.athanly.ui._navigation.navGraphs.homePageNavGraph
import com.example.athanly.ui._navigation.routes.HomePageRoutes
import com.example.athanly.ui.home.subscreens.BottomNavBar

// Persistent scaffold for the post-onboarding app shell — owns the inner NavHost so the
// bottom nav bar doesn't recompose when switching between Home/Qibla/Settings
@Composable
fun HomeLayout(
    modifier: Modifier
) {
    val innerNavController = rememberNavController()

    // Resolves the current window size class reactively — updates on rotation or multi-window resize
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    val currentBackStackEntry by innerNavController.currentBackStackEntryAsState()

    // Map the active destination to its HomePageRoutes tab — falls back to Home if no match yet
    val currentRoute = when {
        currentBackStackEntry?.destination?.hasRoute<HomePageRoutes.Qibla>() == true -> HomePageRoutes.Qibla
        currentBackStackEntry?.destination?.hasRoute<HomePageRoutes.Settings>() == true -> HomePageRoutes.Settings
        else -> HomePageRoutes.Home
    }

    // Screens pushed on top of Home/Settings (Language/Muadhdhin/ChangeCity pickers) have
    // their own DetailTopBar and shouldn't show the tab bar over them
    val isOnDetailScreen = currentBackStackEntry?.destination?.let { destination ->
        destination.hasRoute<HomePageRoutes.LanguagePicker>() ||
            destination.hasRoute<HomePageRoutes.MuadhdhinPicker>() ||
            destination.hasRoute<HomePageRoutes.ChangeCity>()
    } == true

    Scaffold(
        modifier = modifier
    ) { innerPadding ->
        val bottomDp = dimensionResource(R.dimen.bottom_nav_height)

        Box(modifier = Modifier.fillMaxSize()) {
            // Full-bleed, unpadded — each screen's own background now extends behind the nav
            // bar instead of stopping at its top edge, so the bar's rounded corners reveal real
            // screen content instead of Scaffold's flat container color underneath
            NavHost(
                modifier = Modifier.fillMaxSize(),
                navController = innerNavController,
                startDestination = HomePageRoutes.Home
            ) {
                // Only HomeTopBar needs this — applying it to the whole NavHost would push
                // AtmosphericBackground down from the top, exposing a plain white strip
                // behind the status bar instead of staying edge-to-edge
                //
                // bottomBarPadding is the bar's own height plus however far the bar itself is
                // lifted above the system nav bar — content needs to clear all of it, not just
                // the system inset, since BottomNavBar is a floating overlay, not a Scaffold slot
                homePageNavGraph(
                    currentScreen = windowSizeClass,
                    topBarPadding = (innerPadding.calculateTopPadding() / 2),
                    bottomBarPadding = bottomDp,
                    navController = innerNavController
                )
            }

            // Floats over the content instead of living in Scaffold's bottomBar slot, so there's
            // no opaque rectangular slot background exposed behind its rounded top corners.
            // AnimatedVisibility instead of a plain if-check so it fades out in step with the
            // picker screen's own slide-in (same duration) rather than snapping away the instant
            // navigate() is called — the bar floats over content rather than reserving layout
            // space, so there's no reflow to also guard against, unlike OnBoardingLayout's bars.
            AnimatedVisibility(
                visible = !isOnDetailScreen,
                enter = fadeIn(tween(SettingsDetailTransitionDurationMillis)),
                exit = fadeOut(tween(SettingsDetailTransitionDurationMillis)),
                modifier = Modifier
                    .padding(top = innerPadding.calculateBottomPadding())
                    .align(Alignment.BottomCenter)
            ) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        // Re-tapping the already-active tab is a no-op — without this guard,
                        // popUpTo+saveState+restoreState below still pops and immediately
                        // restores the current destination, which is enough of a lifecycle
                        // blip to re-fire resume-triggered effects (e.g. QiblaUi's location
                        // retry) on every repeat tap.
                        if (route != currentRoute) {
                            // Standard bottom-nav pattern: avoid piling up duplicate destinations on the
                            // back stack, and restore each tab's own state when switching back to it
                            innerNavController.navigate(route) {
                                popUpTo(innerNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}
