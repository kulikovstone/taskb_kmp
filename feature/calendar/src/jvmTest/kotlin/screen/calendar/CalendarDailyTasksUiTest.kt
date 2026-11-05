package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CalendarDailyTasksUiTest {
    @Test
    fun `daily routines are first without appearing in the month or duplicating the time sections`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val date = LocalDate(2026, 11, 6)
        val timedDaily = Event(312, "Daily call", date, startTime = LocalTime(8, 0),
            recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily), occurrenceId = EventOccurrenceId(3120, date))
        val state = mutableStateOf(timedCalendarPreviewState().let { it.copy(tasks = it.tasks + Task(320, "Plan the weekend"), events = it.events + timedDaily) })
        val intents = mutableListOf<CalendarIntent>()
        setContent {
            TaskBTheme(false) {
                CalendarScreen(state.value, { intent ->
                    intents.add(intent)
                    state.value = when (intent) {
                        is CalendarIntent.ToggleCompleted -> state.value.copy(tasks = state.value.tasks.map { task ->
                            if (task.id == intent.id && task.occurrenceId == intent.occurrenceId)
                                task.copy(completedAt = if (task.isCompleted) null else state.value.now)
                            else task
                        })
                        CalendarIntent.ShowUnscheduled -> state.value.copy(selectedDate = null)
                        else -> state.value
                    }
                }, {}, {}, {})
            }
        }
        val dailyHeader = onNodeWithText("Daily · 2").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val timedHeader = onNodeWithText("With time · 4").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val untimedHeader = onNodeWithText("Without time · 1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(dailyHeader.top < timedHeader.top && timedHeader.top < untimedHeader.top)
        onNodeWithText("Friday · 3 tasks · 4 events").assertIsDisplayed()
        onNodeWithText("08:00–08:30 (30 min)").assertIsDisplayed()
        onNodeWithContentDescription("Complete Daily call", useUnmergedTree = true).assertDoesNotExist()
        for (task in state.value.sidebarDailyTasks) {
            onNode(hasTestTag("calendar-task-${task.key}") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).assertIsDisplayed()
            onAllNodes(hasTestTag("calendar-task-${task.key}") and hasAnyAncestor(hasTestTag("calendar-grid")), useUnmergedTree = true).assertCountEquals(0)
        }
        onNode(hasText("+2 more") and hasAnyAncestor(hasTestTag("calendar-day-2026-11-06")), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasTestTag("calendar-event:312:3120:2026-11-06") and hasAnyAncestor(hasTestTag("calendar-day-2026-11-06")), useUnmergedTree = true).assertIsDisplayed()
        val morning = state.value.tasks.single { it.id == 310 }
        onNodeWithContentDescription("Complete Morning exercise", useUnmergedTree = true).performClick()
        onNodeWithContentDescription("Mark Morning exercise incomplete", useUnmergedTree = true).assertIsOn()
        runOnIdle { assertEquals(CalendarIntent.ToggleCompleted(310, morning.occurrenceId), intents.single()) }
        onNodeWithTag("calendar-sidebar-toggle").performClick()
        onNodeWithTag("calendar-sidebar-title").assertTextEquals("No date")
        onNodeWithText("Plan the weekend", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Daily · 2").assertDoesNotExist()
        onNodeWithText("With time · 4").assertDoesNotExist()
        onNodeWithText("Without time · 1").assertDoesNotExist()
    }
}
