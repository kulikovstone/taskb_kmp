package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CalendarCompletedTasksUiTest {
    @Test
    fun `completion moves tasks to the shared last section and reopening restores their section`() {
        runSkikoComposeUiTest(size = Size(width = 1440f, height = 900f)) {
            val preview = timedCalendarPreviewState()
            val now = Instant.parse(input = "2026-11-06T12:00:00Z")
            val routine = preview.tasks.single { task -> task.id == 310 }
            val active = preview.tasks.single { task -> task.id == 303 }
            val completedRoutine = preview.tasks.single { task -> task.id == 311 }.copy(completedAt = now - 1.hours)
            val completedReview = Task(
                id = 323,
                name = "Finished review",
                dueDate = preview.selectedDate,
                completedAt = now - 2.hours,
            )
            val completedTask = Task(
                id = 320,
                name = "Finished planning",
                dueDate = preview.selectedDate,
                completedAt = now - 3.hours,
            )
            val undatedCompleted = Task(id = 321, name = "Finished without a date", completedAt = now - 4.hours)
            val undatedActive = Task(id = 322, name = "Plan the weekend")
            val state = mutableStateOf(
                value = preview.copy(
                    now = now,
                    events = emptyList(),
                    tasks = listOf(
                        routine,
                        active,
                        completedTask,
                        completedReview,
                        completedRoutine,
                        undatedCompleted,
                        undatedActive,
                    ),
                )
            )
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = state.value,
                        onIntent = { intent ->
                            state.value = when (intent) {
                                is CalendarIntent.ToggleCompleted -> state.value.copy(
                                    tasks = state.value.tasks.map { task ->
                                        if (
                                            task.id == intent.id &&
                                                task.occurrenceId == intent.occurrenceId
                                        ) {
                                            task.copy(completedAt = if (task.isCompleted) null else now)
                                        } else {
                                            task
                                        }
                                    }
                                )
                                CalendarIntent.ShowUnscheduled -> state.value.copy(selectedDate = null)
                                is CalendarIntent.SelectDay -> state.value.copy(selectedDate = intent.date)
                                else -> state.value
                            }
                        },
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            fun taskNode(task: Task): SemanticsNodeInteraction {
                return onNode(
                    matcher = hasTestTag(testTag = "calendar-task-${task.key}") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
            }
            fun assertCompletedOrder(tasks: List<Task>) {
                var bottom = onNodeWithTag(testTag = "calendar-sidebar-completed-heading")
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .bottom
                for (task in tasks) {
                    val bounds = taskNode(task = task).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertTrue(actual = bounds.top >= bottom)
                    bottom = bounds.bottom
                    onAllNodes(
                            matcher = hasTestTag(testTag = "calendar-task-${task.key}") and
                                hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                            useUnmergedTree = true,
                        )
                        .assertCountEquals(expectedSize = 1)
                }
            }

            onNodeWithText(text = "Recurring · 1").assertIsDisplayed()
            onNodeWithText(text = "Tasks · 1").assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-sidebar-events-heading").assertDoesNotExist()
            onNodeWithText(text = "Completed · 3").assertIsDisplayed()
            assertTrue(
                actual =
                    onNodeWithTag(testTag = "calendar-sidebar-completed-heading")
                        .fetchSemanticsNode()
                        .boundsInRoot
                        .top > taskNode(task = active).fetchSemanticsNode().boundsInRoot.bottom
            )
            assertCompletedOrder(tasks = listOf(completedRoutine, completedReview, completedTask))

            onNodeWithContentDescription(label = "Complete ${routine.name}", useUnmergedTree = true).performClick()
            onNodeWithTag(testTag = "calendar-sidebar-recurring-heading").assertDoesNotExist()
            onNodeWithText(text = "Completed · 4").assertIsDisplayed()
            assertCompletedOrder(tasks = listOf(routine, completedRoutine, completedReview, completedTask))
            val path = Path.of("build/reports/screenshots")
            Files.createDirectories(path)
            ImageIO.write(
                onRoot().captureToImage().toAwtImage(),
                "png",
                path.resolve("calendar-completed-section.png").toFile(),
            )

            onNodeWithTag(testTag = "calendar-sidebar-completed-heading").performClick()
            for (task in listOf(routine, completedRoutine, completedReview, completedTask)) taskNode(task = task)
                .assertDoesNotExist()
            taskNode(task = active).assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-sidebar-completed-heading").performClick()
            onNodeWithContentDescription(label = "Mark ${routine.name} incomplete", useUnmergedTree = true)
                .performClick()
            onNodeWithText(text = "Recurring · 1").assertIsDisplayed()
            onNodeWithText(text = "Completed · 3").assertIsDisplayed()
            assertTrue(
                actual =
                    taskNode(task = routine).fetchSemanticsNode().boundsInRoot.bottom <
                        onNodeWithTag(testTag = "calendar-sidebar-completed-heading")
                            .fetchSemanticsNode()
                            .boundsInRoot
                            .top
            )

            onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
            onNodeWithText(text = "Completed · 1").assertIsDisplayed()
            taskNode(task = undatedActive).assertIsDisplayed()
            assertCompletedOrder(tasks = listOf(element = undatedCompleted))
            assertTrue(
                actual =
                    taskNode(task = undatedActive).fetchSemanticsNode().boundsInRoot.bottom <
                        onNodeWithTag(testTag = "calendar-sidebar-completed-heading")
                            .fetchSemanticsNode()
                            .boundsInRoot
                            .top
            )
            onNodeWithTag(testTag = "calendar-sidebar-completed-heading").performClick()
            onNodeWithTag(testTag = "calendar-day-number-${preview.selectedDate}", useUnmergedTree = true)
                .performClick()
            onNodeWithTag(testTag = "calendar-sidebar-completed-heading")
                .assert(
                    matcher = SemanticsMatcher.expectValue(
                        key = SemanticsProperties.StateDescription,
                        expectedValue = "Expanded",
                    )
                )
            assertCompletedOrder(tasks = listOf(completedRoutine, completedReview, completedTask))
        }
    }
}
