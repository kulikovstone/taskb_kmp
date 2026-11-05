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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CalendarDayCompletedUiTest {
    @Test
    fun `finishing the last task shows the illustration inside the list and history stays scrollable`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val date = LocalDate(2026, 11, 6)
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val state = mutableStateOf(CalendarState(date, now = now, isLoading = false, tasks =
            List(24) { index -> Task(index + 1, "Finished task ${index + 1}", date, createdAt = now, completedAt = now) } +
                Task(25, "Final task", date, createdAt = now)))
        setContent {
            TaskBTheme(false) {
                CalendarScreen(state.value, { intent ->
                    if (intent is CalendarIntent.ToggleCompleted) state.value = state.value.copy(tasks = state.value.tasks.map { task ->
                        if (task.id == intent.id) task.copy(completedAt = if (task.isCompleted) null else now) else task
                    })
                }, {}, {}, {})
            }
        }
        onNodeWithTag("calendar-day-completed").assertDoesNotExist()
        onNode(hasContentDescription("Complete Final task") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
            useUnmergedTree = true).performClick()
        val card = onNode(hasTestTag("calendar-day-completed") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")))
        card.assertIsDisplayed()
        onNodeWithText("All done for today").assertIsDisplayed()
        onNodeWithText("Enjoy your free time.").assertIsDisplayed()
        assertTrue(card.fetchSemanticsNode().boundsInRoot.bottom <=
            onNodeWithTag("calendar-sidebar-completed-heading").fetchSemanticsNode().boundsInRoot.top)
        val path = Path.of("build/reports/screenshots")
        Files.createDirectories(path)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.resolve("calendar-day-completed.png").toFile())

        onNodeWithTag("calendar-sidebar-list").performScrollToNode(hasTestTag("calendar-task-1"))
        onNode(hasTestTag("calendar-task-1") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithTag("calendar-day-completed").assertDoesNotExist()
        onNode(hasContentDescription("Mark Finished task 1 incomplete") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
            useUnmergedTree = true).performClick()
        onNodeWithTag("calendar-sidebar-list").performScrollToIndex(0)
        onNodeWithTag("calendar-day-completed").assertDoesNotExist()
        onNode(hasContentDescription("Complete Finished task 1") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
            useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `completed day card follows day selection and uses the dark theme`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val date = LocalDate(2026, 11, 6)
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val state = mutableStateOf(CalendarState(date, now = now, isLoading = false, tasks = listOf(
            Task(1, "Finished today", date, completedAt = now),
            Task(2, "Finished yesterday", LocalDate(2026, 11, 5), completedAt = now),
            Task(3, "Finished without a date", completedAt = now),
        )))
        setContent {
            TaskBTheme(true) {
                CalendarScreen(state.value, { intent ->
                    state.value = when (intent) {
                        is CalendarIntent.SelectDay -> state.value.copy(selectedDate = intent.date)
                        CalendarIntent.ShowUnscheduled -> state.value.copy(selectedDate = null)
                        CalendarIntent.Today -> state.value.copy(selectedDate = date)
                        else -> state.value
                    }
                }, {}, {}, {})
            }
        }
        onNodeWithTag("calendar-day-completed").assertIsDisplayed()
        val path = Path.of("build/reports/screenshots")
        Files.createDirectories(path)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.resolve("calendar-day-completed-dark.png").toFile())
        onNodeWithTag("calendar-day-number-2026-11-05", useUnmergedTree = true).performClick()
        onNodeWithTag("calendar-day-completed").assertDoesNotExist()
        onNodeWithTag("calendar-sidebar-toggle").performClick()
        onNodeWithTag("calendar-day-completed").assertDoesNotExist()
        onNodeWithTag("calendar-sidebar-toggle").performClick()
        onNodeWithTag("calendar-day-completed").assertIsDisplayed()
    }
}
