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
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppLocationEnrichmentTest {
    private val original = AppLocation("Chelyabinsk, Chelyabinsk Oblast, Russia", 55.1611, 61.42877, "Asia/Yekaterinburg")
    private val verified = original.copy(countryCode = "RU", regionName = "Chelyabinsk Oblast")
    private val legacyJson = """{"name":"Chelyabinsk, Chelyabinsk Oblast, Russia","latitude":55.1611,"longitude":61.42877,"timeZone":"Asia/Yekaterinburg"}"""

    @Test
    fun `legacy weather coordinates are emitted before lookup then enriched and remembered after restart`() = runTest {
        val settings = Settings().apply { values["weatherLocation"] = legacyJson }
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val client = mockClient { request ->
            calls++
            assertEquals("Chelyabinsk", request.url.parameters["name"])
            release.await()
            respond(response(verified.copy(name = "Chelyabinsk")))
        }
        try {
            fun repository() = AppLocationRepository(OpenMeteoGeocodingApi(client), lazyOf(settings), StandardTestDispatcher(testScheduler))
            val values = Channel<AppLocation?>(Channel.UNLIMITED)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository().observeLocation().collect { values.send(it) } }
            runCurrent()
            assertEquals(original, values.receive())
            assertEquals(1, calls)
            release.complete(Unit)
            runCurrent()
            assertEquals(verified, values.receive())
            assertEquals(verified, decodeLocation(settings.values.getValue("appLocation")))
            assertEquals(verified, repository().observeLocation().first())
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test
    fun `already migrated shared location without country is enriched without changing name or coordinates`() = runTest {
        val settings = Settings().apply {
            values["weatherLocation"] = legacyJson
            values["appLocation"] = legacyJson
        }
        val candidate = verified.copy(name = "Different provider display name", latitude = original.latitude + 0.000001)
        val client = mockClient { respond(response(candidate)) }
        try {
            val repository = AppLocationRepository(OpenMeteoGeocodingApi(client), lazyOf(settings), StandardTestDispatcher(testScheduler))
            assertEquals(listOf<AppLocation?>(original, verified), repository.observeLocation().take(2).toList())
            assertEquals(verified, decodeLocation(settings.values.getValue("appLocation")))
        } finally { client.close() }
    }

    @Test
    fun `complete location and cleared location perform no metadata requests`() = runTest {
        val settings = Settings().apply { values["appLocation"] = encodeLocation(verified) }
        var calls = 0
        val client = mockClient { calls++; error("No request expected") }
        try {
            val repository = AppLocationRepository(OpenMeteoGeocodingApi(client), lazyOf(settings), StandardTestDispatcher(testScheduler))
            val values = mutableListOf<AppLocation?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeLocation().collect { values += it } }
            runCurrent()
            repository.setLocation(null)
            runCurrent()
            assertEquals(listOf(verified, null), values)
            assertEquals(0, calls)
        } finally { client.close() }
    }

    @Test
    fun `ambiguous distant wrong timezone and failed lookups preserve the original and do not loop`() = runTest {
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
                if (result == null) respond("Offline", HttpStatusCode.ServiceUnavailable) else respond(result)
            }
            try {
                val repository = AppLocationRepository(OpenMeteoGeocodingApi(client), lazyOf(settings), StandardTestDispatcher(testScheduler))
                val values = mutableListOf<AppLocation?>()
                val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeLocation().collect { values += it } }
                runCurrent()
                assertEquals(listOf<AppLocation?>(original), values)
                assertEquals(original, decodeLocation(settings.values.getValue("appLocation")))
                assertEquals(1, calls)
                observer.cancelAndJoin()
            } finally { client.close() }
        }
    }

    @Test
    fun `concurrent observers share one metadata request`() = runTest {
        val settings = Settings().apply { values["appLocation"] = legacyJson }
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val client = mockClient { calls++; release.await(); respond(response(verified)) }
        try {
            val repository = AppLocationRepository(OpenMeteoGeocodingApi(client), lazyOf(settings), StandardTestDispatcher(testScheduler))
            val first = async { repository.observeLocation().take(2).toList() }
            val second = async { repository.observeLocation().take(2).toList() }
            runCurrent()
            assertEquals(1, calls)
            release.complete(Unit)
            assertEquals(listOf<AppLocation?>(original, verified), first.await())
            assertEquals(listOf<AppLocation?>(original, verified), second.await())
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test
    fun `new user selection is not overwritten by a pending metadata request`() = runTest {
        val settings = Settings().apply { values["appLocation"] = legacyJson }
        val release = CompletableDeferred<Unit>()
        val client = mockClient { release.await(); respond(response(verified)) }
        try {
            val repository = AppLocationRepository(OpenMeteoGeocodingApi(client), lazyOf(settings), StandardTestDispatcher(testScheduler))
            val observing = async { repository.observeLocation().take(2).toList() }
            runCurrent()
            val selected = AppLocation("Paris, France", 48.85, 2.35, "Europe/Paris", "FR")
            repository.setLocation(selected)
            release.complete(Unit)
            assertEquals(listOf<AppLocation?>(original, selected), observing.await())
            assertEquals(selected, decodeLocation(settings.values.getValue("appLocation")))
        } finally { client.close() }
    }

    @Test
    fun `active observer takes over metadata lookup when its original owner is cancelled`() = runTest {
        val settings = Settings().apply { values["appLocation"] = legacyJson }
        var calls = 0
        val client = mockClient {
            calls++
            if (calls == 1) awaitCancellation()
            respond(response(verified))
        }
        try {
            val repository = AppLocationRepository(OpenMeteoGeocodingApi(client), lazyOf(settings), StandardTestDispatcher(testScheduler))
            val owner = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeLocation().collect {} }
            runCurrent()
            val survivor = async { repository.observeLocation().take(2).toList() }
            runCurrent()
            assertEquals(1, calls)
            owner.cancelAndJoin()
            assertEquals(listOf<AppLocation?>(original, verified), survivor.await())
            assertEquals(2, calls)
        } finally { client.close() }
    }

    @Test
    fun `later successful retry updates an existing observer after its first metadata request failed`() = runTest {
        val settings = Settings().apply { values["appLocation"] = legacyJson }
        var calls = 0
        val client = mockClient {
            calls++
            if (calls == 1) respond("Offline", HttpStatusCode.ServiceUnavailable) else respond(response(verified))
        }
        try {
            val repository = AppLocationRepository(OpenMeteoGeocodingApi(client), lazyOf(settings), StandardTestDispatcher(testScheduler))
            val values = Channel<AppLocation?>(Channel.UNLIMITED)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeLocation().collect { values.send(it) } }
            runCurrent()
            assertEquals(original, values.receive())
            assertEquals(1, calls)
            assertEquals(verified, repository.observeLocation().first { it?.countryCode != null })
            assertEquals(verified, values.receive())
            assertEquals(2, calls)
        } finally { client.close() }
    }

    private fun TestScope.mockClient(handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }
    }

    private fun response(vararg locations: AppLocation): String = buildJsonObject {
        putJsonArray("results") {
            locations.forEach { location ->
                add(buildJsonObject {
                    put("name", location.name)
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("timezone", location.timeZone)
                    location.countryCode?.let { put("country_code", it) }
                    location.regionName?.let { put("admin1", it) }
                })
            }
        }
    }.toString()

    private class Settings : IAppSettingsDao {
        val values = mutableMapOf<String, String>()
        override suspend fun getValue(key: String) = values[key]
        override suspend fun upsert(setting: ApplicationSetting) { values[setting.key] = setting.value }
    }
}
