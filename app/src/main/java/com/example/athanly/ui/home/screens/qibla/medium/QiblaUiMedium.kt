package com.example.athanly.ui.home.screens.qibla.medium

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

// Medium (600–839dp) — 12 columns, 12dp min margins. Same structure as Compact, content
// capped at 75% width to avoid over-stretching, mirroring HomeUiMedium's convention.
@Composable
fun QiblaUiMedium(
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
                    .fillMaxWidth(fraction = 0.75f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimensionResource(R.dimen.qibla_screen_horizontal_padding))
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

            // Priority order matters: offline first, then permission-denied — see
            // QiblaUiCompact's matching block for the full reasoning.
            when {
                !state.isOnline -> {
                    OfflineBlocker(
                        modifier = Modifier
                            .fillMaxWidth(fraction = 0.75f)
                            .align(Alignment.Center)
                    )
                }

                !state.isLocationPermissionGranted -> {
                    LocationOffBlocker(
                        onEnableLocationClick = onEnableLocationClick,
                        modifier = Modifier
                            .fillMaxWidth(fraction = 0.75f)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}