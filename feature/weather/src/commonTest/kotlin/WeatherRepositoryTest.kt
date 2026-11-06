package com.mkulikov.taskb.feature.weather

import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.weather.api.model.WeatherCondition
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.feature.weather.network.OpenMeteoApi
import com.mkulikov.taskb.feature.weather.network.weatherCondition
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherRepositoryTest {
    private val city = WeatherLocation("Yekaterinburg, Russia", 56.84, 60.61, "Asia/Yekaterinburg")

    @Test
    fun `forecast requests ten local days in Celsius and preserves dates across month boundaries`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("api.open-meteo.com", request.url.host)
            assertEquals("10", request.url.parameters["forecast_days"])
            assertEquals(city.timeZone, request.url.parameters["timezone"])
            assertEquals(city.latitude.toString(), request.url.parameters["latitude"])
            assertEquals(city.longitude.toString(), request.url.parameters["longitude"])
            assertEquals("celsius", request.url.parameters["temperature_unit"])
            assertEquals("weather_code,temperature_2m_min,temperature_2m_max", request.url.parameters["daily"])
            respond(forecastJson("2026-10-31", "2026-11-01"))
        })
        try {
            val clock = MutableClock(Instant.parse("2026-10-30T21:00:00Z"))
            val repository = WeatherRepository(OpenMeteoApi(client), Locations(city), clock)
            val forecast = repository.observeForecast().first { !it.isLoading }
            assertNull(forecast.error)
            assertEquals(listOf(LocalDate(2026, 10, 31), LocalDate(2026, 11, 1)), forecast.days.map { it.date })
            assertEquals(-5.0, forecast.days.first().minimumCelsius)
            assertEquals(WeatherCondition.Snow, forecast.days.first().condition)
        } finally { client.close() }
    }

    @Test
    fun `null temperatures are omitted and mismatched arrays fail without assigning weather to another date`() = runTest {
        var malformed = false
        val client = HttpClient(MockEngine {
            respond(if (malformed) """{"daily":{"time":["2026-10-31"],"weather_code":[],"temperature_2m_min":[1],"temperature_2m_max":[2]}}"""
                else """{"daily":{"time":["2026-10-31","2026-11-01"],"weather_code":[null,999],"temperature_2m_min":[null,0],"temperature_2m_max":[3,2]}}""")
        })
        try {
            val api = OpenMeteoApi(client)
            val days = api.forecast(city)
            assertEquals(LocalDate(2026, 11, 1), days.single().date)
            assertEquals(WeatherCondition.Unknown, days.single().condition)
            malformed = true
            assertFailsWith<IllegalArgumentException> { api.forecast(city) }
        } finally { client.close() }
    }

    @Test
    fun `cache expires after an hour and failed refresh keeps only upcoming days`() = runTest {
        var calls = 0
        var fail = false
        val client = HttpClient(MockEngine {
            calls++
            if (fail) respond("unavailable", HttpStatusCode.ServiceUnavailable)
            else respond(forecastJson("2026-10-31", "2026-11-01"))
        })
        try {
            val clock = MutableClock(Instant.parse("2026-10-31T10:00:00Z"))
            val repository = WeatherRepository(OpenMeteoApi(client), Locations(city), clock)
            suspend fun read() = repository.observeForecast().first { !it.isLoading }
            assertEquals(2, read().days.size)
            clock.value += 59.minutes
            assertEquals(2, read().days.size)
            assertEquals(1, calls)
            clock.value += 1.minutes
            fail = true
            val stale = read()
            assertNotNull(stale.error)
            assertEquals(2, stale.days.size)
            clock.value = Instant.parse("2026-10-31T21:00:00Z")
            assertEquals(listOf(LocalDate(2026, 11, 1)), read().days.map { it.date })
            fail = false
            assertNull(read().error)
            assertEquals(4, calls)
        } finally { client.close() }
    }

    @Test
    fun `shared location changes clear forecasts and an absent location performs no requests`() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(forecastJson("2026-10-31"))
        })
        try {
            val locations = Locations()
            val clock = MutableClock(Instant.parse("2026-10-31T10:00:00Z"))
            fun repository() = WeatherRepository(OpenMeteoApi(client), locations, clock)
            val repository = repository()
            assertNull(repository.observeForecast().first().location)
            assertEquals(0, calls)
            locations.setLocation(city)
            val states = Channel<WeatherForecast>(Channel.UNLIMITED)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeForecast().collect { states.send(it) } }
            assertTrue(states.receive().isLoading)
            assertEquals(city, states.receive().location)
            val anotherCity = city.copy(name = "Another city", longitude = 61.0)
            locations.setLocation(anotherCity)
            suspend fun nextFor(location: WeatherLocation?): WeatherForecast {
                while (true) {
                    val next = states.receive()
                    if (next.location == location) return next
                }
            }
            val changing = nextFor(anotherCity)
            assertEquals(anotherCity, changing.location)
            assertTrue(changing.isLoading && changing.days.isEmpty())
            assertEquals(anotherCity, nextFor(anotherCity).location)
            assertEquals(2, calls)
            locations.setLocation(null)
            val disabled = nextFor(null)
            assertNull(disabled.location)
            assertTrue(disabled.days.isEmpty())
            assertNull(repository().observeForecast().first().location)
        } finally { client.close() }
    }

    @Test
    fun `cancellation propagates and unknown codes have a neutral condition`() = runTest {
        val started = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { started.complete(Unit); awaitCancellation() })
        try {
            val repository = WeatherRepository(OpenMeteoApi(client), Locations(city))
            val states = mutableListOf<WeatherForecast>()
            val observation = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observeForecast().collect { states += it }
            }
            started.await()
            observation.cancelAndJoin()
            assertTrue(observation.isCancelled)
            assertTrue(states.none { it.error != null })
            assertEquals(WeatherCondition.Unknown, weatherCondition(123))
            assertEquals(WeatherCondition.Thunderstorm, weatherCondition(99))
            assertEquals(WeatherCondition.Fog, weatherCondition(48))
            assertEquals(WeatherCondition.Rain, weatherCondition(82))
        } finally { client.close() }
    }

    private class Locations(initial: WeatherLocation? = null) : IAppLocationRepository {
        private val location = MutableStateFlow(initial)
        override fun observeLocation() = location
        override suspend fun setLocation(location: WeatherLocation?) { this.location.value = location }
        override suspend fun searchLocations(query: String) = emptyList<WeatherLocation>()
    }

    private class MutableClock(var value: Instant) : Clock { override fun now() = value }

    private fun forecastJson(vararg dates: String): String = """{"daily":{"time":[${dates.joinToString { "\"$it\"" }}],"weather_code":[${dates.joinToString { "73" }}],"temperature_2m_min":[${dates.joinToString { "-5" }}],"temperature_2m_max":[${dates.joinToString { "2" }}]}}"""
}
