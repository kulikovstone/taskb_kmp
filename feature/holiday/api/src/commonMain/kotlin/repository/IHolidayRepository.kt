package com.mkulikov.taskb.feature.holiday.api.repository

import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface IHolidayRepository {
    /** Loads only missing months and retains successful snapshots, including empty months. */
    fun observeHolidays(from: LocalDate, through: LocalDate): Flow<HolidayCalendar>
}
