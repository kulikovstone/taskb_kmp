package com.mkulikov.taskb.feature.settings

import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.settings.network.OpenMeteoGeocodingApi
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class AppLocationRepository(
    private val api: OpenMeteoGeocodingApi,
    private val settings: Lazy<IAppSettingsDao>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : IAppLocationRepository {
    private val changes = MutableStateFlow(value = 0L)
    private val mutex = Mutex()

    private val lookupMutex = Mutex()
    private val lookups = mutableMapOf<AppLocation, CompletableDeferred<LookupOutcome>>()

    override fun observeLocation(): Flow<AppLocation?> {
        return changes
            .transformLatest {
                val saved = readLocation()
                // Weather can start using the original coordinates before metadata lookup completes.
                emit(value = saved)
                if (saved != null && saved.countryCode == null) {
                    val enriched = enrichLocation(original = saved)
                    if (enriched != saved) emit(value = enriched)
                }
            }
            .distinctUntilChanged()
    }

    private suspend fun readLocation(): AppLocation? {
        return mutex.withLock {
            withContext(context = dispatcher) { readStoredLocation() }
        }
    }

    /** Called with the persistence mutex held and on the storage dispatcher. */
    private suspend fun readStoredLocation(): AppLocation? {
        val saved = settings.value.getValue(key = LOCATION_KEY)
        if (saved != null) return decodeLocation(value = saved)
        val previous = settings.value.getValue(key = LEGACY_WEATHER_LOCATION_KEY) ?: return null
        return decodeLocation(value = previous).also { location ->
            settings.value.upsert(
                setting = ApplicationSetting(key = LOCATION_KEY, value = encodeLocation(location = location))
            )
        }
    }

    private suspend fun enrichLocation(original: AppLocation): AppLocation? {
        while (true) {
            val (pending, owner) = lookupMutex.withLock {
                val existing = lookups[original]
                if (existing != null) {
                    existing to false
                } else {
                    CompletableDeferred<LookupOutcome>().also { lookup -> lookups[original] = lookup } to true
                }
            }
            if (!owner) {
                when (val outcome = pending.await()) {
                    is LookupOutcome.Resolved -> return outcome.location
                    LookupOutcome.Retry -> continue
                }
            }

            // If this owner is cancelled, an active waiter can take over in its own scope.
            var outcome: LookupOutcome = LookupOutcome.Retry
            try {
                val current = readLocation()
                if (current != original) {
                    outcome = LookupOutcome.Resolved(location = current)
                    return current
                }
                val query = original.name.substringBefore(delimiter = ',').trim()
                val matches = if (query.length < 2) {
                    emptyList()
                } else {
                    api.searchLocations(query = query).filter { candidate ->
                        candidate.countryCode != null &&
                            candidate.timeZone == original.timeZone &&
                            abs(x = candidate.latitude - original.latitude) <= COORDINATE_TOLERANCE &&
                            abs(x = candidate.longitude - original.longitude) <= COORDINATE_TOLERANCE
                    }
                }
                val candidate = matches.singleOrNull()
                if (candidate == null) {
                    outcome = LookupOutcome.Resolved(location = original)
                    return original
                }
                val verified = original.copy(countryCode = candidate.countryCode, regionName = candidate.regionName)
                val result = mutex.withLock {
                    withContext(context = dispatcher) {
                        val latest = readStoredLocation()
                        if (latest != original) {
                            latest
                        } else {
                            verified.also { location ->
                                settings.value.upsert(
                                    setting = ApplicationSetting(
                                        key = LOCATION_KEY,
                                        value = encodeLocation(location = location),
                                    )
                                )
                                // Notify observers that previously completed an unsuccessful metadata attempt.
                                changes.update { revision -> revision + 1 }
                            }
                        }
                    }
                }
                outcome = LookupOutcome.Resolved(location = result)
                return result
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // Network failures do not create a retry loop or disable the original weather selection.
                outcome = LookupOutcome.Resolved(location = original)
                return original
            } finally {
                withContext(context = NonCancellable) {
                    lookupMutex.withLock {
                        if (lookups[original] === pending) lookups.remove(key = original)
                        pending.complete(value = outcome)
                    }
                }
            }
        }
    }

    private sealed interface LookupOutcome {
        data class Resolved(val location: AppLocation?) : LookupOutcome

        data object Retry : LookupOutcome
    }

    override suspend fun setLocation(location: AppLocation?) {
        mutex.withLock {
            withContext(context = dispatcher) {
                settings.value.upsert(
                    setting = ApplicationSetting(key = LOCATION_KEY, value = encodeLocation(location = location))
                )
            }
            changes.update { revision -> revision + 1 }
        }
    }

    override suspend fun searchLocations(query: String): List<AppLocation> {
        return if (query.trim().length < 2) emptyList() else api.searchLocations(query = query)
    }

    private companion object {
        const val COORDINATE_TOLERANCE = 0.00001
        const val LOCATION_KEY = "appLocation"
        const val LEGACY_WEATHER_LOCATION_KEY = "weatherLocation"
    }
}

internal fun encodeLocation(location: AppLocation?): String {
    return if (location == null) {
        "null"
    } else {
        buildJsonObject {
            put(key = "name", value = location.name)
            put(key = "latitude", value = location.latitude)
            put(key = "longitude", value = location.longitude)
            put(key = "timeZone", value = location.timeZone)
            location.countryCode?.let { countryCode -> put(key = "countryCode", value = countryCode) }
            location.regionName?.let { regionName -> put(key = "regionName", value = regionName) }
        }
            .toString()
    }
}

internal fun decodeLocation(value: String): AppLocation? {
    if (value == "null") return null
    val item = Json.parseToJsonElement(string = value).jsonObject
    return AppLocation(
        name = item.getValue(key = "name").jsonPrimitive.content,
        latitude = item.getValue(key = "latitude").jsonPrimitive.double,
        longitude = item.getValue(key = "longitude").jsonPrimitive.double,
        timeZone = item.getValue(key = "timeZone").jsonPrimitive.content,
        countryCode = item["countryCode"]?.jsonPrimitive?.contentOrNull,
        regionName = item["regionName"]?.jsonPrimitive?.contentOrNull,
    )
}
