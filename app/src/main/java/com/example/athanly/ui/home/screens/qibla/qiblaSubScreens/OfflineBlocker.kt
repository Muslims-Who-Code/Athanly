package com.example.athanly.ui.home.screens.qibla.qiblaSubScreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.athanly.R

// Same floating-over-content gradient shape as LocationOffBlocker, minus the button — there's
// no in-app action that fixes connectivity, and the existing ON_RESUME retry already re-checks
// once the user returns to the app, so nothing here needs to trigger that manually.
@Composable
fun OfflineBlocker(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.2f to MaterialTheme.colorScheme.background,
                        0.8f to MaterialTheme.colorScheme.background,
                        1f to Color.Transparent
                    )
                )
            )
            .padding(vertical = dimensionResource(R.dimen.qibla_location_blocker_vertical_padding)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(
                horizontal = dimensionResource(R.dimen.qibla_location_blocker_content_horizontal_padding)
            )
        ) {
            Text(
                text = stringResource(R.string.qibla_offline_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}