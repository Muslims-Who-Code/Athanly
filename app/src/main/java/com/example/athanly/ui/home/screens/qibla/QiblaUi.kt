package com.example.athanly.ui.home.screens.qibla

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.example.athanly.ui.home.screens.qibla.compact.QiblaUiCompact
import com.example.athanly.ui.home.screens.qibla.expanded.QiblaUiExpanded
import com.example.athanly.ui.home.screens.qibla.medium.QiblaUiMedium
import com.example.athanly.ui.home.viewModel.QiblaViewModel

@Composable
fun QiblaUi(
    modifier: Modifier,
    windowSizeClass: WindowSizeClass,
    viewModel: QiblaViewModel = hiltViewModel()
) {
    val state by viewModel.qiblaState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val onEnableLocationClick: () -> Unit = {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    // Fires on every resume, including the initial one (redundant with the ViewModel's own
    // init{} fetch on first entry, but harmless) — this is what actually revives the compass
    // after the user taps the blocker's button, enables location in Settings, and comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.retryLocationAccess()
    }

    when {
        // 840dp+ — expanded (large tablets, landscape)
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> {
            QiblaUiExpanded(
                state = state,
                onUnitToggled = viewModel::onDistanceUnitToggled,
                onEnableLocationClick = onEnableLocationClick,
                modifier = modifier
            )
        }
        // 600dp+ — medium (small tablets, large phones in landscape)
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
            QiblaUiMedium(
                state = state,
                onUnitToggled = viewModel::onDistanceUnitToggled,
                onEnableLocationClick = onEnableLocationClick,
                modifier = modifier
            )
        }
        // < 600dp — compact (standard phones)
        else -> {
            QiblaUiCompact(
                state = state,
                onUnitToggled = viewModel::onDistanceUnitToggled,
                onEnableLocationClick = onEnableLocationClick,
                modifier = modifier
            )
        }
    }
}