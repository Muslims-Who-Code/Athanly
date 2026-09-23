package com.example.athanly.ui.home.screens.qibla.qiblaSubScreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import com.example.athanly.R

// Floats over the compass/distance content instead of replacing it outright — the vertical
// gradient (transparent -> background -> transparent) lets the frozen compass bleed through
// at the top/bottom edges rather than presenting a hard-edged card.
@Composable
fun LocationOffBlocker(
    onEnableLocationClick: () -> Unit,
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
                text = stringResource(R.string.qibla_location_off_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.qibla_location_blocker_message_button_gap)))

            Button(
                onClick = onEnableLocationClick,
                // Full pill shape, matching OnBoardingBottomBar's CTA convention.
                shape = RoundedCornerShape(9999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(
                    horizontal = dimensionResource(R.dimen.qibla_location_blocker_button_horizontal_padding),
                    vertical = dimensionResource(R.dimen.qibla_location_blocker_button_vertical_padding)
                )
            ) {
                Text(
                    text = stringResource(R.string.qibla_location_off_button),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}