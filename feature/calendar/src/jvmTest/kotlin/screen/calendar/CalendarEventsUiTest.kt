package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@OptIn(ExperimentalTestApi::class)
class CalendarEventsUiTest {
    @Test
    fun `past dates of ongoing multi-day events are grey in both themes`() {
        val today = LocalDate(year = 2026, month = 11, day = 6)
        val start = LocalDate(year = 2026, month = 11, day = 4)
        val zone = TimeZone.currentSystemDefault()
        val events = listOf(
            Event(id = 1, name = "Vacation", dueDate = start, groupId = 3, durationDays = 4),
            Event(
                id = 2,
                name = "Business trip",
                dueDate = start,
                groupId = 1,
                startTime = LocalTime(hour = 18, minute = 0),
                durationMinutes = 3840,
            ),
        )
        for (dark in listOf(false, true)) {
            val state = mutableStateOf(
                value = timedCalendarPreviewState()
                    .copy(
                        today = today,
                        selectedDate = today,
                        now = LocalDateTime(date = today, time = LocalTime(hour = 12, minute = 0))
                            .toInstant(timeZone = zone),
                        tasks = emptyList(),
                        events = events,
                    )
            )
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 900f)) {
                setContent {
                    TaskBTheme(darkTheme = dark) {
                        CalendarScreen(
                            state = state.value,
                            onIntent = {},
                            onBack = {},
                            onOpenTask = {},
                            onCreateTask = {},
                        )
                    }
                }
                val active = if (dark) Palette.BlueGrey.s50 else Palette.BlueGrey.s900
                for (day in 4..7) {
                    val date = LocalDate(year = 2026, month = 11, day = day)
                    val cell = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$date"))
                    for (event in events) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        onNode(
                                matcher = hasText(text = calendarMonthEventText(task = event, date = date)) and cell,
                                useUnmergedTree = true,
                            )
                            .performSemanticsAction(key = SemanticsActions.GetTextLayoutResult) { getLayoutResult ->
                                getLayoutResult(layouts)
                            }
                        val layout = layouts.single().layoutInput
                        assertEquals(
                            expected = if (date < today) Palette.Grey.s400 else active,
                            actual = layout.style.color,
                            message = "$date: ${event.name}",
                        )
                        if (date < today)
                            assertTrue(
                                actual = layout.text.spanStyles.all { range ->
                                    range.item.color == Palette.Grey.s400
                                },
                            )
                    }
                }
                val output = Path.of(
                    "build/reports/screenshots/calendar-multi-day-past-${if (dark) "dark" else "light"}.png"
                )
                Files.createDirectories(output.parent)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())

                // The final day's timed entry becomes grey at its end time; the all-day entry is still active.
                val lastDay = LocalDate(year = 2026, month = 11, day = 7)
                runOnIdle {
                    state.value = state.value.copy(
                        today = lastDay,
                        selectedDate = lastDay,
                        now = LocalDateTime(date = lastDay, time = LocalTime(hour = 10, minute = 0))
                            .toInstant(timeZone = zone),
                    )
                }
                for ((name, color) in listOf("Business trip 10:00" to Palette.Grey.s400, "Vacation" to active)) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNode(
                            matcher = hasText(text = name) and
                                hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$lastDay")),
                            useUnmergedTree = true,
                        )
                        .performSemanticsAction(key = SemanticsActions.GetTextLayoutResult) { getLayoutResult ->
                            getLayoutResult(layouts)
                        }
                    assertEquals(expected = color, actual = layouts.single().layoutInput.style.color)
                }
            }
        }
    }

    @Test
    fun `all day and timed events share the Events section and group stripe with no calendar icon inset`() {
        runSkikoComposeUiTest(size = Size(width = 1440f, height = 1100f)) {
            val date = LocalDate(year = 2026, month = 11, day = 6)
            val preview = timedCalendarPreviewState()
            val now = Instant.parse(input = "2026-11-05T12:00:00Z")
            val allDay = Event(
                id = 312,
                name = "All-day review",
                dueDate = date,
                groupId = 1,
                createdAt = now,
                durationDays = 3,
            )
            val short = Event(
                id = 313,
                name = "A",
                dueDate = date,
                createdAt = Instant.parse(input = "2026-11-04T12:00:00Z"),
            )
            val timed = preview.events.single { event -> event.id == 300 }
                .copy(description = "Review priorities for the next sprint.")
            val state = preview.copy(events = listOf(timed, short, allDay))
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(state = state, onIntent = {}, onBack = {}, onOpenTask = {}, onCreateTask = {})
                }
            }
            onNodeWithText(text = "Events · 3").assertIsDisplayed()
            onNodeWithText(text = "Tasks · 1").assertIsDisplayed()
            val selectedCell = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-11-06"))
            val cellRows = listOf(allDay.name, "A", "12:00 Team sync", "Pay utility bills").map { label ->
                onNode(matcher = hasText(text = label) and selectedCell, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
            }
            assertTrue(actual = cellRows.zipWithNext().all { (first, next) -> first.bottom <= next.top })
            val sidebar = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list"))
            val allDayTitle = onNode(matcher = hasText(text = allDay.name) and sidebar, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val shortTitle = onNode(matcher = hasText(text = "A") and sidebar, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val timeTitle = onNodeWithTag(testTag = "calendar-event-time-300", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(actual = allDayTitle.top < shortTitle.top && shortTitle.top < timeTitle.top)
            assertEquals(expected = timeTitle.left, actual = allDayTitle.left, absoluteTolerance = 0.5f)
            assertEquals(expected = 24f, actual = shortTitle.left, absoluteTolerance = 0.5f)
            val timedTitle = onNode(matcher = hasText(text = timed.name) and sidebar, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val description = onNodeWithText(text = timed.description, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(actual = timedTitle.bottom <= description.top)
            assertEquals(expected = timedTitle.left, actual = description.left, absoluteTolerance = 0.5f)
            onNode(matcher = hasTestTag(testTag = "calendar-event-marker-312") and sidebar, useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-event-time-312", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithContentDescription(label = "Complete All-day review").assertDoesNotExist()
            for (day in 6..8) {
                val cell = hasAnyAncestor(
                    matcher = hasTestTag(
                        testTag =
                            "calendar-day-2026-11-${day.toString().padStart(length = 2, padChar = '0')}"
                    )
                )
                onNode(matcher = hasTestTag(testTag = "calendar-event-marker-312") and cell, useUnmergedTree = true)
                    .assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                onNode(matcher = hasText(text = allDay.name) and cell, useUnmergedTree = true)
                    .performSemanticsAction(key = SemanticsActions.GetTextLayoutResult) { getLayoutResult ->
                        getLayoutResult(layouts)
                    }
                assertTrue(actual = layouts.single().layoutInput.text.spanStyles.isEmpty())
            }
            onNode(matcher = hasTestTag(testTag = "calendar-event-marker-313") and sidebar, useUnmergedTree = true)
                .assertDoesNotExist()
            val output = Path.of("build/reports/screenshots/calendar-event-sections.png")
            Files.createDirectories(output.parent)
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
            onNodeWithTag(testTag = "calendar-sidebar-events-heading").performClick()
            onNode(matcher = hasText(text = allDay.name) and sidebar, useUnmergedTree = true).assertDoesNotExist()
            onNode(matcher = hasText(text = "Pay utility bills") and sidebar, useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }
}
