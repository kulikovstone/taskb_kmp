package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CalendarHolidaysUiTest {
    @Test
    fun `holiday rows stay read-only and single-line with green thicker markers and correct ordering`() = runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
        val date = LocalDate(2026, 11, 4)
        val holiday = PublicHoliday("ru-unity", date,
            "Day of National Unity with a very long official holiday name that must fit on one calendar line", "RU")
        val now = Instant.parse("2026-11-06T10:00:00Z")
        val state = mutableStateOf(CalendarState(LocalDate(2026, 11, 6), selectedDate = date, isLoading = false, now = now,
            groups = listOf(Group(1, "Work", GroupColor.Blue)),
            tasks = listOf(Task(1, "Overdue work", date, createdAt = now)),
            events = listOf(Event(1, "All-day review", date, groupId = 1, createdAt = now),
                Event(2, "Meeting", date, groupId = 1, createdAt = now, startTime = LocalTime(12, 0))),
            holidayCalendar = HolidayCalendar(holidays = listOf(holiday))))
        val intents = mutableListOf<CalendarIntent>()
        val opened = mutableListOf<Int>()
        setContent { TaskBTheme(false) { CalendarScreen(state.value, intents::add, {}, opened::add, {}, onOpenEvent = { opened += it.id }) } }
        val cell = hasAnyAncestor(hasTestTag("calendar-day-$date"))
        val sidebar = hasAnyAncestor(hasTestTag("calendar-sidebar-list"))
        val holidayTag = "calendar-holiday:${holiday.id}"
        val monthHoliday = onNode(hasTestTag(holidayTag) and cell, useUnmergedTree = true).assertIsDisplayed().assertHasNoClickAction()
        val sidebarHoliday = onNode(hasTestTag(holidayTag) and sidebar, useUnmergedTree = true).assertIsDisplayed().assertHasNoClickAction()
        val marker = onNode(hasTestTag("calendar-holiday-marker-${holiday.id}") and cell, useUnmergedTree = true)
        assertEquals(5f, marker.fetchSemanticsNode().boundsInRoot.width, 0.5f)
        val markerPixels = marker.captureToImage().toPixelMap()
        assertEquals(Palette.Green.s900, markerPixels[markerPixels.width / 2, markerPixels.height / 2])
        val layouts = mutableListOf<TextLayoutResult>()
        onNode(hasTestTag("calendar-holiday-title-${holiday.id}") and cell, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(layouts.single().hasVisualOverflow)
        assertEquals(Palette.Grey.s400, layouts.single().layoutInput.style.color)
        val monthTask = onNode(hasTestTag("calendar-task-1") and cell, useUnmergedTree = true)
        val monthEvent = onNode(hasTestTag("calendar-event:1") and cell, useUnmergedTree = true)
        assertTrue(monthTask.fetchSemanticsNode().boundsInRoot.top < monthHoliday.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(monthHoliday.fetchSemanticsNode().boundsInRoot.top < monthEvent.fetchSemanticsNode().boundsInRoot.top)
        val sidebarEvent = onNode(hasTestTag("calendar-event:1") and sidebar, useUnmergedTree = true)
        assertTrue(sidebarHoliday.fetchSemanticsNode().boundsInRoot.top < sidebarEvent.fetchSemanticsNode().boundsInRoot.top)
        onNodeWithContentDescription("Complete ${holiday.name}", useUnmergedTree = true).assertDoesNotExist()

        monthHoliday.performMouseInput { click(center) }
        sidebarHoliday.performMouseInput { click(center) }
        runOnIdle {
            assertTrue(opened.isEmpty())
            assertTrue(intents.all { it is CalendarIntent.SelectDay })
        }
        val origin = monthHoliday.fetchSemanticsNode().boundsInRoot.center
        val target = onNodeWithTag("calendar-day-2026-11-05", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput { moveTo(origin); press(); moveTo(origin + Offset(30f, 0f)); moveTo(target); release() }
        onNodeWithTag("calendar-drag-preview", useUnmergedTree = true).assertDoesNotExist()
        runOnIdle { assertTrue(intents.none { it is CalendarIntent.MoveTask || it is CalendarIntent.MoveEvent || it is CalendarIntent.ToggleCompleted }) }
        val output = Path.of("build/reports/screenshots/calendar-holidays.png")
        Files.createDirectories(output.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())

        runOnIdle { state.value = state.value.copy(today = date) }
        assertTrue(monthHoliday.fetchSemanticsNode().boundsInRoot.top < monthEvent.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(monthEvent.fetchSemanticsNode().boundsInRoot.top < monthTask.fetchSemanticsNode().boundsInRoot.top)
    }
}
