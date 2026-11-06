package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    private val city = WeatherLocation("Paris, France", 48.85, 2.35, "Europe/Paris")

    @Test
    fun `search is debounced and selecting or disabling a city saves settings`() = runTest {
        val repository = Weather(city)
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
            store.accept(SettingsIntent.DisableWeather)
            runCurrent()
            assertNull(store.state.value.location)
        }
    }

    @Test
    fun `failed saves retain the selected city and search can recover after a network failure`() = runTest {
        val repository = Weather(city).apply { location.value = city; failSearch = true; failSave = true }
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
            store.accept(SettingsIntent.DisableWeather)
            runCurrent()
            assertEquals(city, store.state.value.location)
            assertFalse(store.state.value.isSaving)
            assertNotNull(store.state.value.error)
            repository.failSave = false
            store.accept(SettingsIntent.DisableWeather)
            runCurrent()
            assertNull(store.state.value.location)
            assertNull(store.state.value.error)
        }
    }

    @Test
    fun `old search results cannot replace results for a newer query and close cancels pending search`() = runTest {
        val state = SettingsState(query = "London", isSearching = true)
        assertEquals(state, reduceSettings(state, SettingsMessage.SearchResult("Paris", listOf(city))))
        val repository = Weather(city).apply { pending = true }
        val store = SettingsStore(repository, StandardTestDispatcher(testScheduler))
        store.accept(SettingsIntent.Search("Paris"))
        advanceTimeBy(350)
        runCurrent()
        store.close()
        runCurrent()
        assertTrue(repository.cancelled)
    }

    private class Weather(private val city: WeatherLocation) : IWeatherRepository {
        val location = MutableStateFlow<WeatherLocation?>(null)
        val searches = mutableListOf<String>()
        var failSearch = false
        var failSave = false
        var pending = false
        var cancelled = false
        override fun observeLocation() = location
        override fun observeForecast(): Flow<WeatherForecast> = flowOf(WeatherForecast())
        override suspend fun setLocation(location: WeatherLocation?) {
            if (failSave) error("Disk unavailable")
            this.location.value = location
        }
        override suspend fun searchLocations(query: String): List<WeatherLocation> {
            searches += query
            if (failSearch) error("Offline")
            if (pending) try { awaitCancellation() } finally { cancelled = true }
            return listOf(city)
        }
    }
}
