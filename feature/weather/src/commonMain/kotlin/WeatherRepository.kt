package com.mkulikov.taskb.feature.weather

import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import com.mkulikov.taskb.feature.weather.network.OpenMeteoApi
import com.mkulikov.taskb.feature.weather.network.string
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class WeatherRepository(
    private val api: OpenMeteoApi,
    private val settings: Lazy<IAppSettingsDao>,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : IWeatherRepository {
    private val changes = MutableStateFlow(0L)
    private val cacheMutex = Mutex()
    private val locationMutex = Mutex()
    private var cache: Cache? = null

    override fun observeLocation(): Flow<WeatherLocation?> = changes.map {
        withContext(dispatcher) {
            val saved = settings.value.getValue(LOCATION_KEY)
            if (saved == null || saved == "null") null else decodeLocation(saved)
        }
    }.distinctUntilChanged()

    override suspend fun setLocation(location: WeatherLocation?) {
        locationMutex.withLock {
            withContext(dispatcher) {
                settings.value.upsert(ApplicationSetting(LOCATION_KEY, location?.let(::encodeLocation) ?: "null"))
            }
            changes.update { it + 1 }
        }
    }

    override suspend fun searchLocations(query: String): List<WeatherLocation> =
        if (query.trim().length < 2) emptyList() else api.searchLocations(query)

    override fun observeForecast(): Flow<WeatherForecast> = observeLocation().flatMapLatest { location ->
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
        emit(WeatherForecast(error = "Couldn't load weather settings. Reopen the calendar to try again."))
    }

    private fun currentDay(location: WeatherLocation) = clock.now().toLocalDateTime(TimeZone.of(location.timeZone)).date

    private fun upcoming(days: List<DailyWeather>, location: WeatherLocation): List<DailyWeather> {
        val today = currentDay(location)
        val end = today.plus(10, DateTimeUnit.DAY)
        return days.filter { it.date >= today && it.date < end }
    }

    private data class Cache(val location: WeatherLocation, val days: List<DailyWeather>, val fetchedAt: Instant, val day: LocalDate)

    private companion object { const val LOCATION_KEY = "weatherLocation" }
}

internal fun encodeLocation(location: WeatherLocation): String = buildJsonObject {
    put("name", location.name)
    put("latitude", location.latitude)
    put("longitude", location.longitude)
    put("timeZone", location.timeZone)
}.toString()

internal fun decodeLocation(value: String): WeatherLocation {
    val item = Json.parseToJsonElement(value).jsonObject
    return WeatherLocation(item.string("name"), item.getValue("latitude").jsonPrimitive.double,
        item.getValue("longitude").jsonPrimitive.double, item.string("timeZone"))
}
