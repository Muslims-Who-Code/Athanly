package com.example.athanly.qibla.local.data

import com.example.athanly.location.domain.repository.LocationProvider
import com.example.athanly.qibla.local.domain.modelData.Coordinates
import com.example.athanly.qibla.local.domain.repository.UserLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

// Real GPS-backed UserLocationRepository, replacing MockUserLocationRepository. Wraps
// LocationProvider's one-shot suspend fetch in flow {} since QiblaViewModel depends on the
// Flow-shaped UserLocationRepository interface, not on LocationProvider directly.
class GpsUserLocationRepository @Inject constructor(
    private val locationProvider: LocationProvider
) : UserLocationRepository {
    override fun observedLocation(): Flow<Coordinates> = flow {
        // Throwing here (rather than emitting nothing) is what lets QiblaViewModel's
        // existing try/catch around this Flow surface a real error to the user.
        val coordinates = locationProvider.getCurrentCoordinates()
            ?: throw IllegalStateException("No GPS fix available")

        emit(coordinates)
    }

    override fun hasLocationPermission(): Boolean {
        return locationProvider.hasLocationPermission()
    }
}