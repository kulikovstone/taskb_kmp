package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.settings.api.IItemGroupSettings
import com.mkulikov.taskb.feature.settings.api.IThemeSettings
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.model.ThemeMode
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    private val themeSettings = TestThemeSettings()
    private val city = AppLocation(
        name = "Paris, France",
        latitude = 48.85,
        longitude = 2.35,
        timeZone = "Europe/Paris",
    )

    @Test
    fun `search is debounced and selecting or clearing a city saves the shared location`(): TestResult {
        return runTest {
            val repository = Locations(city = city)
            SettingsStore(
                locations = repository,
                groupSettings = GroupSettings(),
                themeSettings = themeSettings,
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            ).use { store ->
                runCurrent()
                assertFalse(actual = store.state.value.isLoading)
                store.accept(intent = SettingsIntent.Search(query = "Pa"))
                runCurrent()
                advanceTimeBy(delayTimeMillis = 200)
                store.accept(intent = SettingsIntent.Search(query = "Paris"))
                runCurrent()
                advanceTimeBy(delayTimeMillis = 349)
                runCurrent()
                assertTrue(actual = repository.searches.isEmpty())
                advanceTimeBy(delayTimeMillis = 1)
                runCurrent()
                assertEquals(expected = listOf(element = "Paris"), actual = repository.searches)
                assertEquals(expected = listOf(element = city), actual = store.state.value.results)
                store.accept(intent = SettingsIntent.SelectLocation(location = city))
                runCurrent()
                assertEquals(expected = city, actual = store.state.value.location)
                assertEquals(expected = "", actual = store.state.value.query)
                store.accept(intent = SettingsIntent.ClearLocation)
                runCurrent()
                assertNull(actual = store.state.value.location)
            }
        }
    }

    @Test
    fun `failed saves retain the selected city and search can recover after a network failure`(): TestResult {
        return runTest {
            val repository = Locations(city = city).apply {
                location.value = city
                failSearch = true
                failSave = true
            }
            SettingsStore(
                locations = repository,
                groupSettings = GroupSettings(),
                themeSettings = themeSettings,
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            ).use { store ->
                runCurrent()
                store.accept(intent = SettingsIntent.Search(query = "Paris"))
                advanceTimeBy(delayTimeMillis = 350)
                runCurrent()
                assertNotNull(actual = store.state.value.error)
                repository.failSearch = false
                store.accept(intent = SettingsIntent.Search(query = "Paris"))
                advanceTimeBy(delayTimeMillis = 350)
                runCurrent()
                assertEquals(expected = listOf(element = city), actual = store.state.value.results)
                store.accept(intent = SettingsIntent.ClearLocation)
                runCurrent()
                assertEquals(expected = city, actual = store.state.value.location)
                assertFalse(actual = store.state.value.isSaving)
                assertNotNull(actual = store.state.value.error)
                repository.failSave = false
                store.accept(intent = SettingsIntent.ClearLocation)
                runCurrent()
                assertNull(actual = store.state.value.location)
                assertNull(actual = store.state.value.error)
            }
        }
    }

    @Test
    fun `old search results cannot replace results for a newer query and close cancels pending search`(): TestResult {
        return runTest {
            val state = SettingsState(query = "London", isSearching = true)
            assertEquals(
                expected = state,
                actual = reduceSettings(
                    state = state,
                    message = SettingsMessage.SearchResult(query = "Paris", results = listOf(element = city)),
                ),
            )
            val repository = Locations(city = city).apply { pending = true }
            val store = SettingsStore(
                locations = repository,
                groupSettings = GroupSettings(),
                themeSettings = themeSettings,
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            )
            store.accept(intent = SettingsIntent.Search(query = "Paris"))
            advanceTimeBy(delayTimeMillis = 350)
            runCurrent()
            store.close()
            runCurrent()
            assertTrue(actual = repository.cancelled)
        }
    }

    @Test
    fun `switch loads the saved value and a failed change can be retried`(): TestResult {
        return runTest {
            val settings = GroupSettings().apply { enabled = false }
            SettingsStore(
                locations = Locations(city = city),
                groupSettings = settings,
                themeSettings = themeSettings,
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            ).use { store ->
                runCurrent()
                assertEquals(expected = false, actual = store.state.value.rememberLastGroup)
                settings.failWrite = true
                store.accept(intent = SettingsIntent.SetRememberLastGroup(enabled = true))
                runCurrent()
                assertTrue(actual = store.state.value.groupSettingError)
                assertEquals(expected = false, actual = store.state.value.rememberLastGroup)
                settings.failWrite = false
                store.accept(intent = SettingsIntent.SetRememberLastGroup(enabled = true))
                runCurrent()
                assertTrue(actual = settings.enabled)
                assertEquals(expected = true, actual = store.state.value.rememberLastGroup)
                assertFalse(actual = store.state.value.groupSettingError)
            }
        }
    }

    @Test
    fun `theme changes only after a successful save and failed changes can be retried`(): TestResult = runTest {
        SettingsStore(
            locations = Locations(city = city),
            groupSettings = GroupSettings(),
            themeSettings = themeSettings,
            dispatcher = StandardTestDispatcher(scheduler = testScheduler),
        ).use { store ->
            themeSettings.failWrite = true
            store.accept(intent = SettingsIntent.SetThemeMode(mode = ThemeMode.Dark))
            runCurrent()
            assertEquals(expected = ThemeMode.System, actual = store.state.value.themeMode)
            assertTrue(actual = store.state.value.themeSaveFailed)
            themeSettings.failWrite = false
            store.accept(intent = SettingsIntent.SetThemeMode(mode = ThemeMode.Dark))
            runCurrent()
            assertEquals(expected = ThemeMode.Dark, actual = store.state.value.themeMode)
            assertFalse(actual = store.state.value.themeSaveFailed)
            assertFalse(actual = store.state.value.isSavingTheme)
        }
    }

    private class TestThemeSettings : IThemeSettings {
        override val themeMode = MutableStateFlow(value = ThemeMode.System)
        var failWrite = false

        override suspend fun loadThemeMode() = Unit

        override suspend fun setThemeMode(mode: ThemeMode) {
            if (failWrite) error(message = "Storage unavailable")
            themeMode.value = mode
        }
    }

    private class GroupSettings : IItemGroupSettings {
        var enabled = true
        var failWrite = false

        override suspend fun rememberLastGroupEnabled(): Boolean = enabled

        override suspend fun setRememberLastGroup(enabled: Boolean) {
            if (failWrite) error(message = "Storage unavailable")
            this.enabled = enabled
        }

        override suspend fun lastSelectedGroupId(): Int? = null
    }

    private class Locations(private val city: AppLocation) : IAppLocationRepository {
        val location = MutableStateFlow<AppLocation?>(value = null)
        val searches = mutableListOf<String>()
        var failSearch = false
        var failSave = false
        var pending = false
        var cancelled = false

        override fun observeLocation() = location

        override suspend fun setLocation(location: AppLocation?) {
            if (failSave) error(message = "Disk unavailable")
            this.location.value = location
        }

        override suspend fun searchLocations(query: String): List<AppLocation> {
            searches += query
            if (failSearch) error(message = "Offline")
            if (pending)
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            return listOf(element = city)
        }
    }
}
