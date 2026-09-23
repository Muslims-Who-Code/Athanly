package com.example.athanly.qibla.data

import app.cash.turbine.test
import com.example.athanly.location.domain.repository.LocationProvider
import com.example.athanly.qibla.local.data.GpsUserLocationRepository
import com.example.athanly.qibla.local.domain.modelData.Coordinates
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsUserLocationRepositoryTest {
    @Test
    fun `observedLocation emits the coordinates LocationProvider returns`() = runTest {
        // mockk<LocationProvider>() fakes the interface so this test never touches real
        // GPS hardware — we control exactly what "the device's location" is for this test.
        val locationProvider = mockk<LocationProvider>()

        // coEvery {} stubs a *suspend* function (plain `every` is for non-suspend calls).
        // This says: whenever getCurrentCoordinates() is called on our fake, return this
        // fixed coordinate instead of doing a real GPS lookup.
        coEvery { locationProvider.getCurrentCoordinates() } returns Coordinates(
            latitude = 30.0444,
            longitude = 31.2357
        )

        val repository = GpsUserLocationRepository(locationProvider)

        // .test {} (Turbine) subscribes to the Flow so we can assert its emissions one
        // at a time, instead of manually collecting into a list.
        repository.observedLocation().test {
            // awaitItem() suspends until the Flow emits, then returns that value.
            val coordinates = awaitItem()
            assertEquals(Coordinates(latitude = 30.0444, longitude = 31.2357), coordinates)

            // This is a one-shot GPS fetch, not a live location stream, so after the
            // single emission the Flow should finish — same shape as MockUserLocationRepository.
            awaitComplete()
        }
    }

    @Test
    fun `observedLocation throws when LocationProvider has no fix`() = runTest {
        val locationProvider = mockk<LocationProvider>()

        // Stubbing a null return — this is the "no GPS fix yet" case (permission denied,
        // location off, or just no result back in time).
        coEvery { locationProvider.getCurrentCoordinates() } returns null

        val repository = GpsUserLocationRepository(locationProvider)

        repository.observedLocation().test {
            // awaitError() asserts the Flow terminated via an exception rather than a
            // normal emission/completion. QiblaViewModel already wraps every subscription
            // to this Flow in try/catch and surfaces e.message as qiblaState.error, so
            // throwing here (instead of silently emitting nothing) is what lets that
            // existing error path actually show something to the user.
            awaitError()
        }
    }

    @Test
    fun `hasLocationPermission returns true when LocationProvider has permission`() {
        val locationProvider = mockk<LocationProvider>()

        // every {} (not coEvery) stubs a *non-suspend* function — hasLocationPermission()
        // is a plain synchronous check, unlike getCurrentCoordinates() above.
        every { locationProvider.hasLocationPermission() } returns true

        val repository = GpsUserLocationRepository(locationProvider)

        // This method just forwards the result — the test only needs to prove the
        // delegation happens correctly, not re-test LocationProviderImpl's own permission logic.
        assertTrue(repository.hasLocationPermission())
    }

    @Test
    fun `hasLocationPermission returns false when LocationProvider has no permission`() {
        val locationProvider = mockk<LocationProvider>()
        every { locationProvider.hasLocationPermission() } returns false

        val repository = GpsUserLocationRepository(locationProvider)

        assertFalse(repository.hasLocationPermission())
    }
}