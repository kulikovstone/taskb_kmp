package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalTestApi::class)
class CalendarDayCompletedUiTest {
    @Test
    fun `finishing the last task shows the illustration inside the list and history stays scrollable`() {
        runSkikoComposeUiTest(size = Size(width = 1440f, height = 900f)) {
            val date = LocalDate(year = 2026, month = 11, day = 6)
            val now = Instant.parse(input = "2026-11-06T12:00:00Z")
            val state = mutableStateOf(
                value = CalendarState(
                    today = date,
                    now = now,
                    isLoading = false,
                    tasks = List(size = 24) { index ->
                        Task(
                            id = index + 1,
                            name = "Finished task ${index + 1}",
                            dueDate = date,
                            createdAt = now,
                            completedAt = now,
                        )
                    } + Task(id = 25, name = "Final task", dueDate = date, createdAt = now),
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
                                        if (task.id == intent.id) {
                                            task.copy(completedAt = if (task.isCompleted) null else now)
                                        } else {
                                            task
                                        }
                                    }
                                )
                        },
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            onNodeWithTag(testTag = "calendar-day-completed").assertDoesNotExist()
            onNode(
                    matcher = hasContentDescription(value = "Complete Final task") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .performClick()
            val card = onNode(
                matcher = hasTestTag(testTag = "calendar-day-completed") and
                    hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list"))
            )
            card.assertIsDisplayed()
            onNodeWithText(text = "All done for today").assertIsDisplayed()
            onNodeWithText(text = "Enjoy your free time.").assertIsDisplayed()
            assertTrue(
                actual =
                    card.fetchSemanticsNode().boundsInRoot.bottom <=
                        onNodeWithTag(testTag = "calendar-sidebar-completed-heading")
                            .fetchSemanticsNode()
                            .boundsInRoot
                            .top
            )
            val path = Path.of("build/reports/screenshots")
            Files.createDirectories(path)
            ImageIO.write(
                onRoot().captureToImage().toAwtImage(),
                "png",
                path.resolve("calendar-day-completed.png").toFile(),
            )

            onNodeWithTag(testTag = "calendar-sidebar-list")
                .performScrollToNode(matcher = hasTestTag(testTag = "calendar-task-1"))
            onNode(
                    matcher = hasTestTag(testTag = "calendar-task-1") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-day-completed").assertDoesNotExist()
            onNode(
                    matcher = hasContentDescription(value = "Mark Finished task 1 incomplete") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .performClick()
            onNodeWithTag(testTag = "calendar-sidebar-list").performScrollToIndex(index = 0)
            onNodeWithTag(testTag = "calendar-day-completed").assertDoesNotExist()
            onNode(
                    matcher = hasContentDescription(value = "Complete Finished task 1") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
        }
    }

    @Test
    fun `completed day card follows day selection and uses the dark theme`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 900f),
        ) {
            val date = LocalDate(year = 2026, month = 11, day = 6)
            val now = Instant.parse(input = "2026-11-06T12:00:00Z")
            val state = mutableStateOf(
                value = CalendarState(
                    today = date,
                    now = now,
                    isLoading = false,
                    tasks = listOf(
                        Task(id = 1, name = "Finished today", dueDate = date, completedAt = now),
                        Task(
                            id = 2,
                            name = "Finished yesterday",
                            dueDate = LocalDate(year = 2026, month = 11, day = 5),
                            completedAt = now,
                        ),
                        Task(id = 3, name = "Finished without a date", completedAt = now),
                    ),
                )
            )
            setContent {
                TaskBTheme(darkTheme = true) {
                    CalendarTestScreen(
                        state = state.value,
                        onIntent = { intent ->
                            state.value = when (intent) {
                                is CalendarIntent.SelectDay -> state.value.copy(selectedDate = intent.date)
                                CalendarIntent.ShowUnscheduled -> state.value.copy(selectedDate = null)
                                CalendarIntent.Today -> state.value.copy(selectedDate = date)
                                else -> state.value
                            }
                        },
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            onNodeWithTag(testTag = "calendar-day-completed").assertIsDisplayed()
            val path = Path.of("build/reports/screenshots")
            Files.createDirectories(path)
            ImageIO.write(
                onRoot().captureToImage().toAwtImage(),
                "png",
                path.resolve("calendar-day-completed-dark.png").toFile(),
            )
            onNodeWithTag(testTag = "calendar-day-number-2026-11-05", useUnmergedTree = true).performClick()
            onNodeWithTag(testTag = "calendar-day-completed").assertDoesNotExist()
            onNodeWithContentDescription(label = "Clear date filter").performClick()
            onNodeWithTag(testTag = "calendar-day-completed").assertDoesNotExist()
            onNodeWithTag(testTag = "calendar-today").performClick()
            onNodeWithTag(testTag = "calendar-day-completed").assertIsDisplayed()
        }
    }
}
