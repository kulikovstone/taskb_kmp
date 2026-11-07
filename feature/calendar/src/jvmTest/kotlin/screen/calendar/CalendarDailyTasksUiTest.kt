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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalTestApi::class)
class CalendarDailyTasksUiTest {
    @Test
    fun `daily routines are first without appearing in the month or duplicating the time sections`() {
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
            val state = mutableStateOf(
                value = timedCalendarPreviewState().let { state ->
                    state.copy(
                        tasks = state.tasks + Task(id = 320, name = "Plan the weekend"),
                        events = state.events + timedDaily,
                    )
                }
            )
            val intents = mutableListOf<CalendarIntent>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
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
            val dailyHeader = onNodeWithText(text = "Daily · 2").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val timedHeader = onNodeWithText(text = "Events · 4").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val untimedHeader = onNodeWithText(text = "Tasks · 1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(actual = dailyHeader.top < timedHeader.top && timedHeader.top < untimedHeader.top)
            onNodeWithText(text = "Friday · 3 tasks · 4 events").assertIsDisplayed()
            onNodeWithText(text = "08:00–08:30 (30 min)").assertIsDisplayed()
            onNodeWithContentDescription(label = "Complete Daily call", useUnmergedTree = true).assertDoesNotExist()
            for (task in state.value.sidebarDailyTasks) {
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
            onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
            onNodeWithTag(testTag = "calendar-sidebar-title").assertTextEquals("No date")
            onNodeWithText(text = "Plan the weekend", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(text = "Daily · 2").assertDoesNotExist()
            onNodeWithText(text = "Events · 4").assertDoesNotExist()
            onNodeWithText(text = "Tasks · 1").assertDoesNotExist()
        }
    }
}
