package com.mkulikov.taskb.feature.settings

import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.network.OpenMeteoGeocodingApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppLocationEnrichmentTest {
    private val original = AppLocation(
        name = "Chelyabinsk, Chelyabinsk Oblast, Russia",
        latitude = 55.1611,
        longitude = 61.42877,
        timeZone = "Asia/Yekaterinburg",
    )
    private val verified = original.copy(countryCode = "RU", regionName = "Chelyabinsk Oblast")
    private val legacyJson = """
    {
        "name": "Chelyabinsk, Chelyabinsk Oblast, Russia",
        "latitude": 55.1611,
        "longitude": 61.42877,
        "timeZone": "Asia/Yekaterinburg"
    }
    """
        .trimIndent()

    @Test
    fun `legacy weather coordinates are emitted before lookup then enriched and remembered after restart`():
        TestResult {
        return runTest {
            val settings = Settings().apply { values["weatherLocation"] = legacyJson }
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val client = mockClient { request ->
                calls++
                assertEquals(expected = "Chelyabinsk", actual = request.url.parameters["name"])
                release.await()
                respond(content = response(verified.copy(name = "Chelyabinsk")))
            }
            try {
                fun repository(): AppLocationRepository {
                    return AppLocationRepository(
                        api = OpenMeteoGeocodingApi(client = client),
                        settings = lazyOf(value = settings),
                        dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    )
                }
                val values = Channel<AppLocation?>(capacity = Channel.UNLIMITED)
                backgroundScope.launch(context = UnconfinedTestDispatcher(scheduler = testScheduler)) {
                    repository().observeLocation().collect(collector = values::send)
                }
                runCurrent()
                assertEquals(expected = original, actual = values.receive())
                assertEquals(expected = 1, actual = calls)
                release.complete(value = Unit)
                runCurrent()
                assertEquals(expected = verified, actual = values.receive())
                assertEquals(
                    expected = verified,
                    actual = decodeLocation(value = settings.values.getValue(key = "appLocation")),
                )
                assertEquals(expected = verified, actual = repository().observeLocation().first())
                assertEquals(expected = 1, actual = calls)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `already migrated shared location without country is enriched without changing name or coordinates`():
        TestResult {
        return runTest {
            val settings = Settings().apply {
                values["weatherLocation"] = legacyJson
                values["appLocation"] = legacyJson
            }
            val candidate = verified.copy(
                name = "Different provider display name",
                latitude = original.latitude + 0.000001,
            )
            val client = mockClient { respond(content = response(candidate)) }
            try {
                val repository = AppLocationRepository(
                    api = OpenMeteoGeocodingApi(client = client),
                    settings = lazyOf(value = settings),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                assertEquals(
                    expected = listOf<AppLocation?>(original, verified),
                    actual = repository.observeLocation().take(count = 2).toList(),
                )
                assertEquals(
                    expected = verified,
                    actual = decodeLocation(value = settings.values.getValue(key = "appLocation")),
                )
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `complete location and cleared location perform no metadata requests`(): TestResult {
        return runTest {
            val settings = Settings().apply { values["appLocation"] = encodeLocation(location = verified) }
            var calls = 0
            val client = mockClient {
                calls++
                error(message = "No request expected")
            }
            try {
                val repository = AppLocationRepository(
                    api = OpenMeteoGeocodingApi(client = client),
                    settings = lazyOf(value = settings),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                val values = mutableListOf<AppLocation?>()
                backgroundScope.launch(context = UnconfinedTestDispatcher(scheduler = testScheduler)) {
                    repository.observeLocation().collect { location -> values += location }
                }
                runCurrent()
                repository.setLocation(location = null)
                runCurrent()
                assertEquals(expected = listOf(verified, null), actual = values)
                assertEquals(expected = 0, actual = calls)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `ambiguous distant wrong timezone and failed lookups preserve the original and do not loop`(): TestResult {
        return runTest {
            val results = listOf(
                response(verified, verified.copy(name = "Duplicate city")),
                response(verified.copy(latitude = original.latitude + 0.001)),
                response(verified.copy(timeZone = "Europe/Moscow")),
                response(original),
                null,
            )
            for (result in results) {
                val settings = Settings().apply { values["appLocation"] = legacyJson }
                var calls = 0
                val client = mockClient {
                    calls++
                    if (result == null) {
                        respond(content = "Offline", status = HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(content = result)
                    }
                }
                try {
                    val repository = AppLocationRepository(
                        api = OpenMeteoGeocodingApi(client = client),
                        settings = lazyOf(value = settings),
                        dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                    )
                    val values = mutableListOf<AppLocation?>()
                    val observer = backgroundScope.launch(
                        context = UnconfinedTestDispatcher(scheduler = testScheduler),
                    ) {
                        repository.observeLocation().collect { location -> values += location }
                    }
                    runCurrent()
                    assertEquals(expected = listOf<AppLocation?>(element = original), actual = values)
                    assertEquals(
                        expected = original,
                        actual = decodeLocation(value = settings.values.getValue(key = "appLocation")),
                    )
                    assertEquals(expected = 1, actual = calls)
                    observer.cancelAndJoin()
                } finally {
                    client.close()
                }
            }
        }
    }

    @Test
    fun `concurrent observers share one metadata request`(): TestResult {
        return runTest {
            val settings = Settings().apply { values["appLocation"] = legacyJson }
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val client = mockClient {
                calls++
                release.await()
                respond(content = response(verified))
            }
            try {
                val repository = AppLocationRepository(
                    api = OpenMeteoGeocodingApi(client = client),
                    settings = lazyOf(value = settings),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                val first = async { repository.observeLocation().take(count = 2).toList() }
                val second = async { repository.observeLocation().take(count = 2).toList() }
                runCurrent()
                assertEquals(expected = 1, actual = calls)
                release.complete(value = Unit)
                assertEquals(expected = listOf<AppLocation?>(original, verified), actual = first.await())
                assertEquals(expected = listOf<AppLocation?>(original, verified), actual = second.await())
                assertEquals(expected = 1, actual = calls)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `new user selection is not overwritten by a pending metadata request`(): TestResult {
        return runTest {
            val settings = Settings().apply { values["appLocation"] = legacyJson }
            val release = CompletableDeferred<Unit>()
            val client = mockClient {
                release.await()
                respond(content = response(verified))
            }
            try {
                val repository = AppLocationRepository(
                    api = OpenMeteoGeocodingApi(client = client),
                    settings = lazyOf(value = settings),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                val observing = async { repository.observeLocation().take(count = 2).toList() }
                runCurrent()
                val selected = AppLocation(
                    name = "Paris, France",
                    latitude = 48.85,
                    longitude = 2.35,
                    timeZone = "Europe/Paris",
                    countryCode = "FR",
                )
                repository.setLocation(location = selected)
                release.complete(value = Unit)
                assertEquals(expected = listOf<AppLocation?>(original, selected), actual = observing.await())
                assertEquals(
                    expected = selected,
                    actual = decodeLocation(value = settings.values.getValue(key = "appLocation")),
                )
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `active observer takes over metadata lookup when its original owner is cancelled`(): TestResult {
        return runTest {
            val settings = Settings().apply { values["appLocation"] = legacyJson }
            var calls = 0
            val client = mockClient {
                calls++
                if (calls == 1) awaitCancellation()
                respond(content = response(verified))
            }
            try {
                val repository = AppLocationRepository(
                    api = OpenMeteoGeocodingApi(client = client),
                    settings = lazyOf(value = settings),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                val owner = backgroundScope.launch(context = UnconfinedTestDispatcher(scheduler = testScheduler)) {
                    repository.observeLocation().collect {}
                }
                runCurrent()
                val survivor = async { repository.observeLocation().take(count = 2).toList() }
                runCurrent()
                assertEquals(expected = 1, actual = calls)
                owner.cancelAndJoin()
                assertEquals(expected = listOf<AppLocation?>(original, verified), actual = survivor.await())
                assertEquals(expected = 2, actual = calls)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `later successful retry updates an existing observer after its first metadata request failed`(): TestResult {
        return runTest {
            val settings = Settings().apply { values["appLocation"] = legacyJson }
            var calls = 0
            val client = mockClient {
                calls++
                if (calls == 1) {
                    respond(content = "Offline", status = HttpStatusCode.ServiceUnavailable)
                } else {
                    respond(content = response(verified))
                }
            }
            try {
                val repository = AppLocationRepository(
                    api = OpenMeteoGeocodingApi(client = client),
                    settings = lazyOf(value = settings),
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                val values = Channel<AppLocation?>(capacity = Channel.UNLIMITED)
                backgroundScope.launch(context = UnconfinedTestDispatcher(scheduler = testScheduler)) {
                    repository.observeLocation().collect(collector = values::send)
                }
                runCurrent()
                assertEquals(expected = original, actual = values.receive())
                assertEquals(expected = 1, actual = calls)
                assertEquals(
                    expected = verified,
                    actual = repository.observeLocation().first { location -> location?.countryCode != null },
                )
                assertEquals(expected = verified, actual = values.receive())
                assertEquals(expected = 2, actual = calls)
            } finally {
                client.close()
            }
        }
    }

    private fun TestScope.mockClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(engineFactory = MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(scheduler = testScheduler)
                addHandler(handler = handler)
            }
        }
    }

    private fun response(vararg locations: AppLocation): String {
        return buildJsonObject {
            putJsonArray(key = "results") {
                locations.forEach { location ->
                    add(
                        element = buildJsonObject {
                            put(key = "name", value = location.name)
                            put(key = "latitude", value = location.latitude)
                            put(key = "longitude", value = location.longitude)
                            put(key = "timezone", value = location.timeZone)
                            location.countryCode?.let { countryCode -> put(key = "country_code", value = countryCode) }
                            location.regionName?.let { regionName -> put(key = "admin1", value = regionName) }
                        }
                    )
                }
            }
        }
            .toString()
    }

    private class Settings : IAppSettingsDao {
        val values = mutableMapOf<String, String>()

        override suspend fun getValue(key: String) = values[key]

        override suspend fun deleteValue(key: String) {
            values.remove(key = key)
        }

        override suspend fun upsert(setting: ApplicationSetting) {
            values[setting.key] = setting.value
        }
    }
}
