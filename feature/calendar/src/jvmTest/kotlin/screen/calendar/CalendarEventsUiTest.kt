package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CalendarEventsUiTest {
    @Test fun `all day and timed events share the Events section and group stripe with no calendar icon inset`() = runSkikoComposeUiTest(size = Size(1440f, 1100f)) {
        val date = LocalDate(2026, 11, 6)
        val preview = timedCalendarPreviewState()
        val now = Instant.parse("2026-11-05T12:00:00Z")
        val allDay = Event(312, "All-day review", date, 1, createdAt = now, durationDays = 3)
        val short = Event(313, "A", date, createdAt = Instant.parse("2026-11-04T12:00:00Z"))
        val state = preview.copy(events = listOf(preview.events.single { it.id == 300 }, short, allDay))
        setContent { TaskBTheme(false) { CalendarScreen(state, {}, {}, {}, {}) } }
        onNodeWithText("Events · 3").assertIsDisplayed()
        onNodeWithText("Tasks · 1").assertIsDisplayed()
        val selectedCell = hasAnyAncestor(hasTestTag("calendar-day-2026-11-06"))
        val cellRows = listOf(allDay.name, "A", "12:00 Team sync", "Pay utility bills").map { label ->
            onNode(hasText(label) and selectedCell, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        assertTrue(cellRows.zipWithNext().all { (first, next) -> first.bottom <= next.top })
        val sidebar = hasAnyAncestor(hasTestTag("calendar-sidebar-list"))
        val allDayTitle = onNode(hasText(allDay.name) and sidebar, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val shortTitle = onNode(hasText("A") and sidebar, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val timeTitle = onNodeWithTag("calendar-event-time-300", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(allDayTitle.top < shortTitle.top && shortTitle.top < timeTitle.top)
        assertEquals(timeTitle.left, allDayTitle.left, 0.5f)
        assertEquals(24f, shortTitle.left, 0.5f)
        onNode(hasTestTag("calendar-event-marker-312") and sidebar, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("calendar-event-time-312", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithContentDescription("Complete All-day review").assertDoesNotExist()
        for (day in 6..8) {
            val cell = hasAnyAncestor(hasTestTag("calendar-day-2026-11-${day.toString().padStart(2, '0')}"))
            onNode(hasTestTag("calendar-event-marker-312") and cell, useUnmergedTree = true).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            onNode(hasText(allDay.name) and cell, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.single().layoutInput.text.spanStyles.isEmpty())
        }
        onNode(hasTestTag("calendar-event-marker-313") and sidebar, useUnmergedTree = true).assertDoesNotExist()
        val output = Path.of("build/reports/screenshots/calendar-event-sections.png")
        Files.createDirectories(output.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
        onNodeWithTag("calendar-sidebar-events-heading").performClick()
        onNode(hasText(allDay.name) and sidebar, useUnmergedTree = true).assertDoesNotExist()
        onNode(hasText("Pay utility bills") and sidebar, useUnmergedTree = true).assertIsDisplayed()
    }
}
