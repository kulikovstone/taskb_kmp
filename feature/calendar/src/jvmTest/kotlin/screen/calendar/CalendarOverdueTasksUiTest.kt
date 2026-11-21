package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalTestApi::class)
class CalendarOverdueTasksUiTest {
    @Test
    fun `overdue tasks and hidden daily routines stay visible in order in an overflowing past day`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 900f),
        ) {
            val today = LocalDate(year = 2026, month = 11, day = 6)
            val yesterday = LocalDate(year = 2026, month = 11, day = 5)
            val now = Instant.parse(input = "2026-11-06T12:00:00Z")
            val state = mutableStateOf(
                value = CalendarState(
                    today = today,
                    now = now,
                    isLoading = false,
                    tasks = listOf(
                        Task(id = 1, name = "Overdue bill", dueDate = yesterday, createdAt = now),
                        Task(
                            id = 2,
                            name = "Finished task",
                            dueDate = yesterday,
                            createdAt = now,
                            completedAt = now,
                        ),
                        Task(
                            id = 3,
                            name = "Daily routine",
                            dueDate = yesterday,
                            createdAt = now,
                            recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily),
                            showInCalendar = false,
                        ),
                    ),
                    events = listOf(
                        element = Event(
                            id = 1,
                            name = "Vacation",
                            dueDate = LocalDate(year = 2026, month = 11, day = 4),
                            durationDays = 4,
                        )
                    ) +
                        List(size = 6) { index ->
                            Event(
                                id = index + 2,
                                name = "Meeting ${index + 1}",
                                dueDate = yesterday,
                                startTime = LocalTime(hour = 9 + index, minute = 0),
                            )
                        },
                )
            )
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarTestScreen(
                        state = state.value,
                        onIntent = { intent ->
                            if (intent is CalendarIntent.ToggleCompleted)
                                state.value = state.value.copy(
                                    tasks = state.value.tasks.map { task ->
                                        if (task.id == intent.id) task.copy(completedAt = now) else task
                                    }
                                )
                        },
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            val cell = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$yesterday"))
            val task = onNode(matcher = hasTestTag(testTag = "calendar-task-1") and cell, useUnmergedTree = true)
            val daily = onNode(matcher = hasTestTag(testTag = "calendar-task-3") and cell, useUnmergedTree = true)
            val event = onNode(matcher = hasText(text = "Vacation") and cell, useUnmergedTree = true)
            task.assertIsDisplayed()
            daily.assertIsDisplayed()
            event.assertIsDisplayed()
            val dailyBounds = daily.fetchSemanticsNode().boundsInRoot
            assertTrue(actual = task.fetchSemanticsNode().boundsInRoot.bottom <= dailyBounds.top)
            assertTrue(actual = dailyBounds.bottom <= event.fetchSemanticsNode().boundsInRoot.top)
            onNode(matcher = hasText(text = "more", substring = true) and cell, useUnmergedTree = true)
                .assertIsDisplayed()
            val output = Path.of("build/reports/screenshots/calendar-overdue-task-first.png")
            Files.createDirectories(output.parent)
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())

            onNode(
                    matcher = hasContentDescription(value = "Complete Overdue bill") and cell,
                    useUnmergedTree = true,
                )
                .performClick()
            onAllNodes(matcher = hasTestTag(testTag = "calendar-task-1") and cell, useUnmergedTree = true)
                .assertCountEquals(expectedSize = 0)
            onNode(matcher = hasText(text = "Vacation") and cell, useUnmergedTree = true).assertIsDisplayed()

            onNode(
                    matcher = hasContentDescription(value = "Complete Daily routine") and cell,
                    useUnmergedTree = true,
                )
                .performClick()
            daily.assertDoesNotExist()
        }
    }
}
