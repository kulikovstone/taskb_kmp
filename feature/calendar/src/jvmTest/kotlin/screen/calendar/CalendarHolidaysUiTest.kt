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
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalTestApi::class)
class CalendarHolidaysUiTest {
    @Test
    fun `holiday rows stay read-only and single-line with green thicker markers and correct ordering`() {
        runSkikoComposeUiTest(size = Size(width = 1440f, height = 1000f)) {
            val date = LocalDate(year = 2026, month = 11, day = 4)
            val holiday = PublicHoliday(
                id = "ru-unity",
                date = date,
                name =
                    ("Day of National Unity with a very long official holiday name that must fit " +
                        "on one calendar line"),
                countryCode = "RU",
            )
            val now = Instant.parse(input = "2026-11-06T10:00:00Z")
            val state = mutableStateOf(
                value = CalendarState(
                    today = LocalDate(year = 2026, month = 11, day = 6),
                    selectedDate = date,
                    isLoading = false,
                    now = now,
                    groups = listOf(element = Group(id = 1, name = "Work", color = GroupColor.Blue)),
                    tasks = listOf(
                        element = Task(id = 1, name = "Overdue work", dueDate = date, createdAt = now)
                    ),
                    events = listOf(
                        Event(
                            id = 1,
                            name = "All-day review",
                            dueDate = date,
                            groupId = 1,
                            createdAt = now,
                        ),
                        Event(
                            id = 2,
                            name = "Meeting",
                            dueDate = date,
                            groupId = 1,
                            createdAt = now,
                            startTime = LocalTime(hour = 12, minute = 0),
                        ),
                    ),
                    holidayCalendar = HolidayCalendar(holidays = listOf(element = holiday)),
                )
            )
            val intents = mutableListOf<CalendarIntent>()
            val opened = mutableListOf<Int>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = state.value,
                        onIntent = intents::add,
                        onBack = {},
                        onOpenTask = opened::add,
                        onCreateTask = {},
                        onOpenEvent = { event -> opened += event.id },
                    )
                }
            }
            val cell = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$date"))
            val sidebar = hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list"))
            val holidayTag = "calendar-holiday:${holiday.id}"
            val monthHoliday = onNode(matcher = hasTestTag(testTag = holidayTag) and cell, useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasNoClickAction()
            val sidebarHoliday = onNode(matcher = hasTestTag(testTag = holidayTag) and sidebar, useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasNoClickAction()
            val marker = onNode(
                matcher = hasTestTag(testTag = "calendar-holiday-marker-${holiday.id}") and cell,
                useUnmergedTree = true,
            )
            assertEquals(
                expected = 5f,
                actual = marker.fetchSemanticsNode().boundsInRoot.width,
                absoluteTolerance = 0.5f,
            )
            val markerPixels = marker.captureToImage().toPixelMap()
            assertEquals(
                expected = Palette.Green.s800,
                actual = markerPixels[markerPixels.width / 2, markerPixels.height / 2],
            )
            val layouts = mutableListOf<TextLayoutResult>()
            onNode(
                    matcher = hasTestTag(testTag = "calendar-holiday-title-${holiday.id}") and cell,
                    useUnmergedTree = true,
                )
                .performSemanticsAction(key = SemanticsActions.GetTextLayoutResult) { getLayoutResult ->
                    getLayoutResult(layouts)
                }
            assertEquals(expected = 1, actual = layouts.single().lineCount)
            assertTrue(actual = layouts.single().hasVisualOverflow)
            assertEquals(expected = Palette.Grey.s400, actual = layouts.single().layoutInput.style.color)
            val monthTask = onNode(matcher = hasTestTag(testTag = "calendar-task-1") and cell, useUnmergedTree = true)
            val monthEvent = onNode(matcher = hasTestTag(testTag = "calendar-event:1") and cell, useUnmergedTree = true)
            assertTrue(
                actual =
                    monthTask.fetchSemanticsNode().boundsInRoot.top <
                        monthHoliday.fetchSemanticsNode().boundsInRoot.top
            )
            assertTrue(
                actual =
                    monthHoliday.fetchSemanticsNode().boundsInRoot.top <
                        monthEvent.fetchSemanticsNode().boundsInRoot.top
            )
            val sidebarEvent = onNode(
                matcher = hasTestTag(testTag = "calendar-event:1") and sidebar,
                useUnmergedTree = true,
            )
            assertTrue(
                actual =
                    sidebarHoliday.fetchSemanticsNode().boundsInRoot.top <
                        sidebarEvent.fetchSemanticsNode().boundsInRoot.top
            )
            onNodeWithContentDescription(label = "Complete ${holiday.name}", useUnmergedTree = true)
                .assertDoesNotExist()

            monthHoliday.performMouseInput { click(position = center) }
            sidebarHoliday.performMouseInput { click(position = center) }
            runOnIdle {
                assertTrue(actual = opened.isEmpty())
                assertTrue(actual = intents.all { intent -> intent is CalendarIntent.SelectDay })
            }
            val origin = monthHoliday.fetchSemanticsNode().boundsInRoot.center
            val target = onNodeWithTag(testTag = "calendar-day-2026-11-05", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .center
            onRoot().performMouseInput {
                moveTo(position = origin)
                press()
                moveTo(position = origin + Offset(x = 30f, y = 0f))
                moveTo(position = target)
                release()
            }
            onNodeWithTag(testTag = "calendar-drag-preview", useUnmergedTree = true).assertDoesNotExist()
            runOnIdle {
                assertTrue(
                    actual = intents.none { intent ->
                        intent is CalendarIntent.MoveTask ||
                            intent is CalendarIntent.MoveEvent ||
                            intent is CalendarIntent.ToggleCompleted
                    }
                )
            }
            val output = Path.of("build/reports/screenshots/calendar-holidays.png")
            Files.createDirectories(output.parent)
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())

            runOnIdle { state.value = state.value.copy(today = date) }
            assertTrue(
                actual =
                    monthHoliday.fetchSemanticsNode().boundsInRoot.top <
                        monthEvent.fetchSemanticsNode().boundsInRoot.top
            )
            assertTrue(
                actual =
                    monthEvent.fetchSemanticsNode().boundsInRoot.top <
                        monthTask.fetchSemanticsNode().boundsInRoot.top
            )
        }
    }
}
