package com.mkulikov.taskb.feature.settings

import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.settings.network.OpenMeteoGeocodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
internal class AppLocationRepository(
    private val api: OpenMeteoGeocodingApi,
    private val settings: Lazy<IAppSettingsDao>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : IAppLocationRepository {
    private val changes = MutableStateFlow(0L)
    private val mutex = Mutex()

    private val lookupMutex = Mutex()
    private val lookups = mutableMapOf<AppLocation, CompletableDeferred<LookupOutcome>>()

    override fun observeLocation(): Flow<AppLocation?> = changes.transformLatest {
        val saved = readLocation()
        // Weather can start using the original coordinates before metadata lookup completes.
        emit(saved)
        if (saved != null && saved.countryCode == null) {
            val enriched = enrichLocation(saved)
            if (enriched != saved) emit(enriched)
        }
    }.distinctUntilChanged()

    private suspend fun readLocation(): AppLocation? = mutex.withLock {
        withContext(dispatcher) { readStoredLocation() }
    }

    /** Called with the persistence mutex held and on the storage dispatcher. */
    private suspend fun readStoredLocation(): AppLocation? {
        val saved = settings.value.getValue(LOCATION_KEY)
        if (saved != null) return decodeLocation(saved)
        val previous = settings.value.getValue(LEGACY_WEATHER_LOCATION_KEY) ?: return null
        return decodeLocation(previous).also { location ->
            settings.value.upsert(ApplicationSetting(LOCATION_KEY, encodeLocation(location)))
        }
    }

    private suspend fun enrichLocation(original: AppLocation): AppLocation? {
        while (true) {
            val (pending, owner) = lookupMutex.withLock {
                val existing = lookups[original]
                if (existing != null) existing to false
                else CompletableDeferred<LookupOutcome>().also { lookups[original] = it } to true
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
                    outcome = LookupOutcome.Resolved(current)
                    return current
                }
                val query = original.name.substringBefore(',').trim()
                val matches = if (query.length < 2) emptyList() else api.searchLocations(query).filter { candidate ->
                    candidate.countryCode != null && candidate.timeZone == original.timeZone &&
                        abs(candidate.latitude - original.latitude) <= COORDINATE_TOLERANCE &&
                        abs(candidate.longitude - original.longitude) <= COORDINATE_TOLERANCE
                }
                val candidate = matches.singleOrNull()
                if (candidate == null) {
                    outcome = LookupOutcome.Resolved(original)
                    return original
                }
                val verified = original.copy(countryCode = candidate.countryCode, regionName = candidate.regionName)
                val result = mutex.withLock {
                    withContext(dispatcher) {
                        val latest = readStoredLocation()
                        if (latest != original) latest
                        else verified.also {
                            settings.value.upsert(ApplicationSetting(LOCATION_KEY, encodeLocation(it)))
                            // Notify observers that previously completed an unsuccessful metadata attempt.
                            changes.update { revision -> revision + 1 }
                        }
                    }
                }
                outcome = LookupOutcome.Resolved(result)
                return result
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // Network failures do not create a retry loop or disable the original weather selection.
                outcome = LookupOutcome.Resolved(original)
                return original
            } finally {
                withContext(NonCancellable) {
                    lookupMutex.withLock {
                        if (lookups[original] === pending) lookups.remove(original)
                        pending.complete(outcome)
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
            withContext(dispatcher) {
                settings.value.upsert(ApplicationSetting(LOCATION_KEY, encodeLocation(location)))
            }
            changes.update { it + 1 }
        }
    }

    override suspend fun searchLocations(query: String): List<AppLocation> =
        if (query.trim().length < 2) emptyList() else api.searchLocations(query)

    private companion object {
        const val COORDINATE_TOLERANCE = 0.00001
        const val LOCATION_KEY = "appLocation"
        const val LEGACY_WEATHER_LOCATION_KEY = "weatherLocation"
    }
}

internal fun encodeLocation(location: AppLocation?): String = if (location == null) "null" else buildJsonObject {
    put("name", location.name)
    put("latitude", location.latitude)
    put("longitude", location.longitude)
    put("timeZone", location.timeZone)
    location.countryCode?.let { put("countryCode", it) }
    location.regionName?.let { put("regionName", it) }
}.toString()

internal fun decodeLocation(value: String): AppLocation? {
    if (value == "null") return null
    val item = Json.parseToJsonElement(value).jsonObject
    return AppLocation(item.getValue("name").jsonPrimitive.content, item.getValue("latitude").jsonPrimitive.double,
        item.getValue("longitude").jsonPrimitive.double, item.getValue("timeZone").jsonPrimitive.content,
        item["countryCode"]?.jsonPrimitive?.contentOrNull, item["regionName"]?.jsonPrimitive?.contentOrNull)
}
