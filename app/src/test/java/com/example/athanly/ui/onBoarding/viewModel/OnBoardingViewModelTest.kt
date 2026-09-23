package com.example.athanly.ui.onBoarding.viewModel

import android.content.Context
import app.cash.turbine.test
import com.example.athanly.appData.domain.modelData.Language
import com.example.athanly.appData.domain.modelData.enums.MuadhdhinId
import com.example.athanly.appData.domain.modelData.muadhhinList
import com.example.athanly.appData.domain.repository.AthanPreviewPlayer
import com.example.athanly.appDataPreference.data.AppDataPreferencesDataStore
import com.example.athanly.athanTime.local.domain.repository.AthanTimeRepository
import com.example.athanly.location.domain.repository.LocationProvider
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OnBoardingViewModelTest {
    // Same reasoning as SettingsViewModelTest: viewModelScope needs Dispatchers.Main
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

    // athanTimeRepository defaults to observeCity() emitting null — the ViewModel's own
    // init{} collects that Flow unconditionally, so every construction needs at least this
    // much stubbed or it NPEs on a null Flow. locationProvider/context default to plain
    // mockk() since none of the tests below exercise the location-saving functions that
    // actually use them.
    private fun onBoardingViewModel(
        preferencesDataStore: AppDataPreferencesDataStore = mockk {
            every { observedLanguage() } returns flowOf(Language.English)
            every { observedSelectedMuadhdhin() } returns flowOf(muadhhinList.first())
            every { observedIsHanafiMode() } returns flowOf(false)
        },
        athanTimeRepository: AthanTimeRepository = mockk {
            every { observeCity() } returns flowOf(null)
        },
        locationProvider: LocationProvider = mockk(),
        athanPreviewPlayer: AthanPreviewPlayer = mockk(),
        context: Context = mockk(),
    ) = OnBoardingViewModel(
        preferencesDataStore = preferencesDataStore,
        athanTimeRepository = athanTimeRepository,
        locationProvider = locationProvider,
        athanPreviewPlayer = athanPreviewPlayer,
        context = context,
    )

    @Test
    fun `appDataState combines language, selectedMuadhdhin, and isHanafiMode from the DataStore`() = runTest {
        val kuwait = muadhhinList.first { it.id == MuadhdhinId.ISTANBUL }

        // Every onboarding screen that used to have its own local UiState (Welcome,
        // AthanSelection, Calculations) now reads straight off this shared state instead,
        // so the ViewModel needs all three of its source flows stubbed even in a single test.
        val preferencesDataStore = mockk<AppDataPreferencesDataStore> {
            every { observedLanguage() } returns flowOf(Language.Arabic)
            every { observedSelectedMuadhdhin() } returns flowOf(kuwait)
            every { observedIsHanafiMode() } returns flowOf(true)
        }

        val viewModel = onBoardingViewModel(preferencesDataStore = preferencesDataStore)

        viewModel.appDataState.test {
            val state = awaitItem()

            assertEquals(Language.Arabic, state.selectedLanguage)
            assertEquals(kuwait, state.selectedMuadhdhin)
            assertEquals(true, state.isHanafiMode)
        }
    }

    @Test
    fun `onLanguageSelected writes the given language through to the DataStore`() = runTest {
        val preferencesDataStore = mockk<AppDataPreferencesDataStore> {
            every { observedLanguage() } returns flowOf(Language.English)
            every { observedSelectedMuadhdhin() } returns flowOf(muadhhinList.first())
            every { observedIsHanafiMode() } returns flowOf(false)
            coEvery { setLanguage(any()) } just Runs
        }

        val viewModel = onBoardingViewModel(preferencesDataStore = preferencesDataStore)

        viewModel.onLanguageSelected(language = Language.Arabic)

        coVerify { preferencesDataStore.setLanguage(Language.Arabic) }
    }

    @Test
    fun `onMuadhhinSelected writes the given Muadhdhin through to the DataStore`() = runTest {
        val kuwait = muadhhinList.first { it.id == MuadhdhinId.ISTANBUL }

        val preferencesDataStore = mockk<AppDataPreferencesDataStore> {
            every { observedLanguage() } returns flowOf(Language.English)
            every { observedSelectedMuadhdhin() } returns flowOf(muadhhinList.first())
            every { observedIsHanafiMode() } returns flowOf(false)
            coEvery { setSelectedMuadhdhin(any()) } just Runs
        }

        val viewModel = onBoardingViewModel(preferencesDataStore = preferencesDataStore)

        viewModel.onMuadhhinSelected(muadhdhin = kuwait)

        coVerify { preferencesDataStore.setSelectedMuadhdhin(kuwait) }
    }

    @Test
    fun `onHanafiModeToggled writes the flipped value through to the DataStore`() = runTest {
        // isHanafiMode observed as false, so toggling once should write true.
        val preferencesDataStore = mockk<AppDataPreferencesDataStore> {
            every { observedLanguage() } returns flowOf(Language.English)
            every { observedSelectedMuadhdhin() } returns flowOf(muadhhinList.first())
            every { observedIsHanafiMode() } returns flowOf(false)
            coEvery { setIsHanafiMode(any()) } just Runs
        }

        val viewModel = onBoardingViewModel(preferencesDataStore = preferencesDataStore)

        viewModel.onHanafiModeToggled()

        coVerify { preferencesDataStore.setIsHanafiMode(true) }
    }

    @Test
    fun `onOnboardingComplete writes true through to the DataStore`() = runTest {
        val preferencesDataStore = mockk<AppDataPreferencesDataStore> {
            every { observedLanguage() } returns flowOf(Language.English)
            every { observedSelectedMuadhdhin() } returns flowOf(muadhhinList.first())
            every { observedIsHanafiMode() } returns flowOf(false)
            coEvery { setHasCompletedOnboarding(any()) } just Runs
        }

        val viewModel = onBoardingViewModel(preferencesDataStore = preferencesDataStore)

        viewModel.onOnboardingComplete()

        coVerify { preferencesDataStore.setHasCompletedOnboarding(true) }
    }

    @Test
    fun `notificationsState defaults to permission not granted`() = runTest {
        val preferencesDataStore = mockk<AppDataPreferencesDataStore> {
            every { observedLanguage() } returns flowOf(Language.English)
            every { observedSelectedMuadhdhin() } returns flowOf(muadhhinList.first())
            every { observedIsHanafiMode() } returns flowOf(false)
        }

        val viewModel = onBoardingViewModel(preferencesDataStore = preferencesDataStore)

        // Turbine's .test {} subscribes to the StateFlow and lets us step through
        // its emissions one at a time via awaitItem(), instead of manually collecting
        viewModel.notificationsState.test {
            val state = awaitItem() // the StateFlow's current value, emitted immediately
            assertEquals(false, state.isPermissionGranted)
        }
    }

    @Test
    fun `onNotificationPermissionResult(true) updates isPermissionGranted to true`() = runTest {
        val preferencesDataStore = mockk<AppDataPreferencesDataStore> {
            every { observedLanguage() } returns flowOf(Language.English)
            every { observedSelectedMuadhdhin() } returns flowOf(muadhhinList.first())
            every { observedIsHanafiMode() } returns flowOf(false)
        }

        val viewModel = onBoardingViewModel(preferencesDataStore = preferencesDataStore)

        viewModel.notificationsState.test {
            awaitItem() // consume the initial default state first

            viewModel.onNotificationPermissionResult(isGranted = true)

            // .update {} on the ViewModel's MutableStateFlow emits a new value,
            // which Turbine surfaces here as the next item to await
            val updated = awaitItem()
            assertEquals(true, updated.isPermissionGranted)
        }
    }

    @Test
    fun `onLocationPermissionResult writes the grant result through to the DataStore`() = runTest {
        val preferencesDataStore = mockk<AppDataPreferencesDataStore> {
            every { observedLanguage() } returns flowOf(Language.English)
            every { observedSelectedMuadhdhin() } returns flowOf(muadhhinList.first())
            every { observedIsHanafiMode() } returns flowOf(false)
            coEvery { setIsLocationPermissionGranted(any()) } just Runs
        }

        // fetchAndSaveCurrentLocation() also fires when isGranted is true, so this needs
        // locationProvider stubbed too — its result isn't what this test is checking, just
        // enough of a working answer that it doesn't matter for the assertion below.
        val locationProvider = mockk<LocationProvider> {
            coEvery { getCurrentCity() } returns null
        }

        val viewModel = onBoardingViewModel(
            preferencesDataStore = preferencesDataStore,
            locationProvider = locationProvider
        )

        viewModel.onLocationPermissionResult(isGranted = true)

        coVerify { preferencesDataStore.setIsLocationPermissionGranted(true) }
    }
}