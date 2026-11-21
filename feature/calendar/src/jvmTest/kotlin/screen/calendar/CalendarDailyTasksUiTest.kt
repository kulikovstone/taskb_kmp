package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalTestApi::class)
class CalendarDailyTasksUiTest {
    @Test
    fun `hidden daily and weekly tasks appear first in the sidebar and stay out of the month`() {
        runSkikoComposeUiTest(size = Size(width = 1440f, height = 900f)) {
            val date = LocalDate(year = 2026, month = 11, day = 6)
            val timedDaily = Event(
                id = 312,
                name = "Daily call",
                dueDate = date,
                startTime = LocalTime(hour = 8, minute = 0),
                recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily),
                occurrenceId = EventOccurrenceId(scheduleId = 3120, scheduledDate = date),
            )
            val weekly = Task(
                id = 313,
                name = "Weekly planning",
                dueDate = date,
                recurrence = TaskRecurrence(
                    frequency = RecurrenceFrequency.Weekly,
                    weekdays = setOf(element = DayOfWeek.FRIDAY),
                ),
                occurrenceId = TaskOccurrenceId(scheduleId = 3130, scheduledDate = date),
                showInCalendar = false,
            )
            val state = mutableStateOf(
                value = timedCalendarPreviewState().let { state ->
                    state.copy(
                        tasks = state.tasks + listOf(weekly, Task(id = 320, name = "Plan the weekend")),
                        events = state.events + timedDaily,
                    )
                }
            )
            val intents = mutableListOf<CalendarIntent>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarTestScreen(
                        state = state.value,
                        onIntent = { intent ->
                            intents.add(element = intent)
                            state.value = when (intent) {
                                is CalendarIntent.ToggleCompleted -> state.value.copy(
                                    tasks = state.value.tasks.map { task ->
                                        if (
                                            task.id == intent.id &&
                                                task.occurrenceId == intent.occurrenceId
                                        ) {
                                            task.copy(
                                                completedAt = if (task.isCompleted) null else state.value.now
                                            )
                                        } else {
                                            task
                                        }
                                    }
                                )
                                CalendarIntent.ShowUnscheduled -> state.value.copy(selectedDate = null)
                                else -> state.value
                            }
                        },
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            val recurringHeader = onNodeWithText(text = "Recurring · 3")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val timedHeader = onNodeWithText(text = "Events · 4").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val untimedHeader = onNodeWithText(text = "Tasks · 1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(actual = recurringHeader.top < timedHeader.top && timedHeader.top < untimedHeader.top)
            onNodeWithText(text = "Friday · 4 tasks · 4 events").assertIsDisplayed()
            onNodeWithText(text = "08:00–08:30 (30 min)").assertIsDisplayed()
            onNodeWithContentDescription(label = "Complete Daily call", useUnmergedTree = true).assertDoesNotExist()
            for (task in state.value.sidebarRecurringTasks) {
                onNode(
                        matcher = hasTestTag(testTag = "calendar-task-${task.key}") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                        useUnmergedTree = true,
                    )
                    .assertIsDisplayed()
                onAllNodes(
                        matcher = hasTestTag(testTag = "calendar-task-${task.key}") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-grid")),
                        useUnmergedTree = true,
                    )
                    .assertCountEquals(expectedSize = 0)
            }
            onNode(
                    matcher = hasText(text = "+2 more") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-11-06")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            onNode(
                    matcher = hasTestTag(testTag = "calendar-event:312:3120:2026-11-06") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-11-06")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            val morning = state.value.tasks.single { task -> task.id == 310 }
            onNodeWithContentDescription(label = "Complete Morning exercise", useUnmergedTree = true).performClick()
            onNodeWithContentDescription(label = "Mark Morning exercise incomplete", useUnmergedTree = true)
                .assertIsOn()
            runOnIdle {
                assertEquals(
                    expected = CalendarIntent.ToggleCompleted(id = 310, occurrenceId = morning.occurrenceId),
                    actual = intents.single(),
                )
            }
            onNodeWithContentDescription(label = "Clear date filter").performClick()
            onNodeWithContentDescription(label = "Clear date filter").assertDoesNotExist()
            onNodeWithText(text = "Plan the weekend", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-sidebar-recurring-heading").assertDoesNotExist()
            onNodeWithText(text = "Events · 4").assertDoesNotExist()
            onNodeWithText(text = "Tasks · 1").assertDoesNotExist()
        }
    }
}
