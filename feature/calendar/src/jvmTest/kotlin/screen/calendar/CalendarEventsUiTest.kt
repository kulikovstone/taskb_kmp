package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CalendarEventsUiTest {
    @Test
    fun `past dates of ongoing multi-day events are grey in both themes`() {
        val today = LocalDate(2026, 11, 6)
        val start = LocalDate(2026, 11, 4)
        val zone = TimeZone.currentSystemDefault()
        val events = listOf(
            Event(1, "Vacation", start, groupId = 3, durationDays = 4),
            Event(2, "Business trip", start, groupId = 1, startTime = LocalTime(18, 0), durationMinutes = 3840),
        )
        for (dark in listOf(false, true)) {
            val state = mutableStateOf(timedCalendarPreviewState().copy(today = today, selectedDate = today,
                now = LocalDateTime(today, LocalTime(12, 0)).toInstant(zone), tasks = emptyList(), events = events))
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent { TaskBTheme(dark) { CalendarScreen(state.value, {}, {}, {}, {}) } }
                val active = if (dark) Palette.BlueGrey.s50 else Palette.BlueGrey.s900
                for (day in 4..7) {
                    val date = LocalDate(2026, 11, day)
                    val cell = hasAnyAncestor(hasTestTag("calendar-day-$date"))
                    for (event in events) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        onNode(hasText(calendarMonthEventText(event, date)) and cell, useUnmergedTree = true)
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        val layout = layouts.single().layoutInput
                        assertEquals(if (date < today) Palette.Grey.s400 else active, layout.style.color, "$date: ${event.name}")
                        if (date < today) assertTrue(layout.text.spanStyles.all { it.item.color == Palette.Grey.s400 })
                    }
                }
                val output = Path.of("build/reports/screenshots/calendar-multi-day-past-${if (dark) "dark" else "light"}.png")
                Files.createDirectories(output.parent)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())

                // The final day's timed entry becomes grey at its end time; the all-day entry is still active.
                val lastDay = LocalDate(2026, 11, 7)
                runOnIdle {
                    state.value = state.value.copy(today = lastDay, selectedDate = lastDay,
                        now = LocalDateTime(lastDay, LocalTime(10, 0)).toInstant(zone))
                }
                for ((name, color) in listOf("Business trip 10:00" to Palette.Grey.s400, "Vacation" to active)) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNode(hasText(name) and hasAnyAncestor(hasTestTag("calendar-day-$lastDay")), useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(color, layouts.single().layoutInput.style.color)
                }
            }
        }
    }

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
