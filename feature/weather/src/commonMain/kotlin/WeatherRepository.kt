package com.mkulikov.taskb.feature.weather

import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import com.mkulikov.taskb.feature.weather.network.OpenMeteoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class WeatherRepository(
    private val api: OpenMeteoApi,
    private val locations: IAppLocationRepository,
    private val clock: Clock = Clock.System,
) : IWeatherRepository {
    private val cacheMutex = Mutex()
    private var cache: Cache? = null

    override fun observeForecast(): Flow<WeatherForecast> = locations.observeLocation().flatMapLatest { location ->
        flow {
            if (location == null) {
                emit(WeatherForecast())
                return@flow
            }
            emit(WeatherForecast(location = location, isLoading = true))
            while (true) {
                val result = try {
                    val days = cacheMutex.withLock {
                        val now = clock.now()
                        val saved = cache?.takeIf { it.location == location }
                        if (saved != null && now >= saved.fetchedAt && now - saved.fetchedAt < 1.hours &&
                            saved.day == currentDay(location)) saved.days
                        else api.forecast(location).also {
                            cache = Cache(location, it, now, now.toLocalDateTime(TimeZone.of(location.timeZone)).date)
                        }
                    }
                    WeatherForecast(location, upcoming(days, location))
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    val saved = cacheMutex.withLock { cache?.takeIf { it.location == location } }
                    WeatherForecast(location, upcoming(saved?.days.orEmpty(), location),
                        error = "Couldn't update weather. Retrying automatically.")
                }
                emit(result)
                delay(15.minutes)
            }
        }
    }.catch { exception ->
        if (exception is CancellationException) throw exception
        emit(WeatherForecast(error = "Couldn't load the application location. Reopen the calendar to try again."))
    }

    private fun currentDay(location: WeatherLocation) = clock.now().toLocalDateTime(TimeZone.of(location.timeZone)).date

    private fun upcoming(days: List<DailyWeather>, location: WeatherLocation): List<DailyWeather> {
        val today = currentDay(location)
        val end = today.plus(10, DateTimeUnit.DAY)
        return days.filter { it.date >= today && it.date < end }
    }

    private data class Cache(val location: WeatherLocation, val days: List<DailyWeather>, val fetchedAt: Instant, val day: LocalDate)

}
