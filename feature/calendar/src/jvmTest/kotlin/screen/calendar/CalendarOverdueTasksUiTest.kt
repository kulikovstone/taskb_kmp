package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CalendarOverdueTasksUiTest {
    @Test
    fun `overdue task stays visible ahead of events in an overflowing past day`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val today = LocalDate(2026, 11, 6)
        val yesterday = LocalDate(2026, 11, 5)
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val state = mutableStateOf(CalendarState(today, now = now, isLoading = false, tasks = listOf(
            Task(1, "Overdue bill", yesterday, createdAt = now),
            Task(2, "Finished task", yesterday, createdAt = now, completedAt = now),
        ), events = listOf(Event(1, "Vacation", LocalDate(2026, 11, 4), durationDays = 4)) +
            List(6) { Event(it + 2, "Meeting ${it + 1}", yesterday, startTime = LocalTime(9 + it, 0)) }))
        setContent {
            TaskBTheme(false) {
                CalendarScreen(state.value, { intent ->
                    if (intent is CalendarIntent.ToggleCompleted) state.value = state.value.copy(tasks = state.value.tasks.map {
                        if (it.id == intent.id) it.copy(completedAt = now) else it
                    })
                }, {}, {}, {})
            }
        }
        val cell = hasAnyAncestor(hasTestTag("calendar-day-$yesterday"))
        val task = onNode(hasTestTag("calendar-task-1") and cell, useUnmergedTree = true)
        val event = onNode(hasText("Vacation") and cell, useUnmergedTree = true)
        task.assertIsDisplayed()
        event.assertIsDisplayed()
        assertTrue(task.fetchSemanticsNode().boundsInRoot.bottom <= event.fetchSemanticsNode().boundsInRoot.top)
        onNode(hasText("more", substring = true) and cell, useUnmergedTree = true).assertIsDisplayed()
        val output = Path.of("build/reports/screenshots/calendar-overdue-task-first.png")
        Files.createDirectories(output.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())

        onNode(hasContentDescription("Complete Overdue bill") and cell, useUnmergedTree = true).performClick()
        onAllNodes(hasTestTag("calendar-task-1") and cell, useUnmergedTree = true).assertCountEquals(0)
        onNode(hasText("Vacation") and cell, useUnmergedTree = true).assertIsDisplayed()
    }
}
