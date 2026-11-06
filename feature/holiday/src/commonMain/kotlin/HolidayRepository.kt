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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.number
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

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
        require(from <= through)
        return locations.observeLocation().flatMapLatest { location ->
            flow {
                if (location == null) {
                    emit(HolidayCalendar())
                    return@flow
                }
                val jurisdiction = try { holidayLocation(location) }
                catch (exception: HolidayLocationException) {
                    emit(HolidayCalendar(location = location, error = exception.message))
                    return@flow
                }
                val months = buildList {
                    var month = from.yearMonth
                    while (month <= through.yearMonth) { add(month); month = month.plusMonth() }
                }
                val saved = withContext(dispatcher) {
                    dao.value.getHolidays(jurisdiction.key, from.toEpochDays(), through.toEpochDays())
                }
                val missing = withContext(dispatcher) {
                    months.filter { dao.value.getMonth(jurisdiction.key, it.year, it.month.number) == null }
                }
                emit(HolidayCalendar(location, saved.map(HolidayEntity::toHoliday), isLoading = missing.isNotEmpty()))
                var failure: String? = null
                for (month in missing) {
                    try { loadMonth(jurisdiction, month) }
                    catch (exception: CancellationException) { throw exception }
                    catch (exception: HolidayLocationException) { failure = exception.message }
                    catch (_: Exception) { failure = "Couldn't load public holidays. Cached holidays are still available. Please try again." }
                }
                emitAll(withContext(dispatcher) {
                    dao.value.observeHolidays(jurisdiction.key, from.toEpochDays(), through.toEpochDays())
                }.map { HolidayCalendar(location, it.map(HolidayEntity::toHoliday), error = failure) })
            }.catch { exception ->
                if (exception is CancellationException) throw exception
                emit(HolidayCalendar(location = location, error = "Couldn't read public holidays. Please try again."))
            }
        }.catch { exception ->
            if (exception is CancellationException) throw exception
            emit(HolidayCalendar(error = "Couldn't load location settings. Please try again."))
        }
    }

    private suspend fun loadMonth(location: HolidayLocation, month: YearMonth) = fetchMutex.withLock {
        // Recheck after acquiring the lock: another calendar observer may have
        // populated this month while this request was waiting.
        val alreadyLoaded = withContext(dispatcher) {
            dao.value.getMonth(location.key, month.year, month.month.number) != null
        }
        if (!alreadyLoaded) {
            val holidays = api.holidays(location, month)
            withContext(dispatcher) {
                dao.value.replaceMonth(HolidayMonthEntity(location.key, month.year, month.month.number, clock.now().toEpochMilliseconds()),
                    holidays.map { HolidayEntity(location.key, month.year, month.month.number,
                        it.id, it.date.toEpochDays(), it.name, it.countryCode) })
            }
        }
    }
}

private fun HolidayEntity.toHoliday() = PublicHoliday(id, LocalDate.fromEpochDays(date), name, countryCode)
