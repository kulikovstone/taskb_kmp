package com.mkulikov.taskb.feature.weather

import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherCondition
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.feature.weather.network.OpenMeteoApi
import com.mkulikov.taskb.feature.weather.network.weatherCondition
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherRepositoryTest {
    private val city = WeatherLocation(
        name = "Yekaterinburg, Russia",
        latitude = 56.84,
        longitude = 60.61,
        timeZone = "Asia/Yekaterinburg",
    )

    @Test
    fun `forecast requests ten local days in Celsius and preserves dates across month boundaries`(): TestResult {
        return runTest {
            val client = HttpClient(
                engine = MockEngine { request ->
                    assertEquals(expected = "api.open-meteo.com", actual = request.url.host)
                    assertEquals(expected = "10", actual = request.url.parameters["forecast_days"])
                    assertEquals(expected = city.timeZone, actual = request.url.parameters["timezone"])
                    assertEquals(
                        expected = city.latitude.toString(),
                        actual = request.url.parameters["latitude"],
                    )
                    assertEquals(
                        expected = city.longitude.toString(),
                        actual = request.url.parameters["longitude"],
                    )
                    assertEquals(expected = "celsius", actual = request.url.parameters["temperature_unit"])
                    assertEquals(
                        expected = "weather_code,temperature_2m_min,temperature_2m_max",
                        actual = request.url.parameters["daily"],
                    )
                    respond(content = forecastJson("2026-10-31", "2026-11-01"))
                }
            )
            try {
                val clock = MutableClock(value = Instant.parse(input = "2026-10-30T21:00:00Z"))
                val repository = WeatherRepository(
                    api = OpenMeteoApi(client = client),
                    locations = Locations(initial = city),
                    clock = clock,
                )
                val forecast = repository.observeForecast().first { forecast -> !forecast.isLoading }
                assertNull(actual = forecast.error)
                assertEquals(
                    expected = listOf(
                        LocalDate(year = 2026, month = 10, day = 31),
                        LocalDate(year = 2026, month = 11, day = 1),
                    ),
                    actual = forecast.days.map(transform = DailyWeather::date),
                )
                assertEquals(expected = -5.0, actual = forecast.days.first().minimumCelsius)
                assertEquals(expected = WeatherCondition.Snow, actual = forecast.days.first().condition)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `null temperatures are omitted and mismatched arrays fail without assigning weather to another date`():
        TestResult {
        return runTest {
            var malformed = false
            val client = HttpClient(
                engine =
                    MockEngine {
                        respond(
                            content = if (malformed) {
                                """
                                {
                                    "daily": {
                                        "time": [
                                            "2026-10-31"
                                        ],
                                        "weather_code": [

                                        ],
                                        "temperature_2m_min": [
                                            1
                                        ],
                                        "temperature_2m_max": [
                                            2
                                        ]
                                    }
                                }
                                """
                                    .trimIndent()
                            } else {
                                """
                                {
                                    "daily": {
                                        "time": [
                                            "2026-10-31",
                                            "2026-11-01"
                                        ],
                                        "weather_code": [
                                            null,
                                            999
                                        ],
                                        "temperature_2m_min": [
                                            null,
                                            0
                                        ],
                                        "temperature_2m_max": [
                                            3,
                                            2
                                        ]
                                    }
                                }
                                """
                                    .trimIndent()
                            }
                        )
                    }
            )
            try {
                val api = OpenMeteoApi(client = client)
                val days = api.forecast(location = city)
                assertEquals(expected = LocalDate(year = 2026, month = 11, day = 1), actual = days.single().date)
                assertEquals(expected = WeatherCondition.Unknown, actual = days.single().condition)
                malformed = true
                assertFailsWith<IllegalArgumentException> { api.forecast(location = city) }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `cache expires after an hour and failed refresh keeps only upcoming days`(): TestResult {
        return runTest {
            var calls = 0
            var fail = false
            val client = HttpClient(
                engine = MockEngine {
                    calls++
                    if (fail) {
                        respond(content = "unavailable", status = HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond(content = forecastJson("2026-10-31", "2026-11-01"))
                    }
                }
            )
            try {
                val clock = MutableClock(value = Instant.parse(input = "2026-10-31T10:00:00Z"))
                val repository = WeatherRepository(
                    api = OpenMeteoApi(client = client),
                    locations = Locations(initial = city),
                    clock = clock,
                )
                suspend fun read() = repository.observeForecast().first { forecast -> !forecast.isLoading }
                assertEquals(expected = 2, actual = read().days.size)
                clock.value += 59.minutes
                assertEquals(expected = 2, actual = read().days.size)
                assertEquals(expected = 1, actual = calls)
                clock.value += 1.minutes
                fail = true
                val stale = read()
                assertNotNull(actual = stale.error)
                assertEquals(expected = 2, actual = stale.days.size)
                clock.value = Instant.parse(input = "2026-10-31T21:00:00Z")
                assertEquals(
                    expected = listOf(element = LocalDate(year = 2026, month = 11, day = 1)),
                    actual = read().days.map(transform = DailyWeather::date),
                )
                fail = false
                assertNull(actual = read().error)
                assertEquals(expected = 4, actual = calls)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `shared location changes clear forecasts and an absent location performs no requests`(): TestResult {
        return runTest {
            var calls = 0
            val client = HttpClient(
                engine = MockEngine {
                    calls++
                    respond(content = forecastJson("2026-10-31"))
                }
            )
            try {
                val locations = Locations()
                val clock = MutableClock(value = Instant.parse(input = "2026-10-31T10:00:00Z"))
                fun repository(): WeatherRepository {
                    return WeatherRepository(
                        api = OpenMeteoApi(client = client),
                        locations = locations,
                        clock = clock,
                    )
                }
                val repository = repository()
                assertNull(actual = repository.observeForecast().first().location)
                assertEquals(expected = 0, actual = calls)
                locations.setLocation(location = city)
                val states = Channel<WeatherForecast>(capacity = Channel.UNLIMITED)
                backgroundScope.launch(context = UnconfinedTestDispatcher(scheduler = testScheduler)) {
                    repository.observeForecast().collect(collector = states::send)
                }
                assertTrue(actual = states.receive().isLoading)
                assertEquals(expected = city, actual = states.receive().location)
                val anotherCity = city.copy(name = "Another city", longitude = 61.0)
                locations.setLocation(location = anotherCity)
                suspend fun nextFor(location: WeatherLocation?): WeatherForecast {
                    while (true) {
                        val next = states.receive()
                        if (next.location == location) return next
                    }
                }
                val changing = nextFor(location = anotherCity)
                assertEquals(expected = anotherCity, actual = changing.location)
                assertTrue(actual = changing.isLoading && changing.days.isEmpty())
                assertEquals(expected = anotherCity, actual = nextFor(location = anotherCity).location)
                assertEquals(expected = 2, actual = calls)
                locations.setLocation(location = null)
                val disabled = nextFor(location = null)
                assertNull(actual = disabled.location)
                assertTrue(actual = disabled.days.isEmpty())
                assertNull(actual = repository().observeForecast().first().location)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `cancellation propagates and unknown codes have a neutral condition`(): TestResult {
        return runTest {
            val started = CompletableDeferred<Unit>()
            val client = HttpClient(
                engine = MockEngine {
                    started.complete(value = Unit)
                    awaitCancellation()
                }
            )
            try {
                val repository = WeatherRepository(
                    api = OpenMeteoApi(client = client),
                    locations = Locations(initial = city),
                )
                val states = mutableListOf<WeatherForecast>()
                val observation = backgroundScope.launch(
                    context = UnconfinedTestDispatcher(scheduler = testScheduler),
                ) {
                    repository.observeForecast().collect { forecast -> states += forecast }
                }
                started.await()
                observation.cancelAndJoin()
                assertTrue(actual = observation.isCancelled)
                assertTrue(actual = states.none { forecast -> forecast.error != null })
                assertEquals(expected = WeatherCondition.Unknown, actual = weatherCondition(code = 123))
                assertEquals(expected = WeatherCondition.Thunderstorm, actual = weatherCondition(code = 99))
                assertEquals(expected = WeatherCondition.Fog, actual = weatherCondition(code = 48))
                assertEquals(expected = WeatherCondition.Rain, actual = weatherCondition(code = 63))
                assertEquals(expected = WeatherCondition.HeavyRain, actual = weatherCondition(code = 65))
                assertEquals(expected = WeatherCondition.HeavyRain, actual = weatherCondition(code = 82))
            } finally {
                client.close()
            }
        }
    }

    private class Locations(initial: WeatherLocation? = null) : IAppLocationRepository {
        private val location = MutableStateFlow(value = initial)

        override fun observeLocation() = location

        override suspend fun setLocation(location: WeatherLocation?) {
            this.location.value = location
        }

        override suspend fun searchLocations(query: String) = emptyList<WeatherLocation>()
    }

    private class MutableClock(var value: Instant) : Clock {
        override fun now() = value
    }

    private fun forecastJson(vararg dates: String): String {
        return """
            {
                "daily": {
                    "time": [
                        ${dates.joinToString { date -> "\"$date\"" }}
                    ],
                    "weather_code": [
                        ${dates.joinToString { "73" }}
                    ],
                    "temperature_2m_min": [
                        ${dates.joinToString { "-5" }}
                    ],
                    "temperature_2m_max": [
                        ${dates.joinToString { "2" }}
                    ]
                }
            }
        """
            .trimIndent()
    }
}
