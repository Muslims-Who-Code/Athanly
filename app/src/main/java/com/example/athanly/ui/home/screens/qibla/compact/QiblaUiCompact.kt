package com.example.athanly.ui.home.screens.qibla.compact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.example.athanly.R
import com.example.athanly.qibla.local.domain.modelData.QiblaUiState
import com.example.athanly.ui.home.screens.qibla.qiblaSubScreens.DistanceCard
import com.example.athanly.ui.home.screens.qibla.qiblaSubScreens.LocationOffBlocker
import com.example.athanly.ui.home.screens.qibla.qiblaSubScreens.OfflineBlocker
import com.example.athanly.ui.home.screens.qibla.qiblaSubScreens.QiblaCompass

// Compact (< 600dp): single-column, full-bleed — same shell shape as HomeUiCompact.
@Composable
fun QiblaUiCompact(
    state: QiblaUiState,
    onUnitToggled: () -> Unit,
    onEnableLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimensionResource(R.dimen.qibla_screen_horizontal_padding))
                    .verticalScroll(rememberScrollState())
            ) {
                QiblaCompass(
                    needleRotationDegrees = state.needleRotationDegrees,
                    kaabaMarkerDegrees = state.qiblaDegrees.toFloat()
                )

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.qibla_screen_section_spacing)))

                DistanceCard(
                    distanceValue = state.distanceValue,
                    distanceUnit = state.distanceUnit,
                    qiblaDegrees = state.qiblaDegrees,
                    qiblaDirection = state.qiblaDirection,
                    onUnitToggled = onUnitToggled
                )
            }

            // Floats over the (frozen, default-valued) compass/distance content above
            // instead of replacing it. Priority order matters: offline is checked first,
            // then permission-denied. A plain state.error alone (e.g. a GPS fix still
            // being acquired, or an unrelated Qibla-bearing API failure) shows no blocker
            // at all — intentionally scoped out of this pass, see project plan.
            when {
                !state.isOnline -> {
                    OfflineBlocker(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                    )
                }

                !state.isLocationPermissionGranted -> {
                    LocationOffBlocker(
                        onEnableLocationClick = onEnableLocationClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}