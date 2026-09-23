package com.example.athanly.appDataPreference.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.athanly.appData.domain.modelData.Muadhdhin
import com.example.athanly.appData.domain.modelData.enums.MuadhdhinId
import com.example.athanly.appData.domain.modelData.muadhhinList
import com.example.athanly.appData.domain.modelData.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Takes DataStore<Preferences> directly (not a Context it builds one from) so tests
// can hand it a real temp-file-backed instance — AppDataModule wires up the actual
// Context-bound DataStore for the running app.
class AppDataPreferencesDataStore(
    private val dataStore: DataStore<Preferences>
) {
    // Defaults to false (12h format) when the key is missing, i.e. first launch.
    fun observedIs24HourFormat(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[IS_24_HOUR_FORMAT_KEY] ?: false
        }
    }

    suspend fun setIs24HourFormat(is24HourFormat: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_24_HOUR_FORMAT_KEY] = is24HourFormat
        }
    }

    // Single source of truth for Hanafi mode — onboarding's Hanafi toggle writes here too,
    // so the choice made during onboarding survives past it instead of staying in-memory only.
    fun observedIsHanafiMode(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[IS_HANAFI_MODE_KEY] ?: false
        }
    }

    suspend fun setIsHanafiMode(isHanafiMode: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_HANAFI_MODE_KEY] = isHanafiMode
        }
    }

    // Language is a sealed class, not a native DataStore type, so it's stored by its
    // simple name (data object's auto-generated toString()) and parsed back on read.
    fun observedLanguage(): Flow<Language> {
        return dataStore.data.map { preferences ->
            when (preferences[LANGUAGE_KEY]) {
                "Arabic" -> Language.Arabic
                else -> Language.English
            }
        }
    }

    suspend fun setLanguage(language: Language) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language.toString()
        }
    }

    // Stored by MuadhdhinId (stable across list order/resource id changes) and resolved
    // back to the full Muadhdhin via muadhhinList, falling back to the first entry if the
    // key is missing or its id no longer matches anything in the list.
    fun observedSelectedMuadhdhin(): Flow<Muadhdhin> {
        return dataStore.data.map { preferences ->
            val storedId = preferences[SELECTED_MUADHDHIN_ID_KEY]

            storedId
                ?.let { runCatching { MuadhdhinId.valueOf(it) }.getOrNull() }
                ?.let { id -> muadhhinList.find { it.id == id } }
                ?: muadhhinList.first()
        }
    }

    suspend fun setSelectedMuadhdhin(muadhdhin: Muadhdhin) {
        dataStore.edit { preferences ->
            preferences[SELECTED_MUADHDHIN_ID_KEY] = muadhdhin.id.name
        }
    }

    // Gates AppRoute's start destination — false means onboarding hasn't been
    // completed yet, so it should be shown again the next time the app launches.
    fun observedHasCompletedOnboarding(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING_KEY] ?: false
        }
    }

    suspend fun setHasCompletedOnboarding(hasCompletedOnboarding: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING_KEY] = hasCompletedOnboarding
        }
    }

    // Single source of truth for "does the app have location permission" — written both at
    // onboarding grant time and re-checked on every Qibla screen visit, so it can't go stale
    // if permission is later revoked in system Settings. Defaults to false when missing.
    fun observedIsLocationPermissionGranted(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[IS_LOCATION_PERMISSION_GRANTED_KEY] ?: false
        }
    }

    suspend fun setIsLocationPermissionGranted(isLocationPermissionGranted: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_LOCATION_PERMISSION_GRANTED_KEY] = isLocationPermissionGranted
        }
    }

    companion object {
        private val IS_24_HOUR_FORMAT_KEY = booleanPreferencesKey("is_24_hour_format")
        private val IS_HANAFI_MODE_KEY = booleanPreferencesKey("is_hanafi_mode")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val SELECTED_MUADHDHIN_ID_KEY = stringPreferencesKey("selected_muadhdhin_id")
        private val HAS_COMPLETED_ONBOARDING_KEY = booleanPreferencesKey("has_completed_onboarding")
        private val IS_LOCATION_PERMISSION_GRANTED_KEY = booleanPreferencesKey("is_location_permission_granted")
    }
}