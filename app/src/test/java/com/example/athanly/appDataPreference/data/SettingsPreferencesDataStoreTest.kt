package com.example.athanly.appDataPreference.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.example.athanly.appData.domain.modelData.enums.MuadhdhinId
import com.example.athanly.appData.domain.modelData.muadhhinList
import com.example.athanly.appData.domain.modelData.Language
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// TemporaryFolder (JUnit4 @Rule) creates a real, fresh directory before each test and
// deletes it after — DataStore needs an actual file on disk, this gives it one without
// touching real app storage or leaking state between tests.
//
// PreferenceDataStoreFactory.create(produceFile = ...) builds a real DataStore<Preferences>
// backed by that temp file — same factory the real DI module (AppDataModule) will use,
// just pointed at a test file instead of the app's real preferences file.
//
// Turbine's `.test { }` on a Flow lets us call awaitItem() to pull the next emission and
// assert on it, instead of manually collecting into a list — it also fails the test if an
// emission never arrives (avoids trivially-passing async tests).
class SettingsPreferencesDataStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun settingsPreferencesDataStore(): AppDataPreferencesDataStore {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("test_settings_preferences.preferences_pb") }
        )
        return AppDataPreferencesDataStore(dataStore = dataStore)
    }

    // --- Time Format ---

    @Test
    fun `observedIs24HourFormat defaults to false when nothing has been stored yet`() = runTest {
        settingsPreferencesDataStore().observedIs24HourFormat().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setIs24HourFormat persists true and observedIs24HourFormat reflects it on the next emission`() = runTest {
        val preferencesDataStore = settingsPreferencesDataStore()

        preferencesDataStore.observedIs24HourFormat().test {
            assertEquals(false, awaitItem()) // initial default, file is still empty

            preferencesDataStore.setIs24HourFormat(true)

            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Hanafi Mode (Asr Calculation) ---
    // Same shape as Time Format above — this is also the single source of truth that
    // onboarding's Hanafi toggle will write through to, so its value survives onboarding.

    @Test
    fun `observedIsHanafiMode defaults to false when nothing has been stored yet`() = runTest {
        settingsPreferencesDataStore().observedIsHanafiMode().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setIsHanafiMode persists true and observedIsHanafiMode reflects it on the next emission`() = runTest {
        val preferencesDataStore = settingsPreferencesDataStore()

        preferencesDataStore.observedIsHanafiMode().test {
            assertEquals(false, awaitItem()) // initial default, file is still empty

            preferencesDataStore.setIsHanafiMode(true)

            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Language ---
    // Language has no native DataStore type, so (like Qibla's DistanceUnit) it's stored
    // as a string and parsed back, falling back to English if the key is missing or unparseable.

    @Test
    fun `observedLanguage defaults to English when nothing has been stored yet`() = runTest {
        settingsPreferencesDataStore().observedLanguage().test {
            assertEquals(Language.English, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLanguage persists Arabic and observedLanguage reflects it on the next emission`() = runTest {
        val preferencesDataStore = settingsPreferencesDataStore()

        preferencesDataStore.observedLanguage().test {
            assertEquals(Language.English, awaitItem()) // initial default, file is still empty

            preferencesDataStore.setLanguage(Language.Arabic)

            assertEquals(Language.Arabic, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Selected Muadhin ---
    // Stored by MuadhdhinId (stable, list-order-independent) and resolved back to the
    // full Muadhdhin via muadhhinList, falling back to the first entry (Makkah) if the
    // key is missing or its id no longer matches anything in the list.

    @Test
    fun `observedSelectedMuadhdhin defaults to the first list entry when nothing has been stored yet`() = runTest {
        settingsPreferencesDataStore().observedSelectedMuadhdhin().test {
            assertEquals(muadhhinList.first(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSelectedMuadhdhin persists Kuwait and observedSelectedMuadhdhin reflects it on the next emission`() = runTest {
        val preferencesDataStore = settingsPreferencesDataStore()
        val kuwait = muadhhinList.first { it.id == MuadhdhinId.ISTANBUL }

        preferencesDataStore.observedSelectedMuadhdhin().test {
            assertEquals(muadhhinList.first(), awaitItem()) // initial default, file is still empty

            preferencesDataStore.setSelectedMuadhdhin(kuwait)

            assertEquals(kuwait, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Onboarding Complete ---
    // Gates AppRoute's start destination — false means the app hasn't finished
    // onboarding yet, so it should be shown again on the next launch.

    @Test
    fun `observedHasCompletedOnboarding defaults to false when nothing has been stored yet`() = runTest {
        settingsPreferencesDataStore().observedHasCompletedOnboarding().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setHasCompletedOnboarding persists true and observedHasCompletedOnboarding reflects it on the next emission`() = runTest {
        val preferencesDataStore = settingsPreferencesDataStore()

        preferencesDataStore.observedHasCompletedOnboarding().test {
            assertEquals(false, awaitItem()) // initial default, file is still empty

            preferencesDataStore.setHasCompletedOnboarding(true)

            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Location Permission Granted ---
    // Single source of truth for "does the app have location permission" — written both at
    // onboarding grant time (OnBoardingViewModel) and re-checked on every Qibla screen
    // visit (QiblaViewModel), so it can't go stale if permission is later revoked in system
    // Settings. Defaults to false, same shape as HasCompletedOnboarding above.

    @Test
    fun `observedIsLocationPermissionGranted defaults to false when nothing has been stored yet`() = runTest {
        settingsPreferencesDataStore().observedIsLocationPermissionGranted().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setIsLocationPermissionGranted persists true and observedIsLocationPermissionGranted reflects it on the next emission`() = runTest {
        val preferencesDataStore = settingsPreferencesDataStore()

        preferencesDataStore.observedIsLocationPermissionGranted().test {
            assertEquals(false, awaitItem()) // initial default, file is still empty

            preferencesDataStore.setIsLocationPermissionGranted(true)

            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}