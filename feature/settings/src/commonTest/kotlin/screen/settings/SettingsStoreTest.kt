package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    private val city = AppLocation("Paris, France", 48.85, 2.35, "Europe/Paris")

    @Test
    fun `search is debounced and selecting or clearing a city saves the shared location`() = runTest {
        val repository = Locations(city)
        SettingsStore(repository, StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            assertFalse(store.state.value.isLoading)
            store.accept(SettingsIntent.Search("Pa"))
            runCurrent()
            advanceTimeBy(200)
            store.accept(SettingsIntent.Search("Paris"))
            runCurrent()
            advanceTimeBy(349)
            runCurrent()
            assertTrue(repository.searches.isEmpty())
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("Paris"), repository.searches)
            assertEquals(listOf(city), store.state.value.results)
            store.accept(SettingsIntent.SelectLocation(city))
            runCurrent()
            assertEquals(city, store.state.value.location)
            assertEquals("", store.state.value.query)
            store.accept(SettingsIntent.ClearLocation)
            runCurrent()
            assertNull(store.state.value.location)
        }
    }

    @Test
    fun `failed saves retain the selected city and search can recover after a network failure`() = runTest {
        val repository = Locations(city).apply { location.value = city; failSearch = true; failSave = true }
        SettingsStore(repository, StandardTestDispatcher(testScheduler)).use { store ->
            runCurrent()
            store.accept(SettingsIntent.Search("Paris"))
            advanceTimeBy(350)
            runCurrent()
            assertNotNull(store.state.value.error)
            repository.failSearch = false
            store.accept(SettingsIntent.Search("Paris"))
            advanceTimeBy(350)
            runCurrent()
            assertEquals(listOf(city), store.state.value.results)
            store.accept(SettingsIntent.ClearLocation)
            runCurrent()
            assertEquals(city, store.state.value.location)
            assertFalse(store.state.value.isSaving)
            assertNotNull(store.state.value.error)
            repository.failSave = false
            store.accept(SettingsIntent.ClearLocation)
            runCurrent()
            assertNull(store.state.value.location)
            assertNull(store.state.value.error)
        }
    }

    @Test
    fun `old search results cannot replace results for a newer query and close cancels pending search`() = runTest {
        val state = SettingsState(query = "London", isSearching = true)
        assertEquals(state, reduceSettings(state, SettingsMessage.SearchResult("Paris", listOf(city))))
        val repository = Locations(city).apply { pending = true }
        val store = SettingsStore(repository, StandardTestDispatcher(testScheduler))
        store.accept(SettingsIntent.Search("Paris"))
        advanceTimeBy(350)
        runCurrent()
        store.close()
        runCurrent()
        assertTrue(repository.cancelled)
    }

    private class Locations(private val city: AppLocation) : IAppLocationRepository {
        val location = MutableStateFlow<AppLocation?>(null)
        val searches = mutableListOf<String>()
        var failSearch = false
        var failSave = false
        var pending = false
        var cancelled = false
        override fun observeLocation() = location
        override suspend fun setLocation(location: AppLocation?) {
            if (failSave) error("Disk unavailable")
            this.location.value = location
        }
        override suspend fun searchLocations(query: String): List<AppLocation> {
            searches += query
            if (failSearch) error("Offline")
            if (pending) try { awaitCancellation() } finally { cancelled = true }
            return listOf(city)
        }
    }
}
