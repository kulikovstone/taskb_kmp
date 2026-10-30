package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class CalendarScreenUiTest {
    @Test
    fun `mouse dragging highlights days moves from sidebar and between cells and undo restores the source`() = runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
        val state = mutableStateOf(calendarPreviewState())
        val openedTasks = mutableListOf<Int>()
        val openedDays = mutableListOf<LocalDate>()
        var token = 0L
        setContent {
            TaskBTheme(false) {
                CalendarScreen(state.value, { intent ->
                    when (intent) {
                        is CalendarIntent.MoveTask -> {
                            val task = state.value.tasks.first { it.id == intent.id }
                            state.value = reduceCalendar(state.value, CalendarMessage.Moved(CalendarMove(++token, task.id, task.dueDate, intent.date), false))
                        }
                        CalendarIntent.UndoMove -> state.value.undo?.let { state.value = reduceCalendar(state.value, CalendarMessage.Moved(it, true)) }
                        else -> Unit
                    }
                }, {}, openedTasks::add, {}, openedDays::add)
            }
        }
        waitForIdle()
        screenshot("calendar-wide")
        val targetDate = LocalDate(2026, 10, 30)
        val target = onNodeWithTag("calendar-day-$targetDate", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val source = onNodeWithTag("calendar-task-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput { moveTo(source); press(); moveTo(source + Offset(28f, 0f)); moveTo(target) }
        onNodeWithTag("calendar-day-$targetDate", useUnmergedTree = true).assertIsSelected()
        screenshot("calendar-drag-sidebar")
        onRoot().performMouseInput { release() }
        waitForIdle()
        runOnIdle {
            assertEquals(targetDate, state.value.tasks.first { it.id == 1 }.dueDate)
            assertTrue(openedTasks.isEmpty())
            assertTrue(openedDays.isEmpty())
        }
        onNodeWithText("Task moved to October 30").assertIsDisplayed()
        screenshot("calendar-moved")
        onNodeWithText("Undo").performClick()
        runOnIdle { assertNull(state.value.tasks.first { it.id == 1 }.dueDate) }

        val scheduled = onNodeWithTag("calendar-task-121", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput { moveTo(scheduled); press(); moveTo(scheduled + Offset(28f, 0f)); moveTo(target) }
        onNodeWithTag("calendar-day-$targetDate", useUnmergedTree = true).assertIsSelected()
        screenshot("calendar-drag-between-days")
        onRoot().performMouseInput { release() }
        runOnIdle { assertEquals(targetDate, state.value.tasks.first { it.id == 121 }.dueDate) }
        onNodeWithText("Undo").performClick()
        runOnIdle { assertEquals(LocalDate(2026, 10, 28), state.value.tasks.first { it.id == 121 }.dueDate) }

        onRoot().performMouseInput { moveTo(source); press(); moveTo(source + Offset(28f, 0f)); moveTo(Offset(10f, 10f)); release() }
        runOnIdle { assertNull(state.value.tasks.first { it.id == 1 }.dueDate) }
        onNodeWithTag("calendar-undo").assertDoesNotExist()
        onNodeWithText("Organize project notes", useUnmergedTree = true).performClick()
        runOnIdle { assertEquals(listOf(1), openedTasks) }
        onNodeWithTag("calendar-day-2026-10-30", useUnmergedTree = true).performClick()
        runOnIdle { assertEquals(listOf(targetDate), openedDays) }
    }

    @Test
    fun `calendar wheel and touch swipes change months while sidebar wheel remains local`() = runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
        val state = mutableStateOf(calendarPreviewState())
        setContent {
            TaskBTheme(false) {
                CalendarScreen(state.value, { intent ->
                    state.value = when (intent) {
                        CalendarIntent.NextMonth -> reduceCalendar(state.value, CalendarMessage.Month(kotlinx.datetime.YearMonth(2026, 11)))
                        CalendarIntent.PreviousMonth -> reduceCalendar(state.value, CalendarMessage.Month(kotlinx.datetime.YearMonth(2026, 10)))
                        CalendarIntent.Today -> calendarPreviewState()
                        else -> state.value
                    }
                }, {}, {}, {}, {})
            }
        }
        onNodeWithTag("calendar-undated").performMouseInput { moveTo(center); scroll(1f) }
        onNodeWithText("October 2026").assertIsDisplayed()
        onNodeWithTag("calendar-grid").performMouseInput { moveTo(center); scroll(1f) }
        onNodeWithText("November 2026").assertIsDisplayed()
        onNodeWithTag("calendar-grid").performTouchInput { swipeRight() }
        onNodeWithText("October 2026").assertIsDisplayed()
        onNodeWithTag("calendar-grid").performTouchInput { swipeLeft() }
        onNodeWithText("November 2026").assertIsDisplayed()
        onNodeWithText("Today").performClick()
        onNodeWithText("October 2026").assertIsDisplayed()
    }

    @Test
    fun `long press dragging on touch schedules a task without swiping the month`() = runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
        val moves = mutableListOf<CalendarIntent>()
        val opened = mutableListOf<Int>()
        setContent { TaskBTheme(false) { CalendarScreen(calendarPreviewState(), moves::add, {}, opened::add, {}, {}) } }
        val source = onNodeWithTag("calendar-task-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val target = onNodeWithTag("calendar-day-2026-10-30", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        onRoot().performTouchInput {
            down(source)
            advanceEventTime(700)
            moveTo(source + Offset(0f, 1f))
            moveTo(target)
        }
        onNodeWithTag("calendar-day-2026-10-30", useUnmergedTree = true).assertIsSelected()
        onRoot().performTouchInput { up() }
        runOnIdle {
            assertEquals(listOf<CalendarIntent>(CalendarIntent.MoveTask(1, LocalDate(2026, 10, 30))), moves)
            assertTrue(opened.isEmpty())
        }
    }

    @Test
    fun `dark wide view keeps overflow and day actions accessible in short windows`() = runSkikoComposeUiTest(size = Size(1280f, 600f)) {
        val state = calendarPreviewState()
        setContent { TaskBTheme(true) { CalendarScreen(state, {}, {}, {}, {}, {}) } }
        onNodeWithText("New task").assertIsDisplayed()
        onNodeWithTag("calendar-day-2026-10-28", useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("more", substring = true) and hasAnyAncestor(hasTestTag("calendar-day-2026-10-28")), useUnmergedTree = true).assertIsDisplayed()
        screenshot("calendar-wide-dark-short")
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val path = Path.of("build/reports/screenshots")
        Files.createDirectories(path)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.resolve("$name.png").toFile())
    }
}
