package com.example.athanly.ui.onBoarding.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.athanly.appData.domain.modelData.AppDataUiState
import com.example.athanly.appData.domain.modelData.Language
import com.example.athanly.appData.domain.modelData.Muadhdhin
import com.example.athanly.appData.domain.modelData.enums.MuadhdhinId
import com.example.athanly.appData.domain.repository.AthanPreviewPlayer
import com.example.athanly.appDataPreference.data.AppDataPreferencesDataStore
import com.example.athanly.athanTime.local.domain.modelData.City
import com.example.athanly.athanTime.local.domain.repository.AthanTimeRepository
import com.example.athanly.location.domain.modelData.LocationUiState
import com.example.athanly.location.domain.repository.LocationProvider
import com.example.athanly.notifications.domain.modelData.NotificationsUiState
import com.example.athanly.notifications.worker.PRAYER_SYNC_WORK_NAME
import com.example.athanly.notifications.worker.PrayerScheduleSyncWorker
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.athanly.location.domain.modelData.presetCities
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnBoardingViewModel @Inject constructor(
    private val preferencesDataStore: AppDataPreferencesDataStore,
    private val athanTimeRepository: AthanTimeRepository,
    private val locationProvider: LocationProvider,
    private val athanPreviewPlayer: AthanPreviewPlayer,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // Welcome, AthanSelection, and Calculations used to each hold their own local
    // UiState (WelcomeUiState/AthanSelectionUiState/CalculationsUiState) that duplicated
    // a single field AppDataUiState already carries. Reading/writing straight off
    // AppDataUiState here removes that duplication and matches how SettingsViewModel
    // already drives the same three values on the Settings screen.
    private val _appDataState = MutableStateFlow(AppDataUiState())
    val appDataState: StateFlow<AppDataUiState> = _appDataState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(
                    preferencesDataStore.observedLanguage(),
                    preferencesDataStore.observedSelectedMuadhdhin(),
                    preferencesDataStore.observedIsHanafiMode(),
                ) { language, selectedMuadhdhin, isHanafiMode ->
                    AppDataUiState(
                        selectedLanguage = language,
                        selectedMuadhdhin = selectedMuadhdhin,
                        isHanafiMode = isHanafiMode,
                    )
                }.collect { combinedState ->
                    // hasSelectedMuadhdhin isn't backed by DataStore — preserved here so this
                    // combine (re-triggered by onMuadhhinSelected's own setSelectedMuadhdhin
                    // write, among other things) doesn't reset it back to false
                    _appDataState.update { current ->
                        combinedState.copy(
                            hasSelectedMuadhdhin = current.hasSelectedMuadhdhin
                        )
                    }
                }
            } catch (e: Exception) {
                _appDataState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Called whenever the user taps a language card
    fun onLanguageSelected(language: Language) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                preferencesDataStore.setLanguage(language)
            } catch (e: Exception) {
                _appDataState.update { it.copy(error = e.message) }
            }
        }
    }

    // Tapping a Muadhdhin card both selects it (saved immediately, same as before) and
    // previews it — tapping the one already playing stops it instead of restarting it.
    // previewingMuadhdhinId lives in its own StateFlow rather than on appDataState, since
    // appDataState's combine above fully replaces its value on every DataStore emission
    // (including this function's own setSelectedMuadhdhin write) and would otherwise wipe
    // out the pause icon it just set.
    private val _previewingMuadhdhinId = MutableStateFlow<MuadhdhinId?>(null)
    val previewingMuadhdhinId: StateFlow<MuadhdhinId?> = _previewingMuadhdhinId.asStateFlow()

    fun onMuadhhinSelected(muadhdhin: Muadhdhin) {
        if (_previewingMuadhdhinId.value == muadhdhin.id) {
            stopPreview()
            return
        }

        // MediaPlayer calls are made directly here (on the caller's thread, which is
        // Compose's Main thread for an onClick) rather than inside the IO launch below,
        // since MediaPlayer expects to be driven from a single consistent thread.
        athanPreviewPlayer.play(
            audioRes = muadhdhin.otherAudioRes,
            onCompletion = {
                // Only clear if this clip is still the one "playing" — guards against a
                // stale completion callback clearing a newer selection's playing state
                if (_previewingMuadhdhinId.value == muadhdhin.id) {
                    _previewingMuadhdhinId.update { null }
                }
            }
        )
        _previewingMuadhdhinId.update { muadhdhin.id }
        _appDataState.update { data ->
            data.copy(
                hasSelectedMuadhdhin = true
            ) 
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                preferencesDataStore.setSelectedMuadhdhin(muadhdhin)
            } catch (e: Exception) {
                _appDataState.update { it.copy(error = e.message) }
            }
        }
    }

    // Called when the Athan Selection step is left, so a preview doesn't keep playing
    // in the background on a later onboarding step
    fun stopPreview() {
        athanPreviewPlayer.stop()
        _previewingMuadhdhinId.update { null }
    }

    override fun onCleared() {
        super.onCleared()
        athanPreviewPlayer.stop()
    }

    // Called whenever the user flips the Hanafi mode toggle — reads the last known
    // value straight off appDataState (sourced from the DataStore) and writes the flip.
    fun onHanafiModeToggled() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                preferencesDataStore.setIsHanafiMode(!_appDataState.value.isHanafiMode)
            } catch (e: Exception) {
                _appDataState.update { it.copy(error = e.message) }
            }
        }
    }

    // Called once the Greeting screen's fade sequence finishes — persists that onboarding
    // is done so AppViewModel routes straight to Home on every later launch.
    fun onOnboardingComplete() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                preferencesDataStore.setHasCompletedOnboarding(true)
            } catch (e: Exception) {
                _appDataState.update { it.copy(error = e.message) }
            }
        }
    }

    // Notification permission result isn't persisted (see AppDataPreferencesDataStore) —
    // it's read live from Android's own permission APIs, so this stays local-only state.
    private val _notificationsState = MutableStateFlow(NotificationsUiState())
    val notificationsState: StateFlow<NotificationsUiState> = _notificationsState.asStateFlow()

    // Called with the OS permission dialog result on the final onboarding step
    fun onNotificationPermissionResult(isGranted: Boolean) {
        _notificationsState.update { current ->
            current.copy(
                isPermissionGranted = isGranted
            )
        }
    }

    // hasSavedCity is sourced from Room (via observeCity()), not set manually — the
    // OnBoardingLayout scaffold needs to gate its shared Continue button on this too, and it
    // holds its own separate OnBoardingViewModel instance (hiltViewModel() is scoped per nav
    // destination, same as appDataState above). Reading it off the persisted city, same as
    // appDataState reads off DataStore, means every instance agrees regardless of which
    // screen actually performed the save.
    private val _locationState = MutableStateFlow(LocationUiState())
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()

    init {
        viewModelScope.launch {
            athanTimeRepository.observeCity().collect { city ->
                _locationState.update { current ->
                    current.copy(
                        hasSavedCity = city != null
                    )
                }
            }
        }
    }

    // Called with the OS permission dialog result on the Location onboarding step. Granted
    // kicks off the GPS fetch-and-save; denied leaves hasSavedCity false so the screen falls
    // back to forcing a PresetCityPicker choice. Also persists the result to DataStore — the
    // single source of truth QiblaViewModel re-checks against on every Qibla screen visit,
    // so a later revocation in system Settings doesn't leave this onboarding-time write stale.
    fun onLocationPermissionResult(isGranted: Boolean) {
        _locationState.update { current ->
            current.copy(
                isPermissionGranted = isGranted
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                preferencesDataStore.setIsLocationPermissionGranted(isGranted)
            } catch (e: Exception) {
                _locationState.update { current ->
                    current.copy(
                        error = e.message
                    )
                }
            }
        }

        if (isGranted) {
            fetchAndSaveCurrentLocation()
        }
    }

    private fun fetchAndSaveCurrentLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            _locationState.update { current ->
                current.copy(isLoading = true)
            }
            try {
                val city = locationProvider.getCurrentCity()
                if (city != null) {
                    athanTimeRepository.saveCity(city)
                    enqueueImmediateScheduleSync()
                }
                // city == null (no fix obtained despite permission) leaves hasSavedCity false —
                // the observeCity() collector above never sees a save, so the screen's forced
                // fallback to PresetCityPicker handles it, same as an outright permission denial.
            } catch (e: Exception) {
                _locationState.update { current ->
                    current.copy(
                        error = e.message
                    )
                }
            } finally {
                _locationState.update { current ->
                    current.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    // Called when the user picks a city from PresetCityPicker (permission denied/skipped path)
    fun onPresetCitySelected(city: City) {
        _locationState.update { current ->
            current.copy(
                city = city,
                hasSavedCity = true
            )
        }
    }

    fun updatePresetCity(city: City) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                athanTimeRepository.saveCity(city = city)
                enqueueImmediateScheduleSync()
            } catch (e: Exception) {
                _locationState.update { current ->
                    current.copy(
                        error = e.message
                    )
                }
            }
        }
    }

    fun undoPresetCitySelected() {
        _locationState.update { current ->
            current.copy(
                city = null,
                hasSavedCity = false
            )
        }
    }

    // Both saveCity call sites above land here right after a successful save. REPLACE (not
    // KEEP, which AthanlyApplication's app-start enqueue uses) is deliberate: onboarding just
    // saved the FIRST city this install has ever had, so the existing city-less chain
    // (scheduled with nothing to do until now) needs to be superseded with a real run right
    // away, rather than waiting for its already-scheduled midnight fire.
    private fun enqueueImmediateScheduleSync() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            PRAYER_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PrayerScheduleSyncWorker>().build()
        )
    }
}