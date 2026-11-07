package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class CalendarDayCompletedTest {
    private val date = LocalDate(year = 2026, month = 11, day = 6)
    private val tomorrow = LocalDate(year = 2026, month = 11, day = 7)
    private val now = LocalDateTime(date = date, time = LocalTime(hour = 13, minute = 0))
        .toInstant(timeZone = TimeZone.currentSystemDefault())
    private val completed = Task(id = 1, name = "Finished task", dueDate = date, completedAt = now)
    private val state = CalendarState(today = date, tasks = listOf(element = completed), now = now, isLoading = false)
    private val meeting = Event(
        id = 1,
        name = "Meeting",
        dueDate = date,
        startTime = LocalTime(hour = 12, minute = 0),
        durationMinutes = 60,
    )

    @Test
    fun `only today's nonempty plan can be completed`() {
        assertTrue(actual = state.isSelectedDayCompleted)
        assertFalse(actual = state.copy(tasks = emptyList()).isSelectedDayCompleted)
        assertFalse(
            actual = state
                .copy(selectedDate = null, tasks = listOf(element = completed.copy(dueDate = null)))
                .isSelectedDayCompleted
        )
        for (otherDate in listOf(LocalDate(year = 2026, month = 11, day = 5), tomorrow)) {
            assertFalse(
                actual = state
                    .copy(
                        selectedDate = otherDate,
                        tasks = listOf(element = completed.copy(dueDate = otherDate)),
                    )
                    .isSelectedDayCompleted
            )
        }
        assertFalse(actual = state.copy(isLoading = true).isSelectedDayCompleted)
        assertFalse(actual = state.copy(loadError = "Couldn't load the calendar.").isSelectedDayCompleted)
    }

    @Test
    fun `every task and daily occurrence for today must be completed`() {
        val routine = Task(
            id = 2,
            name = "Daily routine",
            dueDate = date,
            recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily),
            occurrenceId = TaskOccurrenceId(scheduleId = 2, scheduledDate = date),
        )
        assertFalse(
            actual = state.copy(tasks = listOf(element = completed.copy(completedAt = null))).isSelectedDayCompleted
        )
        assertFalse(actual = state.copy(tasks = listOf(completed, routine)).isSelectedDayCompleted)
        assertTrue(
            actual = state
                .copy(
                    tasks = listOf(
                        completed,
                        routine.copy(completedAt = now),
                        routine.copy(
                            dueDate = tomorrow,
                            occurrenceId = TaskOccurrenceId(scheduleId = 2, scheduledDate = tomorrow),
                        ),
                        Task(id = 3, name = "Unscheduled task"),
                    )
                )
                .isSelectedDayCompleted
        )
    }

    @Test
    fun `timed events finish at their end rather than their start`() {
        val withMeeting = state.copy(events = listOf(element = meeting))
        assertFalse(actual = withMeeting.copy(now = now - 61.minutes).isSelectedDayCompleted)
        assertFalse(actual = withMeeting.copy(now = now - 30.minutes).isSelectedDayCompleted)
        assertFalse(actual = withMeeting.copy(now = now - 1.minutes).isSelectedDayCompleted)
        assertTrue(actual = withMeeting.isSelectedDayCompleted)
        assertTrue(actual = withMeeting.copy(tasks = emptyList()).isSelectedDayCompleted)
        assertFalse(
            actual = withMeeting
                .copy(
                    events = listOf(meeting, meeting.copy(id = 2, startTime = LocalTime(hour = 14, minute = 0)))
                )
                .isSelectedDayCompleted
        )
        assertTrue(
            actual = withMeeting
                .copy(events = listOf(meeting, meeting.copy(id = 2, dueDate = tomorrow)))
                .isSelectedDayCompleted
        )
    }

    @Test
    fun `all day events neither block completion nor count as a completed plan on their own`() {
        val allDay = Event(id = 2, name = "Day off", dueDate = date)
        val multiDay = Event(
            id = 3,
            name = "Vacation",
            dueDate = LocalDate(year = 2026, month = 11, day = 5),
            durationDays = 3,
        )
        val events = listOf(allDay, multiDay)
        assertTrue(actual = state.copy(events = events).isSelectedDayCompleted)
        assertFalse(actual = state.copy(tasks = emptyList(), events = events).isSelectedDayCompleted)
        assertTrue(actual = state.copy(tasks = emptyList(), events = events + meeting).isSelectedDayCompleted)
    }

    @Test
    fun `a timed event spanning days blocks completion until its actual end`() {
        val overnight = meeting.copy(dueDate = LocalDate(year = 2026, month = 11, day = 5), durationMinutes = 1500)
        assertFalse(
            actual = state.copy(events = listOf(element = overnight), now = now - 1.minutes).isSelectedDayCompleted
        )
        assertTrue(actual = state.copy(events = listOf(element = overnight)).isSelectedDayCompleted)
        assertFalse(
            actual = state.copy(events = listOf(element = meeting.copy(durationMinutes = 1440))).isSelectedDayCompleted
        )
    }

    @Test
    fun `clock updates reveal completion and midnight hides it for the previous date`() {
        val before = state.copy(events = listOf(element = meeting), now = now - 1.minutes)
        assertFalse(actual = before.isSelectedDayCompleted)
        val finished = reduceCalendar(state = before, message = CalendarMessage.Today(value = date, now = now))
        assertTrue(actual = finished.isSelectedDayCompleted)
        val nextDay = reduceCalendar(
            state = finished,
            message = CalendarMessage.Today(
                value = tomorrow,
                now = LocalDateTime(date = tomorrow, time = LocalTime(hour = 0, minute = 0))
                    .toInstant(timeZone = TimeZone.currentSystemDefault()),
            ),
        )
        assertFalse(actual = nextDay.isSelectedDayCompleted)
    }
}
