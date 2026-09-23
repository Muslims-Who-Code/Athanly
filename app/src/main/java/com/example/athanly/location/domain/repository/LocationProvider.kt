package com.example.athanly.location.domain.repository

import com.example.athanly.athanTime.local.domain.modelData.City
import com.example.athanly.qibla.local.domain.modelData.Coordinates

// Wraps FusedLocationProviderClient + reverse geocoding behind a domain-friendly interface —
// the onboarding Location screen never touches Google Play Services types directly.
interface LocationProvider {

    // Null means no fix was obtained (permission denied, location off, or the device
    // couldn't resolve one in time) — the caller falls back to PresetCityPicker, it's not
    // an exceptional case worth throwing over.
    suspend fun getCurrentCity(): City?

    // Same GPS fix as getCurrentCity(), minus the reverse-geocode step — Qibla only needs
    // a lat/lng to do its bearing math, so it skips work getCurrentCity() would waste on it.
    suspend fun getCurrentCoordinates(): Coordinates?

    // Exposed separately from the fetch methods above so a caller can check permission state
    // on its own (e.g. to distinguish "permission denied" from "no GPS fix yet" for the Qibla
    // blocker) without needing to trigger an actual location fetch just to find out.
    fun hasLocationPermission(): Boolean
}