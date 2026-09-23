package com.example.athanly.ui.home.screens.qibla.viewModel

import app.cash.turbine.test
import com.example.athanly.appDataPreference.data.AppDataPreferencesDataStore
import com.example.athanly.connectivity.domain.repository.ConnectivityProvider
import com.example.athanly.qibla.local.data.QiblaPreferencesDataStore
import com.example.athanly.qibla.local.domain.modelData.Coordinates
import com.example.athanly.qibla.local.domain.modelData.enums.DistanceUnit
import com.example.athanly.qibla.local.domain.repository.CompassRepository
import com.example.athanly.qibla.local.domain.repository.UserLocationRepository
import com.example.athanly.qibla.remote.domain.repository.QiblaApiRepository
import com.example.athanly.qibla.userInfoCalculations.QiblaCalculator
import com.example.athanly.ui.home.viewModel.QiblaViewModel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QiblaViewModelTest {

    // Same reasoning as HomeViewModelTest: viewModelScope needs Dispatchers.Main
    // substituted before construction, and Unconfined so the init{} coroutine's
    // combine() runs eagerly instead of needing manual time-advancement.
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `qiblaState derives needle rotation from heading alone and distance-bearing from location and unit`() = runTest {
        val coordinates = Coordinates(latitude = 40.7128, longitude = -74.0060)
        val headingDegrees = 10f

        // mockk<T> { every { ... } returns ... } builds a fake implementation of the
        // interface/class where every method call returns a hardcoded value we control,
        // instead of running real GPS/sensor/DataStore code.
        val userLocationRepository = mockk<UserLocationRepository> {
            every {
                observedLocation()
            } returns flowOf(coordinates)
            every { hasLocationPermission() } returns true
        }

        val compassRepository = mockk<CompassRepository> {
            every {
                observedHeadingDegrees()
            } returns flowOf(headingDegrees)
        }

        val preferencesDataStore = mockk<QiblaPreferencesDataStore> {
            every {
                observedDistanceUnit()
            } returns flowOf(DistanceUnit.KILOMETERS)
        }

        // Bearing now comes from QiblaApiRepository (the Aladhan API), not a local
        // formula — coEvery stubs its suspend fetchQiblaBearing call with a fixed value.
        val expectedBearing = 136.14
        val qiblaApiRepository = mockk<QiblaApiRepository> {
            coEvery {
                fetchQiblaBearing(latitude = coordinates.latitude, longitude = coordinates.longitude)
            } returns expectedBearing
        }

        val connectivityProvider = mockk<ConnectivityProvider> {
            every { isOnline() } returns true
        }

        // `just Runs` stubs a suspend function that returns Unit — there's no return value
        // to hand back, just an "acknowledge this call happened" stub.
        val appDataPreferencesDataStore = mockk<AppDataPreferencesDataStore> {
            coEvery { setIsLocationPermissionGranted(any()) } just Runs
        }

        val viewModel = QiblaViewModel(
            userLocationRepository = userLocationRepository,
            compassRepository = compassRepository,
            preferencesDataStore = preferencesDataStore,
            qiblaApiRepository = qiblaApiRepository,
            connectivityProvider = connectivityProvider,
            appDataPreferencesDataStore = appDataPreferencesDataStore
        )

        viewModel.qiblaState.test {
            val state = awaitItem()

            // Distance is still computed via QiblaCalculator locally (already covered by
            // its own tests) — this test is only checking that QiblaViewModel wires its
            // inputs into the right state fields, not re-verifying the formula's math.
            val expectedDistanceKm = QiblaCalculator.distanceToKaaba(from = coordinates)

            // Needle is now purely heading — no bearing involved at all, since it's
            // decoupled from the Kaaba's direction (the coupling that used to exist here
            // — bearing minus heading — was the bug this rearchitecture fixes). Not negated:
            // it must match the device's native compass heading directly, e.g. facing due
            // east rotates the needle toward the fixed "E" label, not "W".
            assertEquals(headingDegrees, state.needleRotationDegrees, 0.01f)
            assertEquals(expectedDistanceKm, state.distanceValue, 0.01)
            assertEquals(DistanceUnit.KILOMETERS, state.distanceUnit)
            assertEquals(expectedBearing, state.qiblaDegrees, 0.01)
            assertEquals(QiblaCalculator.cardinalDirectionFor(bearingDegrees = expectedBearing), state.qiblaDirection)
        }
    }

    @Test
    fun `needleRotationDegrees updates on new heading emissions while qiblaDegrees stays untouched`() = runTest {
        val coordinates = Coordinates(latitude = 40.7128, longitude = -74.0060)

        // MutableStateFlow (vs flowOf's fixed single value) lets this test push a
        // second heading value after construction, simulating a live sensor update —
        // exactly the scenario the old coupled implementation got wrong.
        val headingFlow = MutableStateFlow(10f)

        val userLocationRepository = mockk<UserLocationRepository> {
            every { observedLocation() } returns flowOf(coordinates)
            every { hasLocationPermission() } returns true
        }
        val compassRepository = mockk<CompassRepository> {
            every { observedHeadingDegrees() } returns headingFlow
        }
        val preferencesDataStore = mockk<QiblaPreferencesDataStore> {
            every { observedDistanceUnit() } returns flowOf(DistanceUnit.KILOMETERS)
        }
        val qiblaApiRepository = mockk<QiblaApiRepository> {
            coEvery {
                fetchQiblaBearing(latitude = coordinates.latitude, longitude = coordinates.longitude)
            } returns 136.14
        }
        val connectivityProvider = mockk<ConnectivityProvider> {
            every { isOnline() } returns true
        }
        val appDataPreferencesDataStore = mockk<AppDataPreferencesDataStore> {
            coEvery { setIsLocationPermissionGranted(any()) } just Runs
        }

        val viewModel = QiblaViewModel(
            userLocationRepository = userLocationRepository,
            compassRepository = compassRepository,
            preferencesDataStore = preferencesDataStore,
            qiblaApiRepository = qiblaApiRepository,
            connectivityProvider = connectivityProvider,
            appDataPreferencesDataStore = appDataPreferencesDataStore
        )

        viewModel.qiblaState.test {
            val initialState = awaitItem()
            val qiblaDegreesBeforeHeadingChange = initialState.qiblaDegrees

            headingFlow.value = 45f

            // The needle's collect{} re-runs on this new emission, producing a new
            // QiblaUiState (different needleRotationDegrees), which StateFlow emits
            // as the next distinct item.
            val stateAfterHeadingChange = awaitItem()

            assertEquals(45f, stateAfterHeadingChange.needleRotationDegrees, 0.01f)
            // The whole point of the fix: a heading change must not touch qiblaDegrees.
            assertEquals(qiblaDegreesBeforeHeadingChange, stateAfterHeadingChange.qiblaDegrees, 0.01)
        }
    }

    @Test
    fun `qiblaDegrees only updates when recalculateQiblaDirection is called, not when location drifts on its own`() = runTest {
        val coordinatesA = Coordinates(latitude = 40.7128, longitude = -74.0060)
        val coordinatesB = Coordinates(latitude = 51.5074, longitude = -0.1278)

        // MutableStateFlow again — this simulates location changing in the background
        // (e.g. the user walking) without any explicit recalculation being requested.
        val locationFlow = MutableStateFlow(coordinatesA)

        val userLocationRepository = mockk<UserLocationRepository> {
            every { observedLocation() } returns locationFlow
            every { hasLocationPermission() } returns true
        }
        val compassRepository = mockk<CompassRepository> {
            every { observedHeadingDegrees() } returns flowOf(0f)
        }
        val preferencesDataStore = mockk<QiblaPreferencesDataStore> {
            every { observedDistanceUnit() } returns flowOf(DistanceUnit.KILOMETERS)
        }

        // Each coordinate gets its own stubbed bearing — coEvery matches on the exact
        // latitude/longitude args, so calling with coordinatesA vs coordinatesB returns
        // different fixed values, standing in for the real API returning a different
        // bearing once the phone's location has actually moved.
        val expectedBearingA = 58.4
        val expectedBearingB = 119.7
        val qiblaApiRepository = mockk<QiblaApiRepository> {
            coEvery {
                fetchQiblaBearing(latitude = coordinatesA.latitude, longitude = coordinatesA.longitude)
            } returns expectedBearingA
            coEvery {
                fetchQiblaBearing(latitude = coordinatesB.latitude, longitude = coordinatesB.longitude)
            } returns expectedBearingB
        }

        val connectivityProvider = mockk<ConnectivityProvider> {
            every { isOnline() } returns true
        }
        val appDataPreferencesDataStore = mockk<AppDataPreferencesDataStore> {
            coEvery { setIsLocationPermissionGranted(any()) } just Runs
        }

        val viewModel = QiblaViewModel(
            userLocationRepository = userLocationRepository,
            compassRepository = compassRepository,
            preferencesDataStore = preferencesDataStore,
            qiblaApiRepository = qiblaApiRepository,
            connectivityProvider = connectivityProvider,
            appDataPreferencesDataStore = appDataPreferencesDataStore
        )

        viewModel.qiblaState.test {
            val initialState = awaitItem()
            assertEquals(expectedBearingA, initialState.qiblaDegrees, 0.01)

            locationFlow.value = coordinatesB

            // Distance is still a live subscriber to location, so it reacts immediately
            // — this is the contrast that proves qiblaDegrees's staleness is deliberate,
            // not an accident of the mocks used.
            val stateAfterLocationDrift = awaitItem()
            val expectedDistanceB = QiblaCalculator.distanceToKaaba(from = coordinatesB)
            assertEquals(expectedDistanceB, stateAfterLocationDrift.distanceValue, 0.01)
            // qiblaDegrees is untouched by the location drift alone.
            assertEquals(expectedBearingA, stateAfterLocationDrift.qiblaDegrees, 0.01)

            viewModel.recalculateQiblaDirection()

            // Only now, after the explicit call, does qiblaDegrees catch up to the
            // Kaaba bearing from the phone's current (drifted) location.
            val stateAfterRecalculation = awaitItem()
            assertEquals(expectedBearingB, stateAfterRecalculation.qiblaDegrees, 0.01)
        }
    }

    @Test
    fun `qiblaState reflects offline and permission-denied from ConnectivityProvider and UserLocationRepository at init`() = runTest {
        val coordinates = Coordinates(latitude = 40.7128, longitude = -74.0060)

        val userLocationRepository = mockk<UserLocationRepository> {
            every { observedLocation() } returns flowOf(coordinates)
            // false here is the thing this test is actually about — everything else
            // below is just enough of a working setup that the rest of init{} doesn't
            // also throw and add noise to the assertion.
            every { hasLocationPermission() } returns false
        }
        val compassRepository = mockk<CompassRepository> {
            every { observedHeadingDegrees() } returns flowOf(0f)
        }
        val preferencesDataStore = mockk<QiblaPreferencesDataStore> {
            every { observedDistanceUnit() } returns flowOf(DistanceUnit.KILOMETERS)
        }
        val qiblaApiRepository = mockk<QiblaApiRepository> {
            coEvery { fetchQiblaBearing(latitude = coordinates.latitude, longitude = coordinates.longitude) } returns 136.14
        }
        val connectivityProvider = mockk<ConnectivityProvider> {
            every { isOnline() } returns false
        }
        val appDataPreferencesDataStore = mockk<AppDataPreferencesDataStore> {
            coEvery { setIsLocationPermissionGranted(any()) } just Runs
        }

        val viewModel = QiblaViewModel(
            userLocationRepository = userLocationRepository,
            compassRepository = compassRepository,
            preferencesDataStore = preferencesDataStore,
            qiblaApiRepository = qiblaApiRepository,
            connectivityProvider = connectivityProvider,
            appDataPreferencesDataStore = appDataPreferencesDataStore
        )

        viewModel.qiblaState.test {
            val state = awaitItem()
            assertFalse(state.isOnline)
            assertFalse(state.isLocationPermissionGranted)
        }
    }

    @Test
    fun `retryLocationAccess rechecks connectivity and permission, updating qiblaState with the fresh result`() = runTest {
        val coordinates = Coordinates(latitude = 40.7128, longitude = -74.0060)

        val userLocationRepository = mockk<UserLocationRepository> {
            every { observedLocation() } returns flowOf(coordinates)
            every { hasLocationPermission() } returns true
        }
        val compassRepository = mockk<CompassRepository> {
            every { observedHeadingDegrees() } returns flowOf(0f)
        }
        val preferencesDataStore = mockk<QiblaPreferencesDataStore> {
            every { observedDistanceUnit() } returns flowOf(DistanceUnit.KILOMETERS)
        }
        val qiblaApiRepository = mockk<QiblaApiRepository> {
            coEvery { fetchQiblaBearing(latitude = coordinates.latitude, longitude = coordinates.longitude) } returns 136.14
        }
        // Starts online — retryLocationAccess() is what's expected to notice it went offline.
        val connectivityProvider = mockk<ConnectivityProvider> {
            every { isOnline() } returns true
        }
        val appDataPreferencesDataStore = mockk<AppDataPreferencesDataStore> {
            coEvery { setIsLocationPermissionGranted(any()) } just Runs
        }

        val viewModel = QiblaViewModel(
            userLocationRepository = userLocationRepository,
            compassRepository = compassRepository,
            preferencesDataStore = preferencesDataStore,
            qiblaApiRepository = qiblaApiRepository,
            connectivityProvider = connectivityProvider,
            appDataPreferencesDataStore = appDataPreferencesDataStore
        )

        viewModel.qiblaState.test {
            val initialState = awaitItem()
            assertTrue(initialState.isOnline)

            // Re-stubbing the same mock function overrides its previous answer — simulates
            // the device having gone offline between the initial check and this retry, e.g.
            // after the user backgrounds the app and returns (ON_RESUME → retryLocationAccess()).
            every { connectivityProvider.isOnline() } returns false

            viewModel.retryLocationAccess()

            val stateAfterRetry = awaitItem()
            assertFalse(stateAfterRetry.isOnline)
        }
    }

    @Test
    fun `checkLocationAccessPreconditions persists the permission result to AppDataPreferencesDataStore`() = runTest {
        val coordinates = Coordinates(latitude = 40.7128, longitude = -74.0060)

        val userLocationRepository = mockk<UserLocationRepository> {
            every { observedLocation() } returns flowOf(coordinates)
            every { hasLocationPermission() } returns true
        }
        val compassRepository = mockk<CompassRepository> {
            every { observedHeadingDegrees() } returns flowOf(0f)
        }
        val preferencesDataStore = mockk<QiblaPreferencesDataStore> {
            every { observedDistanceUnit() } returns flowOf(DistanceUnit.KILOMETERS)
        }
        val qiblaApiRepository = mockk<QiblaApiRepository> {
            coEvery { fetchQiblaBearing(latitude = coordinates.latitude, longitude = coordinates.longitude) } returns 136.14
        }
        val connectivityProvider = mockk<ConnectivityProvider> {
            every { isOnline() } returns true
        }
        val appDataPreferencesDataStore = mockk<AppDataPreferencesDataStore> {
            coEvery { setIsLocationPermissionGranted(any()) } just Runs
        }

        QiblaViewModel(
            userLocationRepository = userLocationRepository,
            compassRepository = compassRepository,
            preferencesDataStore = preferencesDataStore,
            qiblaApiRepository = qiblaApiRepository,
            connectivityProvider = connectivityProvider,
            appDataPreferencesDataStore = appDataPreferencesDataStore
        )

        // The write happens inside a viewModelScope.launch(Dispatchers.IO) block, off the
        // UnconfinedTestDispatcher this test's other coroutines run on — coVerify's timeout
        // parameter polls with real time for up to 1 second instead of asserting instantly,
        // since there's no virtual-time control over the real IO dispatcher.
        coVerify(timeout = 1_000) {
            appDataPreferencesDataStore.setIsLocationPermissionGranted(true)
        }
    }
}
