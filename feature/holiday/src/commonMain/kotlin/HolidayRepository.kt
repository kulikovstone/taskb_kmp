package com.mkulikov.taskb.feature.holiday

import com.mkulikov.taskb.feature.holiday.api.database.HolidayEntity
import com.mkulikov.taskb.feature.holiday.api.database.HolidayMonthEntity
import com.mkulikov.taskb.feature.holiday.api.database.IHolidayDao
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.holiday.api.repository.IHolidayRepository
import com.mkulikov.taskb.feature.holiday.network.EnricoApi
import com.mkulikov.taskb.feature.holiday.network.HolidayLocation
import com.mkulikov.taskb.feature.holiday.network.HolidayLocationException
import com.mkulikov.taskb.feature.holiday.network.holidayLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth

@OptIn(ExperimentalCoroutinesApi::class)
internal class HolidayRepository(
    private val api: EnricoApi,
    private val locations: IAppLocationRepository,
    private val dao: Lazy<IHolidayDao>,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : IHolidayRepository {
    private val fetchMutex = Mutex()

    override fun observeHolidays(from: LocalDate, through: LocalDate): Flow<HolidayCalendar> {
        require(value = from <= through)
        return locations
            .observeLocation()
            .flatMapLatest { location ->
                flow {
                    if (location == null) {
                        emit(value = HolidayCalendar())
                        return@flow
                    }
                    val jurisdiction = try {
                        holidayLocation(location = location)
                    } catch (exception: HolidayLocationException) {
                        emit(value = HolidayCalendar(location = location, error = exception.message))
                        return@flow
                    }
                    val months = buildList {
                        var month = from.yearMonth
                        while (month <= through.yearMonth) {
                            add(element = month)
                            month = month.plusMonth()
                        }
                    }
                    val saved = withContext(context = dispatcher) {
                        dao.value.getHolidays(
                            locationKey = jurisdiction.key,
                            fromDate = from.toEpochDays(),
                            throughDate = through.toEpochDays(),
                        )
                    }
                    val missing = withContext(context = dispatcher) {
                        months.filter { month ->
                            dao.value.getMonth(
                                locationKey = jurisdiction.key,
                                year = month.year,
                                month = month.month.number,
                            ) == null
                        }
                    }
                    emit(
                        value = HolidayCalendar(
                            location = location,
                            holidays = saved.map(transform = HolidayEntity::toHoliday),
                            isLoading = missing.isNotEmpty(),
                        )
                    )
                    var failure: String? = null
                    for (month in missing) {
                        try {
                            loadMonth(location = jurisdiction, month = month)
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: HolidayLocationException) {
                            failure = exception.message
                        } catch (_: Exception) {
                            failure =
                                "Couldn't load public holidays. Cached holidays are still available. Please try again."
                        }
                    }
                    emitAll(
                        flow = withContext(context = dispatcher) {
                                dao.value.observeHolidays(
                                    locationKey = jurisdiction.key,
                                    fromDate = from.toEpochDays(),
                                    throughDate = through.toEpochDays(),
                                )
                            }
                            .map { holidays ->
                                HolidayCalendar(
                                    location = location,
                                    holidays = holidays.map(transform = HolidayEntity::toHoliday),
                                    error = failure,
                                )
                            }
                    )
                }
                    .catch { exception ->
                        if (exception is CancellationException) throw exception
                        emit(
                            value = HolidayCalendar(
                                location = location,
                                error = "Couldn't read public holidays. Please try again.",
                            )
                        )
                    }
            }
            .catch { exception ->
                if (exception is CancellationException) throw exception
                emit(value = HolidayCalendar(error = "Couldn't load location settings. Please try again."))
            }
    }

    private suspend fun loadMonth(location: HolidayLocation, month: YearMonth) {
        fetchMutex.withLock {
            // Recheck after acquiring the lock: another calendar observer may have
            // populated this month while this request was waiting.
            val alreadyLoaded = withContext(context = dispatcher) {
                dao.value.getMonth(locationKey = location.key, year = month.year, month = month.month.number) !=
                    null
            }
            if (!alreadyLoaded) {
                val holidays = api.holidays(location = location, month = month)
                withContext(context = dispatcher) {
                    dao.value.replaceMonth(
                        month = HolidayMonthEntity(
                            locationKey = location.key,
                            year = month.year,
                            month = month.month.number,
                            fetchedAt = clock.now().toEpochMilliseconds(),
                        ),
                        holidays = holidays.map { holiday ->
                            HolidayEntity(
                                locationKey = location.key,
                                year = month.year,
                                month = month.month.number,
                                id = holiday.id,
                                date = holiday.date.toEpochDays(),
                                name = holiday.name,
                                countryCode = holiday.countryCode,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun HolidayEntity.toHoliday(): PublicHoliday {
    return PublicHoliday(
        id = id,
        date = LocalDate.fromEpochDays(epochDays = date),
        name = name,
        countryCode = countryCode,
    )
}
