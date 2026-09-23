package com.example.athanly.ui.home.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.athanly.appDataPreference.data.AppDataPreferencesDataStore
import com.example.athanly.connectivity.domain.repository.ConnectivityProvider
import com.example.athanly.qibla.local.data.QiblaPreferencesDataStore
import com.example.athanly.qibla.local.domain.modelData.QiblaUiState
import com.example.athanly.qibla.local.domain.modelData.enums.DistanceUnit
import com.example.athanly.qibla.local.domain.repository.CompassRepository
import com.example.athanly.qibla.local.domain.repository.UserLocationRepository
import com.example.athanly.qibla.remote.domain.repository.QiblaApiRepository
import com.example.athanly.qibla.userInfoCalculations.QiblaCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QiblaViewModel @Inject constructor(
    private val userLocationRepository: UserLocationRepository,
    private val compassRepository: CompassRepository,
    private val preferencesDataStore: QiblaPreferencesDataStore,
    private val qiblaApiRepository: QiblaApiRepository,
    private val connectivityProvider: ConnectivityProvider,
    private val appDataPreferencesDataStore: AppDataPreferencesDataStore
) : ViewModel() {
    private val _qiblaState = MutableStateFlow(QiblaUiState())
    val qiblaState: StateFlow<QiblaUiState> = _qiblaState.asStateFlow()

    // Tracks the distance/location subscription so retryLocationAccess() can cancel and
    // relaunch it — observedLocation() is a one-shot fetch (see GpsUserLocationRepository),
    // so once it fails, this collect{} terminates for good and needs a fresh Job to recover.
    private var distanceJob: Job? = null

    init {
        // Checked first, synchronously — see checkLocationAccessPreconditions()'s own
        // comment for why this can't just be folded into the coroutines below.
        checkLocationAccessPreconditions()

        // Needle: a live compass hand driven purely by device heading — updates
        // continuously and has no relationship to the Kaaba's direction. Matches the
        // heading directly (no negation) so it lines up with the device's native
        // compass: e.g. facing due east should rotate the needle toward the fixed "E"
        // label, not "W" — negating here previously mirrored east and west.
        viewModelScope.launch {
            try {
                compassRepository.observedHeadingDegrees().collect { headingDegrees ->
                    _qiblaState.update { it.copy(needleRotationDegrees = headingDegrees) }
                }
            } catch (e: Exception) {
                _qiblaState.update { it.copy(error = e.message) }
            }
        }

        distanceJob = observeDistance()

        // Kaaba marker/bearing text: a one-shot calculation, not a live subscription.
        // Per design this must only change via an explicit recalculation — never
        // automatically in response to heading (or even ongoing location) changes.
        recalculateQiblaDirection()
    }

    // Distance card's value/unit: reactive to location and the unit preference — never
    // to heading, which has nothing to do with distance and would just mean recomputing
    // this pointlessly on every gyroscope tick.
    private fun observeDistance(): Job {
        return viewModelScope.launch {
            try {
                combine(
                    userLocationRepository.observedLocation(),
                    preferencesDataStore.observedDistanceUnit()
                ) { coordinates, distanceUnit ->
                    val distanceKm = QiblaCalculator.distanceToKaaba(from = coordinates)
                    val distanceValue = if (distanceUnit == DistanceUnit.MILES) {
                        QiblaCalculator.kmToMiles(km = distanceKm)
                    } else {
                        distanceKm
                    }
                    distanceValue to distanceUnit
                }.collect { (distanceValue, distanceUnit) ->
                    _qiblaState.update {
                        it.copy(isLoading = false, distanceValue = distanceValue, distanceUnit = distanceUnit, error = null)
                    }
                }
            } catch (e: Exception) {
                _qiblaState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Called after the user returns from the system location settings screen (see the
    // location-off blocker) — re-fetches location for both the distance subscription and
    // the Kaaba bearing, since both gave up for good after the first failure. Also rechecks
    // connectivity/permission first, since that's exactly the round trip (leave app → fix
    // the setting → come back) this is meant to catch.
    fun retryLocationAccess() {
        checkLocationAccessPreconditions()
        distanceJob?.cancel()
        distanceJob = observeDistance()
        recalculateQiblaDirection()
    }

    // isOnline()/hasLocationPermission() are plain synchronous checks, not suspend calls —
    // updating state from them directly (no launch{}) keeps this deterministic and ordered
    // ahead of the other init{} coroutines, rather than racing them on a separate dispatcher.
    // Only the DataStore write below is genuine I/O, so only that part needs Dispatchers.IO.
    private fun checkLocationAccessPreconditions() {
        val isOnline = connectivityProvider.isOnline()
        val isLocationPermissionGranted = userLocationRepository.hasLocationPermission()

        _qiblaState.update { data ->
            data.copy(
                isOnline = isOnline, 
                isLocationPermissionGranted = isLocationPermissionGranted
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Written even though this ViewModel never reads it back — this keeps
                // AppDataPreferencesDataStore's flag from going stale if permission is
                // revoked in system Settings after onboarding already wrote it once.
                appDataPreferencesDataStore.setIsLocationPermissionGranted(isLocationPermissionGranted)
            } catch (e: Exception) {
                _qiblaState.update { it.copy(error = e.message) }
            }
        }
    }

    // Recomputes the Kaaba's direction from the current location and updates
    // qiblaDegrees/qiblaDirection, which in turn move the compass's Kaaba marker and
    // the distance card's bearing text. Called once at startup; a future refresh
    // trigger (UI/UX not decided yet) will call this again to move the marker.
    fun recalculateQiblaDirection() {
        viewModelScope.launch {
            try {
                // .first() takes a single snapshot from the location flow rather than
                // subscribing to it — exactly what "only updates via explicit
                // recalculation" requires, instead of combine()'s continuous reacting.
                val coordinates = userLocationRepository.observedLocation().first()
                val bearingDegrees = qiblaApiRepository.fetchQiblaBearing(
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude
                )

                _qiblaState.update { data ->
                    data.copy(
                        qiblaDegrees = bearingDegrees,
                        qiblaDirection = QiblaCalculator.cardinalDirectionFor(bearingDegrees = bearingDegrees),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _qiblaState.update { data ->
                    data.copy(error = e.message)
                }
            }
        }
    }

    fun onDistanceUnitToggled() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nextUnit =
                    if (_qiblaState.value.distanceUnit == DistanceUnit.KILOMETERS) {
                        DistanceUnit.MILES
                    }
                    else {
                        DistanceUnit.KILOMETERS
                    }

                preferencesDataStore.setDistanceUnit(nextUnit)
            } catch (e: Exception) {
                _qiblaState.update { data ->
                    data.copy(
                        error = e.message
                    )
                }
            }
        }
    }
}