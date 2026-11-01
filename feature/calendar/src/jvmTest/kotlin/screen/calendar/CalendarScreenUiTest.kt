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
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CalendarScreenUiTest {
    @Test
    fun `mouse dragging highlights days moves from sidebar and between cells and undo restores the source`() = runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
        val state = mutableStateOf(calendarPreviewState())
        val openedTasks = mutableListOf<Int>()
        val openedDays = mutableListOf<LocalDate>()
        val createdDates = mutableListOf<LocalDate?>()
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
                }, {}, openedTasks::add, createdDates::add, openedDays::add)
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
            assertTrue(createdDates.isEmpty())
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
        runOnIdle {
            assertEquals(listOf<LocalDate?>(targetDate), createdDates)
            assertTrue(openedDays.isEmpty())
        }
        onNodeWithTag("calendar-day-number-$targetDate", useUnmergedTree = true).performClick()
        runOnIdle {
            assertEquals(listOf(targetDate), openedDays)
            assertEquals(listOf<LocalDate?>(targetDate), createdDates)
        }
        val adjacentDate = LocalDate(2026, 9, 28)
        onNodeWithTag("calendar-day-$adjacentDate", useUnmergedTree = true).performClick()
        runOnIdle { assertEquals(listOf<LocalDate?>(targetDate, adjacentDate), createdDates) }
        onNodeWithText("New task").performClick()
        runOnIdle { assertEquals(listOf(targetDate, adjacentDate, null), createdDates) }
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

    @Test
    fun `wrapped titles keep the overflow action visible and clickable at different window sizes`() {
        for (size in listOf(Size(1440f, 900f), Size(1280f, 720f))) {
            runSkikoComposeUiTest(size = size) {
                val state = calendarPreviewState()
                val openedDays = mutableListOf<LocalDate>()
                val createdDates = mutableListOf<LocalDate?>()
                setContent { TaskBTheme(false) { CalendarScreen(state, {}, {}, {}, createdDates::add, openedDays::add) } }

                onNodeWithText("Unscheduled").assertIsDisplayed()
                val longTitle = onNodeWithText(state.tasks.first { it.id == 8 }.name, useUnmergedTree = true).textLayout()
                assertEquals(2, longTitle.lineCount)
                assertTrue(longTitle.multiParagraph.didExceedMaxLines)
                val calendarTitle = onNodeWithText("Kotlin study session", useUnmergedTree = true).textLayout()
                assertEquals(2, calendarTitle.lineCount)

                val date = LocalDate(2026, 10, 28)
                val dayTag = "calendar-day-$date"
                val day = onNodeWithTag(dayTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val more = onNode(hasText("more", substring = true) and hasAnyAncestor(hasTestTag(dayTag)), useUnmergedTree = true)
                more.assertIsDisplayed()
                val moreBounds = more.fetchSemanticsNode().boundsInRoot
                assertTrue(moreBounds.height > 0f && moreBounds.bottom <= day.bottom)
                if (size == Size(1440f, 900f)) {
                    more.assertTextEquals("+2 more")
                    onNodeWithTag("calendar-task-123", useUnmergedTree = true).assertIsDisplayed()
                    onNodeWithTag("calendar-task-124", useUnmergedTree = true).assertIsNotDisplayed()
                    val titleBounds = onNodeWithText("Review calendar design", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val moreLayout = more.textLayout()
                    val moreTextStart = moreBounds.right - moreLayout.size.width + moreLayout.getLineLeft(0)
                    assertEquals(titleBounds.left, moreTextStart, absoluteTolerance = 0.5f)
                    val today = onNodeWithTag("calendar-day-2026-10-29", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val todayTask = onNodeWithTag("calendar-task-126", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val firstTask = onNodeWithTag("calendar-task-121", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertEquals(firstTask.top - day.top, todayTask.top - today.top, absoluteTolerance = 0.5f)
                    val dateBounds = onNode(hasText("28") and hasAnyAncestor(hasTestTag(dayTag)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val todayBounds = onNode(hasText("29") and hasAnyAncestor(hasTestTag("calendar-day-2026-10-29")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertEquals(dateBounds.top - day.top, todayBounds.top - today.top, absoluteTolerance = 0.5f)
                    assertEquals(dateBounds.left - day.left, todayBounds.left - today.left, absoluteTolerance = 0.5f)
                }
                var previousBottom = day.top
                for (id in 121..125) {
                    val node = onNodeWithTag("calendar-task-$id", useUnmergedTree = true)
                    if (onAllNodesWithTag("calendar-task-$id", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() || !node.isDisplayed()) continue
                    val row = node.fetchSemanticsNode().boundsInRoot
                    assertTrue(row.top >= previousBottom && row.bottom <= moreBounds.top,
                        "Window $size, task $id: $row, previous bottom $previousBottom, overflow $moreBounds")
                    previousBottom = row.bottom
                }
                screenshot("calendar-design-${size.width.toInt()}x${size.height.toInt()}")
                onRoot().performMouseInput { click(Offset(day.right - 4f, moreBounds.center.y)) }
                runOnIdle {
                    assertEquals(listOf<LocalDate?>(date), createdDates)
                    assertTrue(openedDays.isEmpty())
                }
                more.performClick()
                runOnIdle {
                    assertEquals(listOf(date), openedDays)
                    assertEquals(listOf<LocalDate?>(date), createdDates)
                }
            }
        }
    }

    @Test
    fun `six week overflow uses all available space while reserving the more action`() {
        for ((height, expectedVisible) in listOf(900f to 2, 864f to 1)) {
            runSkikoComposeUiTest(size = Size(1440f, height)) {
                val date = LocalDate(2026, 10, 30)
                val title = "Проверить перенос длинного названия задачи"
                val created = calendarPreviewState().now.toEpochMilliseconds()
                val tasks = List(6) { index -> Task(id = 1000 + index, name = title, dueDate = date,
                    createdAt = Instant.fromEpochMilliseconds(created - index)) }
                val previousDay = LocalDate(2026, 10, 29)
                val state = calendarPreviewState().copy(month = YearMonth(2026, 11), tasks = tasks + List(2) { index ->
                    Task(id = 2000 + index, name = title, dueDate = previousDay, completedAt = tasks.first().createdAt)
                })
                setContent { TaskBTheme(false) { CalendarScreen(state, {}, {}, {}, {}, {}) } }

                val dayTag = "calendar-day-$date"
                val day = onNodeWithTag(dayTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val rows = (1000..1005).map { onNodeWithTag("calendar-task-$it", useUnmergedTree = true) }
                val displayed = rows.filterIndexed { index, node ->
                    onAllNodesWithTag("calendar-task-${1000 + index}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() && node.isDisplayed()
                }
                assertEquals(expectedVisible, displayed.size)
                val more = onNode(hasText("more", substring = true) and hasAnyAncestor(hasTestTag(dayTag)), useUnmergedTree = true)
                more.assertTextEquals("+${tasks.size - expectedVisible} more")
                val firstRow = displayed.first().fetchSemanticsNode().boundsInRoot
                val lastRow = displayed.last().fetchSemanticsNode().boundsInRoot
                val moreBounds = more.fetchSemanticsNode().boundsInRoot
                assertTrue(lastRow.bottom <= moreBounds.top && moreBounds.bottom <= day.bottom)
                assertTrue(day.bottom - moreBounds.bottom < firstRow.height, "Another row would still fit below the overflow action")
                onNodeWithTag("calendar-task-2000", useUnmergedTree = true).assertIsDisplayed()
                onNodeWithTag("calendar-task-2001", useUnmergedTree = true).assertIsDisplayed()
                screenshot("calendar-six-week-overflow-${height.toInt()}")
            }
        }
    }

    @Test
    fun `a tall calendar shows more than five tasks when they all fit`() = runSkikoComposeUiTest(size = Size(1440f, 1100f)) {
        val date = LocalDate(2026, 10, 30)
        val state = calendarPreviewState().copy(tasks = List(6) { index ->
            Task(id = 1000 + index, name = "Task ${index + 1}", dueDate = date)
        })
        setContent { TaskBTheme(false) { CalendarScreen(state, {}, {}, {}, {}, {}) } }

        for (id in 1000..1005) onNodeWithTag("calendar-task-$id", useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("more", substring = true) and hasAnyAncestor(hasTestTag("calendar-day-$date")), useUnmergedTree = true).assertDoesNotExist()
        screenshot("calendar-six-fitting-tasks")
    }

    @Test
    fun `calendar tasks hug complete titles but truncated titles use the available width`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val shortDate = LocalDate(2026, 10, 5)
        val wrappedDate = LocalDate(2026, 10, 6)
        val longDate = LocalDate(2026, 10, 7)
        val wrappedName = "Configure autoreply"
        val longName = "Prepare a detailed release plan and review all remaining development tasks"
        val state = calendarPreviewState().copy(tasks = listOf(
            Task(1000, "Read", dueDate = shortDate),
            Task(1001, wrappedName, dueDate = wrappedDate),
            Task(1002, longName, dueDate = longDate),
        ))
        val openedDays = mutableListOf<LocalDate>()
        val openedTasks = mutableListOf<Int>()
        val createdDates = mutableListOf<LocalDate?>()
        setContent { TaskBTheme(false) { CalendarScreen(state, {}, {}, openedTasks::add, createdDates::add, openedDays::add) } }

        val shortRow = onNodeWithTag("calendar-task-1000", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val wrappedTask = onNodeWithTag("calendar-task-1001", useUnmergedTree = true)
        val wrappedRow = wrappedTask.fetchSemanticsNode().boundsInRoot
        val wrappedDay = onNodeWithTag("calendar-day-$wrappedDate", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val longRow = onNodeWithTag("calendar-task-1002", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val longDay = onNodeWithTag("calendar-day-$longDate", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val wrappedLayout = onNodeWithText(wrappedName, useUnmergedTree = true).textLayout()
        val longLayout = onNodeWithText(longName, useUnmergedTree = true).textLayout()
        assertEquals(2, wrappedLayout.lineCount)
        assertFalse(wrappedLayout.multiParagraph.didExceedMaxLines)
        assertEquals(2, longLayout.lineCount)
        assertTrue(longLayout.multiParagraph.didExceedMaxLines)
        assertTrue(shortRow.width < wrappedRow.width)
        assertTrue(wrappedRow.right < wrappedDay.right - 20f)
        assertTrue(longDay.right - longRow.right <= 4f)

        onNodeWithText(wrappedName, useUnmergedTree = true).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(200)
        screenshot("calendar-content-width-hover")
        onRoot().performMouseInput { click(Offset(wrappedRow.right + 8f, wrappedRow.center.y)) }
        runOnIdle {
            assertEquals(listOf<LocalDate?>(wrappedDate), createdDates)
            assertTrue(openedDays.isEmpty())
            assertTrue(openedTasks.isEmpty())
        }
        onNodeWithText(wrappedName, useUnmergedTree = true).performClick()
        runOnIdle { assertEquals(listOf(1001), openedTasks) }
    }

    @Test
    fun `drag preview matches horizontal padding to checkbox gap and preserves title layout`() {
        for (size in listOf(Size(1440f, 900f), Size(1280f, 720f))) {
            runSkikoComposeUiTest(size = size) {
                val tasks = listOf(
                    Task(1000, "Read", dueDate = LocalDate(2026, 10, 5)),
                    Task(1001, "Configure autoreply", dueDate = LocalDate(2026, 10, 6)),
                    Task(1002, "Prepare a detailed release plan and review all remaining development tasks", dueDate = LocalDate(2026, 10, 7)),
                    Task(1003, "Unscheduled task", groupId = 1),
                )
                val state = calendarPreviewState().copy(tasks = tasks)
                setContent { TaskBTheme(false) { CalendarScreen(state, {}, {}, {}, {}, {}) } }
                val target = onNodeWithTag("calendar-day-2026-10-08", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
                for (task in tasks) {
                    val source = onNodeWithTag("calendar-task-${task.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val sourceTitle = onNodeWithText(task.name, useUnmergedTree = true).textLayout()
                    onRoot().performMouseInput {
                        moveTo(source.center)
                        press()
                        moveTo(source.center + Offset(28f, 0f))
                        moveTo(target)
                    }
                    val preview = onNodeWithTag("calendar-drag-preview", useUnmergedTree = true)
                    val previewBounds = preview.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertEquals(source.height + 4f, previewBounds.height, 0.5f, "Task ${task.id} at $size: height")
                    val previewTitleNode = onNode(hasText(task.name) and hasAnyAncestor(hasTestTag("calendar-drag-preview")), useUnmergedTree = true)
                    val titleBounds = previewTitleNode.fetchSemanticsNode().boundsInRoot
                    val checkboxBounds = onNode(hasContentDescription("Complete ${task.name}") and hasAnyAncestor(hasTestTag("calendar-drag-preview")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val gap = titleBounds.left - checkboxBounds.right
                    assertEquals(gap, checkboxBounds.left - previewBounds.left, 0.5f, "Task ${task.id} at $size: left padding")
                    assertEquals(gap, previewBounds.right - titleBounds.right, 0.5f, "Task ${task.id} at $size: right padding")
                    val previewTitle = previewTitleNode.textLayout()
                    assertEquals(sourceTitle.size, previewTitle.size, "Task ${task.id} at $size: title size")
                    assertEquals(sourceTitle.lineCount, previewTitle.lineCount, "Task ${task.id} at $size: title wrapping")
                    assertEquals(sourceTitle.multiParagraph.didExceedMaxLines, previewTitle.multiParagraph.didExceedMaxLines)
                    if (task.id == 1000 || task.id == 1001) screenshot("calendar-drag-size-${task.id}-${size.width.toInt()}")
                    onRoot().performMouseInput { release() }
                    preview.assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun `mouse hover highlights the whole task without highlighting its day and checkbox stays independent`() = runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
        val intents = mutableListOf<CalendarIntent>()
        val openedTasks = mutableListOf<Int>()
        val openedDays = mutableListOf<LocalDate>()
        val createdDates = mutableListOf<LocalDate?>()
        setContent { TaskBTheme(false) { CalendarScreen(calendarPreviewState(), intents::add, {}, openedTasks::add, createdDates::add, openedDays::add) } }

        val emptyDay = onNodeWithTag("calendar-day-2026-10-10", useUnmergedTree = true)
        val emptyBefore = emptyDay.captureToImage().toPixelMap()
        emptyDay.performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(200)
        val emptyAfter = emptyDay.captureToImage().toPixelMap()
        assertEquals(emptyBefore[emptyBefore.width / 2, emptyBefore.height - 8], emptyAfter[emptyAfter.width / 2, emptyAfter.height - 8])

        val task = onNodeWithTag("calendar-task-121", useUnmergedTree = true)
        val day = onNodeWithTag("calendar-day-2026-10-28", useUnmergedTree = true)
        val taskBefore = task.captureToImage().toPixelMap()
        val dayBefore = day.captureToImage().toPixelMap()
        onNodeWithText("Review calendar design", useUnmergedTree = true).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(200)
        val taskAfter = task.captureToImage().toPixelMap()
        val dayAfter = day.captureToImage().toPixelMap()
        assertNotEquals(taskBefore[2, taskBefore.height / 2], taskAfter[2, taskAfter.height / 2])
        assertEquals(dayBefore[dayBefore.width - 2, dayBefore.height / 2], dayAfter[dayAfter.width - 2, dayAfter.height / 2])
        screenshot("calendar-task-hover")

        val checkbox = onNodeWithContentDescription("Complete Review calendar design", useUnmergedTree = true)
        assertEquals(task.fetchSemanticsNode().boundsInRoot.center.y, checkbox.fetchSemanticsNode().boundsInRoot.center.y, absoluteTolerance = 0.5f)
        checkbox.performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(200)
        val checkboxHover = task.captureToImage().toPixelMap()
        assertNotEquals(taskBefore[2, taskBefore.height / 2], checkboxHover[2, checkboxHover.height / 2])
        checkbox.performClick()
        runOnIdle {
            assertEquals(listOf<CalendarIntent>(CalendarIntent.ToggleCompleted(121)), intents)
            assertTrue(openedTasks.isEmpty())
            assertTrue(openedDays.isEmpty())
            assertTrue(createdDates.isEmpty())
        }
        onNodeWithText("Review calendar design", useUnmergedTree = true).performClick()
        runOnIdle {
            assertEquals(listOf(121), openedTasks)
            assertTrue(openedDays.isEmpty())
            assertTrue(createdDates.isEmpty())
        }

        val sidebarTask = onNodeWithTag("calendar-task-1", useUnmergedTree = true)
        val sidebarBefore = sidebarTask.captureToImage().toPixelMap()
        onNodeWithText("Organize project notes", useUnmergedTree = true).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(200)
        val sidebarAfter = sidebarTask.captureToImage().toPixelMap()
        assertNotEquals(sidebarBefore[1, 1], sidebarAfter[1, 1])
        screenshot("calendar-sidebar-hover")
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val path = Path.of("build/reports/screenshots")
        Files.createDirectories(path)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.resolve("$name.png").toFile())
    }
}
