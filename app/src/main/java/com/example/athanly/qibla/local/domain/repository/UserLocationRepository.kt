package com.example.athanly.qibla.local.domain.repository

import com.example.athanly.qibla.local.domain.modelData.Coordinates
import kotlinx.coroutines.flow.Flow

// Abstraction over "where is the user right now" — mock-backed today (MockUserLocationRepository),
// swappable for a real GPS/City-backed implementation later without QiblaViewModel changing at all.
interface UserLocationRepository {
    fun observedLocation(): Flow<Coordinates>

    // "Can I get the user's location" intrinsically includes "do I have permission to try" —
    // exposed here so QiblaViewModel can check it directly instead of inferring it from
    // observedLocation()'s generic "no fix" failure.
    fun hasLocationPermission(): Boolean
}