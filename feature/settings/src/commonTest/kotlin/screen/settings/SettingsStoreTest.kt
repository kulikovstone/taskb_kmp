package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.settings.TestAppSettings
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
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
                settings = TestAppSettings(),
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
                settings = TestAppSettings(),
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
                settings = TestAppSettings(),
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
