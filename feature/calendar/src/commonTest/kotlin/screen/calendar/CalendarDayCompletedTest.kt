package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class CalendarDayCompletedTest {
    private val date = LocalDate(2026, 11, 6)
    private val tomorrow = LocalDate(2026, 11, 7)
    private val now = LocalDateTime(date, LocalTime(13, 0)).toInstant(TimeZone.currentSystemDefault())
    private val completed = Task(1, "Finished task", date, completedAt = now)
    private val state = CalendarState(date, tasks = listOf(completed), now = now, isLoading = false)
    private val meeting = Event(1, "Meeting", date, startTime = LocalTime(12, 0), durationMinutes = 60)

    @Test
    fun `only today's nonempty plan can be completed`() {
        assertTrue(state.isSelectedDayCompleted)
        assertFalse(state.copy(tasks = emptyList()).isSelectedDayCompleted)
        assertFalse(state.copy(selectedDate = null, tasks = listOf(completed.copy(dueDate = null))).isSelectedDayCompleted)
        for (otherDate in listOf(LocalDate(2026, 11, 5), tomorrow)) {
            assertFalse(state.copy(selectedDate = otherDate, tasks = listOf(completed.copy(dueDate = otherDate))).isSelectedDayCompleted)
        }
        assertFalse(state.copy(isLoading = true).isSelectedDayCompleted)
        assertFalse(state.copy(loadError = "Couldn't load the calendar.").isSelectedDayCompleted)
    }

    @Test
    fun `every task and daily occurrence for today must be completed`() {
        val routine = Task(2, "Daily routine", date,
            recurrence = TaskRecurrence(RecurrenceFrequency.Daily), occurrenceId = TaskOccurrenceId(2, date))
        assertFalse(state.copy(tasks = listOf(completed.copy(completedAt = null))).isSelectedDayCompleted)
        assertFalse(state.copy(tasks = listOf(completed, routine)).isSelectedDayCompleted)
        assertTrue(state.copy(tasks = listOf(completed, routine.copy(completedAt = now),
            routine.copy(dueDate = tomorrow, occurrenceId = TaskOccurrenceId(2, tomorrow)),
            Task(3, "Unscheduled task"))).isSelectedDayCompleted)
    }

    @Test
    fun `timed events finish at their end rather than their start`() {
        val withMeeting = state.copy(events = listOf(meeting))
        assertFalse(withMeeting.copy(now = now - 61.minutes).isSelectedDayCompleted)
        assertFalse(withMeeting.copy(now = now - 30.minutes).isSelectedDayCompleted)
        assertFalse(withMeeting.copy(now = now - 1.minutes).isSelectedDayCompleted)
        assertTrue(withMeeting.isSelectedDayCompleted)
        assertTrue(withMeeting.copy(tasks = emptyList()).isSelectedDayCompleted)
        assertFalse(withMeeting.copy(events = listOf(meeting, meeting.copy(id = 2, startTime = LocalTime(14, 0)))).isSelectedDayCompleted)
        assertTrue(withMeeting.copy(events = listOf(meeting, meeting.copy(id = 2, dueDate = tomorrow))).isSelectedDayCompleted)
    }

    @Test
    fun `all day events neither block completion nor count as a completed plan on their own`() {
        val allDay = Event(2, "Day off", date)
        val multiDay = Event(3, "Vacation", LocalDate(2026, 11, 5), durationDays = 3)
        val events = listOf(allDay, multiDay)
        assertTrue(state.copy(events = events).isSelectedDayCompleted)
        assertFalse(state.copy(tasks = emptyList(), events = events).isSelectedDayCompleted)
        assertTrue(state.copy(tasks = emptyList(), events = events + meeting).isSelectedDayCompleted)
    }

    @Test
    fun `a timed event spanning days blocks completion until its actual end`() {
        val overnight = meeting.copy(dueDate = LocalDate(2026, 11, 5), durationMinutes = 1500)
        assertFalse(state.copy(events = listOf(overnight), now = now - 1.minutes).isSelectedDayCompleted)
        assertTrue(state.copy(events = listOf(overnight)).isSelectedDayCompleted)
        assertFalse(state.copy(events = listOf(meeting.copy(durationMinutes = 1440))).isSelectedDayCompleted)
    }

    @Test
    fun `clock updates reveal completion and midnight hides it for the previous date`() {
        val before = state.copy(events = listOf(meeting), now = now - 1.minutes)
        assertFalse(before.isSelectedDayCompleted)
        val finished = reduceCalendar(before, CalendarMessage.Today(date, now))
        assertTrue(finished.isSelectedDayCompleted)
        val nextDay = reduceCalendar(finished, CalendarMessage.Today(tomorrow,
            LocalDateTime(tomorrow, LocalTime(0, 0)).toInstant(TimeZone.currentSystemDefault())))
        assertFalse(nextDay.isSelectedDayCompleted)
    }
}
