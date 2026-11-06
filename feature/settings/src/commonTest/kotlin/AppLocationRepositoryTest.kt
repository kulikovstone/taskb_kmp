package com.mkulikov.taskb.feature.settings

import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.network.OpenMeteoGeocodingApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppLocationRepositoryTest {
    private val city = AppLocation("Paris, Île-de-France, France", 48.85, 2.35, "Europe/Paris", "FR", "Île-de-France")

    @Test
    fun `search returns country and region metadata and ignores malformed locations`() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            assertEquals("geocoding-api.open-meteo.com", request.url.host)
            assertEquals("Paris", request.url.parameters["name"])
            assertEquals("en", request.url.parameters["language"])
            respond("""{"results":[{"name":"Paris","admin1":"Île-de-France","country":"France","country_code":"fr","latitude":48.85,"longitude":2.35,"timezone":"Europe/Paris"},{"name":"Invalid","latitude":999,"longitude":2,"timezone":"Europe/Paris"}]}""")
        })
        try {
            val repository = AppLocationRepository(OpenMeteoGeocodingApi(client), lazyOf(Settings()), StandardTestDispatcher(testScheduler))
            assertTrue(repository.searchLocations(" ").isEmpty())
            assertEquals(listOf(city), repository.searchLocations(" Paris "))
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test
    fun `empty search results and locations without country information do not invent a country`() = runTest {
        var empty = true
        val client = HttpClient(MockEngine {
            respond(if (empty) "{}" else """{"results":[{"name":"Paris","latitude":48.85,"longitude":2.35,"timezone":"Europe/Paris"}]}""")
        })
        try {
            val api = OpenMeteoGeocodingApi(client)
            assertTrue(api.searchLocations("Unknown").isEmpty())
            empty = false
            assertNull(api.searchLocations("Paris").single().countryCode)
        } finally { client.close() }
    }

    @Test
    fun `shared city and its metadata are remembered after restart and changes reach observers`() = runTest {
        val settings = Settings()
        fun repository() = AppLocationRepository(OpenMeteoGeocodingApi(null), lazyOf(settings), StandardTestDispatcher(testScheduler))
        val repository = repository()
        val locations = mutableListOf<AppLocation?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeLocation().collect { locations += it } }
        runCurrent()
        assertNull(locations.single())
        repository.setLocation(city)
        runCurrent()
        assertEquals(city, locations.last())
        assertEquals(city, repository().observeLocation().first())
        repository.setLocation(null)
        runCurrent()
        assertNull(locations.last())
        assertNull(repository().observeLocation().first())
    }

    @Test
    fun `legacy weather location migrates without losing coordinates or guessing its country`() = runTest {
        val settings = Settings()
        settings.values["weatherLocation"] = """{"name":"Paris, France","latitude":48.85,"longitude":2.35,"timeZone":"Europe/Paris"}"""
        fun repository() = AppLocationRepository(OpenMeteoGeocodingApi(null), lazyOf(settings), StandardTestDispatcher(testScheduler))
        val location = repository().observeLocation().first()
        assertEquals(AppLocation("Paris, France", 48.85, 2.35, "Europe/Paris"), location)
        assertNull(location?.countryCode)
        assertNotNull(settings.values["appLocation"])
        repository().setLocation(null)
        assertNull(repository().observeLocation().first())
        assertNotNull(settings.values["weatherLocation"])
    }

    @Test
    fun `failed save keeps the previously selected location`() = runTest {
        val settings = Settings()
        val repository = AppLocationRepository(OpenMeteoGeocodingApi(null), lazyOf(settings), StandardTestDispatcher(testScheduler))
        repository.setLocation(city)
        settings.failSave = true
        assertFailsWith<IllegalStateException> { repository.setLocation(null) }
        assertEquals(city, repository.observeLocation().first())
    }

    @Test
    fun `cancelled writes leave the saved location and observed selection intact`() = runTest {
        val settings = Settings()
        val repository = AppLocationRepository(OpenMeteoGeocodingApi(null), lazyOf(settings), StandardTestDispatcher(testScheduler))
        repository.setLocation(city)
        val observed = mutableListOf<AppLocation?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeLocation().collect { observed += it } }
        runCurrent()
        val started = CompletableDeferred<Unit>()
        settings.beforeSave = { started.complete(Unit); awaitCancellation() }
        val saving = backgroundScope.launch { repository.setLocation(null) }
        started.await()
        saving.cancelAndJoin()
        runCurrent()
        assertEquals(listOf<AppLocation?>(city), observed)
        assertEquals(city, repository.observeLocation().first())
    }

    @Test
    fun `location validates coordinates and uppercase two-letter country codes`() {
        assertFailsWith<IllegalArgumentException> { city.copy(countryCode = "fr") }
        assertFailsWith<IllegalArgumentException> { city.copy(countryCode = "FRA") }
        assertFailsWith<IllegalArgumentException> { city.copy(latitude = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { city.copy(longitude = 181.0) }
    }

    private class Settings : IAppSettingsDao {
        val values = mutableMapOf<String, String>()
        var failSave = false
        var beforeSave: suspend () -> Unit = {}
        override suspend fun getValue(key: String) = values[key]
        override suspend fun upsert(setting: ApplicationSetting) {
            beforeSave()
            if (failSave) error("Storage unavailable")
            values[setting.key] = setting.value
        }
    }
}
