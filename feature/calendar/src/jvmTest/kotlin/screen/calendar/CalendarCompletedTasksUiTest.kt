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
    fun `completion moves tasks to the shared last section and reopening restores their section`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val preview = timedCalendarPreviewState()
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val routine = preview.tasks.single { it.id == 310 }
        val active = preview.tasks.single { it.id == 303 }
        val completedRoutine = preview.tasks.single { it.id == 311 }.copy(completedAt = now - 1.hours)
        val completedReview = Task(323, "Finished review", preview.selectedDate, completedAt = now - 2.hours)
        val completedTask = Task(320, "Finished planning", preview.selectedDate, completedAt = now - 3.hours)
        val undatedCompleted = Task(321, "Finished without a date", completedAt = now - 4.hours)
        val undatedActive = Task(322, "Plan the weekend")
        val state = mutableStateOf(preview.copy(now = now, events = emptyList(), tasks = listOf(
            routine, active, completedTask, completedReview, completedRoutine, undatedCompleted, undatedActive,
        )))
        setContent {
            TaskBTheme(false) {
                CalendarScreen(state.value, { intent ->
                    state.value = when (intent) {
                        is CalendarIntent.ToggleCompleted -> state.value.copy(tasks = state.value.tasks.map { task ->
                            if (task.id == intent.id && task.occurrenceId == intent.occurrenceId)
                                task.copy(completedAt = if (task.isCompleted) null else now)
                            else task
                        })
                        CalendarIntent.ShowUnscheduled -> state.value.copy(selectedDate = null)
                        is CalendarIntent.SelectDay -> state.value.copy(selectedDate = intent.date)
                        else -> state.value
                    }
                }, {}, {}, {})
            }
        }
        fun taskNode(task: Task) = onNode(
            hasTestTag("calendar-task-${task.key}") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
            useUnmergedTree = true,
        )
        fun assertCompletedOrder(tasks: List<Task>) {
            var bottom = onNodeWithTag("calendar-sidebar-completed-heading").fetchSemanticsNode().boundsInRoot.bottom
            for (task in tasks) {
                val bounds = taskNode(task).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.top >= bottom)
                bottom = bounds.bottom
                onAllNodes(hasTestTag("calendar-task-${task.key}") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
                    useUnmergedTree = true).assertCountEquals(1)
            }
        }

        onNodeWithText("Daily · 1").assertIsDisplayed()
        onNodeWithText("Tasks · 1").assertIsDisplayed()
        onNodeWithTag("calendar-sidebar-events-heading").assertDoesNotExist()
        onNodeWithText("Completed · 3").assertIsDisplayed()
        assertTrue(onNodeWithTag("calendar-sidebar-completed-heading").fetchSemanticsNode().boundsInRoot.top >
            taskNode(active).fetchSemanticsNode().boundsInRoot.bottom)
        assertCompletedOrder(listOf(completedRoutine, completedReview, completedTask))

        onNodeWithContentDescription("Complete ${routine.name}", useUnmergedTree = true).performClick()
        onNodeWithTag("calendar-sidebar-daily-heading").assertDoesNotExist()
        onNodeWithText("Completed · 4").assertIsDisplayed()
        assertCompletedOrder(listOf(routine, completedRoutine, completedReview, completedTask))
        val path = Path.of("build/reports/screenshots")
        Files.createDirectories(path)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.resolve("calendar-completed-section.png").toFile())

        onNodeWithTag("calendar-sidebar-completed-heading").performClick()
        for (task in listOf(routine, completedRoutine, completedReview, completedTask)) taskNode(task).assertDoesNotExist()
        taskNode(active).assertIsDisplayed()
        onNodeWithTag("calendar-sidebar-completed-heading").performClick()
        onNodeWithContentDescription("Mark ${routine.name} incomplete", useUnmergedTree = true).performClick()
        onNodeWithText("Daily · 1").assertIsDisplayed()
        onNodeWithText("Completed · 3").assertIsDisplayed()
        assertTrue(taskNode(routine).fetchSemanticsNode().boundsInRoot.bottom <
            onNodeWithTag("calendar-sidebar-completed-heading").fetchSemanticsNode().boundsInRoot.top)

        onNodeWithTag("calendar-sidebar-toggle").performClick()
        onNodeWithText("Completed · 1").assertIsDisplayed()
        taskNode(undatedActive).assertIsDisplayed()
        assertCompletedOrder(listOf(undatedCompleted))
        assertTrue(taskNode(undatedActive).fetchSemanticsNode().boundsInRoot.bottom <
            onNodeWithTag("calendar-sidebar-completed-heading").fetchSemanticsNode().boundsInRoot.top)
        onNodeWithTag("calendar-sidebar-completed-heading").performClick()
        onNodeWithTag("calendar-day-number-${preview.selectedDate}", useUnmergedTree = true).performClick()
        onNodeWithTag("calendar-sidebar-completed-heading")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        assertCompletedOrder(listOf(completedRoutine, completedReview, completedTask))
    }
}
