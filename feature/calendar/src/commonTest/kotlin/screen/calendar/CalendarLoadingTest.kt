package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class CalendarLoadingTest {
    private val initial = CalendarState(
        today = LocalDate(year = 2026, month = 11, day = 27),
        weather = WeatherForecast(isLoading = true),
        holidayCalendar = HolidayCalendar(isLoading = true),
    )
    private val month = CalendarMessage.Loaded(tasks = emptyList(), groups = emptyList(), now = initial.now)
    private val directory = CalendarMessage.TaskListLoaded(tasks = emptyList())

    @Test
    fun `calendar waits for both local results in either order while external data is pending`() {
        for ((first, second) in listOf(month to directory, directory to month)) {
            val partial = reduceCalendar(state = initial, message = first)
            assertTrue(actual = partial.isContentLoading)
            assertFalse(actual = reduceCalendar(state = partial, message = second).isContentLoading)
        }
    }

    @Test
    fun `failed local loads end the wait and retry starts it again`() {
        val monthFailed = reduceCalendar(state = initial, message = CalendarMessage.LoadFailed)
        val bothFailed = reduceCalendar(state = monthFailed, message = CalendarMessage.TaskListLoadFailed)
        assertFalse(actual = bothFailed.isContentLoading)

        val retryMonth = reduceCalendar(state = bothFailed, message = CalendarMessage.Loading)
        assertTrue(actual = retryMonth.isContentLoading)
        val monthReady = reduceCalendar(state = retryMonth, message = month)
        assertFalse(actual = monthReady.isContentLoading)

        val retryDirectory = reduceCalendar(state = monthReady, message = CalendarMessage.TaskListLoading)
        assertTrue(actual = retryDirectory.isContentLoading)
        assertFalse(actual = reduceCalendar(state = retryDirectory, message = directory).isContentLoading)
    }
}
