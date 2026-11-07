package com.mkulikov.taskb.feature.settings

import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.network.OpenMeteoGeocodingApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppLocationRepositoryTest {
    private val city = AppLocation(
        name = "Paris, Île-de-France, France",
        latitude = 48.85,
        longitude = 2.35,
        timeZone = "Europe/Paris",
        countryCode = "FR",
        regionName = "Île-de-France",
    )

    @Test
    fun `search returns country and region metadata and ignores malformed locations`(): TestResult {
        return runTest {
            var calls = 0
            val client = HttpClient(
                engine =
                    MockEngine { request ->
                        calls++
                        assertEquals(expected = "geocoding-api.open-meteo.com", actual = request.url.host)
                        assertEquals(expected = "Paris", actual = request.url.parameters["name"])
                        assertEquals(expected = "en", actual = request.url.parameters["language"])
                        respond(
                            content = """
                            {
                                "results": [
                                    {
                                        "name": "Paris",
                                        "admin1": "Île-de-France",
                                        "country": "France",
                                        "country_code": "fr",
                                        "latitude": 48.85,
                                        "longitude": 2.35,
                                        "timezone": "Europe/Paris"
                                    },
                                    {
                                        "name": "Invalid",
                                        "latitude": 999,
                                        "longitude": 2,
                                        "timezone": "Europe/Paris"
                                    }
                                ]
                            }
                            """
                                .trimIndent()
                        )
                    }
            )
            try {
                val repository = AppLocationRepository(
                    api = OpenMeteoGeocodingApi(client = client),
                    settings = lazyOf(value = Settings()),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                assertTrue(actual = repository.searchLocations(query = " ").isEmpty())
                assertEquals(expected = listOf(element = city), actual = repository.searchLocations(query = " Paris "))
                assertEquals(expected = 1, actual = calls)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `empty search results and locations without country information do not invent a country`(): TestResult {
        return runTest {
            var empty = true
            val client = HttpClient(
                engine =
                    MockEngine {
                        respond(
                            content = if (empty) {
                                "{}"
                            } else {
                                """
                                {
                                    "results": [
                                        {
                                            "name": "Paris",
                                            "latitude": 48.85,
                                            "longitude": 2.35,
                                            "timezone": "Europe/Paris"
                                        }
                                    ]
                                }
                                """
                                    .trimIndent()
                            }
                        )
                    }
            )
            try {
                val api = OpenMeteoGeocodingApi(client = client)
                assertTrue(actual = api.searchLocations(query = "Unknown").isEmpty())
                empty = false
                assertNull(actual = api.searchLocations(query = "Paris").single().countryCode)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `shared city and its metadata are remembered after restart and changes reach observers`(): TestResult {
        return runTest {
            val settings = Settings()
            fun repository(): AppLocationRepository {
                return AppLocationRepository(
                    api = OpenMeteoGeocodingApi(client = null),
                    settings = lazyOf(value = settings),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
            }
            val repository = repository()
            val locations = mutableListOf<AppLocation?>()
            backgroundScope.launch(context = UnconfinedTestDispatcher(scheduler = testScheduler)) {
                repository.observeLocation().collect { location -> locations += location }
            }
            runCurrent()
            assertNull(actual = locations.single())
            repository.setLocation(location = city)
            runCurrent()
            assertEquals(expected = city, actual = locations.last())
            assertEquals(expected = city, actual = repository().observeLocation().first())
            repository.setLocation(location = null)
            runCurrent()
            assertNull(actual = locations.last())
            assertNull(actual = repository().observeLocation().first())
        }
    }

    @Test
    fun `legacy weather location migrates without losing coordinates or guessing its country`(): TestResult {
        return runTest {
            val settings = Settings()
            settings.values["weatherLocation"] =
                """{"name":"Paris, France","latitude":48.85,"longitude":2.35,"timeZone":"Europe/Paris"}"""
            fun repository(): AppLocationRepository {
                return AppLocationRepository(
                    api = OpenMeteoGeocodingApi(client = null),
                    settings = lazyOf(value = settings),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
            }
            val location = repository().observeLocation().first()
            assertEquals(
                expected = AppLocation(
                    name = "Paris, France",
                    latitude = 48.85,
                    longitude = 2.35,
                    timeZone = "Europe/Paris",
                ),
                actual = location,
            )
            assertNull(actual = location?.countryCode)
            assertNotNull(actual = settings.values["appLocation"])
            repository().setLocation(location = null)
            assertNull(actual = repository().observeLocation().first())
            assertNotNull(actual = settings.values["weatherLocation"])
        }
    }

    @Test
    fun `failed save keeps the previously selected location`(): TestResult {
        return runTest {
            val settings = Settings()
            val repository = AppLocationRepository(
                api = OpenMeteoGeocodingApi(client = null),
                settings = lazyOf(value = settings),
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            )
            repository.setLocation(location = city)
            settings.failSave = true
            assertFailsWith<IllegalStateException> { repository.setLocation(location = null) }
            assertEquals(expected = city, actual = repository.observeLocation().first())
        }
    }

    @Test
    fun `cancelled writes leave the saved location and observed selection intact`(): TestResult {
        return runTest {
            val settings = Settings()
            val repository = AppLocationRepository(
                api = OpenMeteoGeocodingApi(client = null),
                settings = lazyOf(value = settings),
                dispatcher = StandardTestDispatcher(scheduler = testScheduler),
            )
            repository.setLocation(location = city)
            val observed = mutableListOf<AppLocation?>()
            backgroundScope.launch(context = UnconfinedTestDispatcher(scheduler = testScheduler)) {
                repository.observeLocation().collect { location -> observed += location }
            }
            runCurrent()
            val started = CompletableDeferred<Unit>()
            settings.beforeSave = {
                started.complete(value = Unit)
                awaitCancellation()
            }
            val saving = backgroundScope.launch { repository.setLocation(location = null) }
            started.await()
            saving.cancelAndJoin()
            runCurrent()
            assertEquals(expected = listOf<AppLocation?>(element = city), actual = observed)
            assertEquals(expected = city, actual = repository.observeLocation().first())
        }
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
            if (failSave) error(message = "Storage unavailable")
            values[setting.key] = setting.value
        }
    }
}
