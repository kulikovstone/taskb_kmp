package com.mkulikov.taskb.feature.weather

import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import com.mkulikov.taskb.feature.weather.network.OpenMeteoApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*

private val forecastCacheLifetime = 1.hours
private val forecastRefreshCheckInterval = 15.minutes

@OptIn(ExperimentalCoroutinesApi::class)
internal class WeatherRepository(
    private val api: OpenMeteoApi,
    private val locations: IAppLocationRepository,
    private val clock: Clock = Clock.System,
) : IWeatherRepository {
    private val cacheMutex = Mutex()
    private var cache: Cache? = null

    override fun observeForecast(): Flow<WeatherForecast> {
        return locations
            .observeLocation()
            .flatMapLatest { location ->
                flow {
                    if (location == null) {
                        emit(value = WeatherForecast())
                        return@flow
                    }
                    emit(value = WeatherForecast(location = location, isLoading = true))
                    while (true) {
                        val result = try {
                            val days = cacheMutex.withLock {
                                val now = clock.now()
                                val saved = cache?.takeIf { cached -> cached.location == location }
                                if (
                                    saved != null &&
                                        now >= saved.fetchedAt &&
                                        now - saved.fetchedAt < forecastCacheLifetime &&
                                        saved.day == currentDay(location = location)
                                ) {
                                    saved.days
                                } else {
                                    api.forecast(location = location).also { weather ->
                                        cache = Cache(
                                            location = location,
                                            days = weather,
                                            fetchedAt = now,
                                            day = now.toLocalDateTime(
                                                    timeZone = TimeZone.of(zoneId = location.timeZone)
                                                )
                                                .date,
                                        )
                                    }
                                }
                            }
                            WeatherForecast(
                                location = location,
                                days = upcoming(days = days, location = location),
                            )
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (_: Exception) {
                            val saved = cacheMutex.withLock { cache?.takeIf { cached -> cached.location == location } }
                            WeatherForecast(
                                location = location,
                                days = upcoming(days = saved?.days.orEmpty(), location = location),
                                error = "Couldn't update weather. Retrying automatically.",
                            )
                        }
                        emit(value = result)
                        delay(duration = forecastRefreshCheckInterval)
                    }
                }
            }
            .catch { exception ->
                if (exception is CancellationException) throw exception
                emit(
                    value = WeatherForecast(
                        error = "Couldn't load the application location. Reopen the calendar to try again."
                    )
                )
            }
    }

    private fun currentDay(location: WeatherLocation): LocalDate {
        return clock.now().toLocalDateTime(
            timeZone = TimeZone.of(zoneId = location.timeZone),
        ).date
    }

    private fun upcoming(days: List<DailyWeather>, location: WeatherLocation): List<DailyWeather> {
        val today = currentDay(location = location)
        val end = today.plus(value = WeatherForecast.HORIZON_DAYS, unit = DateTimeUnit.DAY)
        return days.filter { weather -> weather.date >= today && weather.date < end }
    }

    private data class Cache(
        val location: WeatherLocation,
        val days: List<DailyWeather>,
        val fetchedAt: Instant,
        val day: LocalDate,
    )
}
