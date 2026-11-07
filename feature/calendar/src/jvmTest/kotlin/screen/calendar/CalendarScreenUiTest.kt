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
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

@OptIn(ExperimentalTestApi::class)
class CalendarScreenUiTest {
    @Test
    fun `selected day sidebar shows descriptions and overflow tasks`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 900f),
        ) {
            val state = mutableStateOf(value = calendarPreviewState())
            val intents = mutableListOf<CalendarIntent>()
            val opened = mutableListOf<Int>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = state.value,
                        onIntent = { intent ->
                            intents.add(element = intent)
                            state.value = when (intent) {
                                is CalendarIntent.SelectDay -> reduceCalendar(
                                    state = state.value,
                                    message = CalendarMessage.SelectedDate(value = intent.date),
                                )
                                CalendarIntent.ShowUnscheduled -> reduceCalendar(
                                    state = state.value,
                                    message = CalendarMessage.SelectedDate(value = null),
                                )
                                CalendarIntent.Today -> reduceCalendar(
                                    state = state.value,
                                    message = CalendarMessage.SelectedDate(value = state.value.today),
                                )
                                else -> state.value
                            }
                        },
                        onBack = {},
                        onOpenTask = opened::add,
                        onCreateTask = {},
                    )
                }
            }
            onNodeWithTag(testTag = "calendar-sidebar-title").assertTextEquals("Oct 29, 2026")
            onNode(
                    matcher = hasText(text = "Summarize progress and the next milestones.") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-choose-date").assertIsEnabled()
            onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
            onNodeWithTag(testTag = "calendar-sidebar-title").assertTextEquals("No date")
            onNodeWithText(text = "Scope, milestones and owners for the next release.", useUnmergedTree = true)
                .assertIsDisplayed()
            val date = LocalDate(year = 2026, month = 10, day = 28)
            onNode(
                    matcher = hasText(text = "more", substring = true) and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$date")),
                    useUnmergedTree = true,
                )
                .performClick()
            onNodeWithTag(testTag = "calendar-sidebar-title").assertTextEquals("Oct 28, 2026")
            onNodeWithTag(testTag = "calendar-day-$date", useUnmergedTree = true).assertIsSelected()
            onNodeWithText(text = "Wednesday · 5 tasks").assertIsDisplayed()
            for (id in 121..125) onNode(
                    matcher = hasTestTag(testTag = "calendar-task-$id") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            screenshot(name = "calendar-selected-day")
            onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
            onNodeWithTag(testTag = "calendar-day-number-2026-10-10", useUnmergedTree = true).performClick()
            onNodeWithTag(testTag = "calendar-sidebar-title").assertTextEquals("Oct 10, 2026")
            onNodeWithText(text = "No tasks or events for this day.").assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-today").performClick()
            onNodeWithTag(testTag = "calendar-sidebar-title").assertTextEquals("Oct 29, 2026")
            onNode(
                    matcher = hasText(text = "Summarize progress and the next milestones.") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar")),
                    useUnmergedTree = true,
                )
                .performClick()
            runOnIdle { assertEquals(expected = listOf(element = 126), actual = opened) }
        }
    }

    @Test
    fun `dragging a scheduled sidebar task preserves its description and sidebar layout`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 900f),
        ) {
            val intents = mutableListOf<CalendarIntent>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = calendarPreviewState(),
                        onIntent = intents::add,
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            val source = onNode(
                    matcher = hasTestTag(testTag = "calendar-task-126") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNode()
                .boundsInRoot
            val target = onNodeWithTag(testTag = "calendar-day-2026-10-08", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .center
            onRoot().performMouseInput {
                moveTo(position = source.center)
                press()
                moveTo(position = source.center + Offset(x = 28f, y = 0f))
                moveTo(position = target)
            }
            val preview = onNodeWithTag(testTag = "calendar-drag-preview", useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            assertEquals(expected = source.width - 48f + 12f, actual = preview.width, absoluteTolerance = 0.5f)
            assertEquals(expected = source.height + 4f, actual = preview.height, absoluteTolerance = 0.5f)
            onNode(
                    matcher = hasText(text = "Summarize progress and the next milestones.") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-drag-preview")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            onRoot().performMouseInput { release() }
            runOnIdle {
                assertEquals(
                    expected = listOf<CalendarIntent>(
                        element = CalendarIntent.MoveTask(
                            id = 126,
                            date = LocalDate(year = 2026, month = 10, day = 8),
                        )
                    ),
                    actual = intents,
                )
            }
        }
    }

    @Test
    fun `mouse dragging highlights days moves from sidebar and between cells and undo restores the source`() {
        runSkikoComposeUiTest(size = Size(width = 1440f, height = 1000f)) {
            val state = mutableStateOf(value = unscheduledPreviewState())
            val openedTasks = mutableListOf<Int>()
            val selectedDays = mutableListOf<LocalDate>()
            val createdDates = mutableListOf<LocalDate?>()
            var token = 0L
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = state.value,
                        onIntent = { intent ->
                            when (intent) {
                                is CalendarIntent.SelectDay -> selectedDays.add(element = intent.date)
                                is CalendarIntent.MoveTask -> {
                                    val task = state.value.tasks.first { task -> task.id == intent.id }
                                    state.value = reduceCalendar(
                                        state = state.value,
                                        message = CalendarMessage.Moved(
                                            move = CalendarMove(
                                                token = ++token,
                                                taskId = task.id,
                                                previousDate = task.dueDate,
                                                date = intent.date,
                                            ),
                                            undoing = false,
                                        ),
                                    )
                                }
                                CalendarIntent.UndoMove -> state.value.undo?.let { move ->
                                    state.value = reduceCalendar(
                                        state = state.value,
                                        message = CalendarMessage.Moved(move = move, undoing = true),
                                    )
                                }
                                else -> Unit
                            }
                        },
                        onBack = {},
                        onOpenTask = openedTasks::add,
                        onCreateTask = createdDates::add,
                    )
                }
            }
            waitForIdle()
            screenshot(name = "calendar-wide")
            val targetDate = LocalDate(year = 2026, month = 10, day = 30)
            val target = onNodeWithTag(testTag = "calendar-day-$targetDate", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .center
            val source = onNodeWithTag(testTag = "calendar-task-1", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .center
            onRoot().performMouseInput {
                moveTo(position = source)
                press()
                moveTo(position = source + Offset(x = 28f, y = 0f))
                moveTo(position = target)
            }
            onNodeWithTag(testTag = "calendar-day-$targetDate", useUnmergedTree = true).assertIsSelected()
            screenshot(name = "calendar-drag-sidebar")
            onRoot().performMouseInput { release() }
            waitForIdle()
            runOnIdle {
                assertEquals(expected = targetDate, actual = state.value.tasks.first { task -> task.id == 1 }.dueDate)
                assertTrue(actual = openedTasks.isEmpty())
                assertTrue(actual = selectedDays.isEmpty())
                assertTrue(actual = createdDates.isEmpty())
            }
            onNodeWithText(text = "Task moved to October 30").assertIsDisplayed()
            screenshot(name = "calendar-moved")
            onNodeWithText(text = "Undo").performClick()
            runOnIdle { assertNull(actual = state.value.tasks.first { task -> task.id == 1 }.dueDate) }

            val scheduled = onNodeWithTag(testTag = "calendar-task-121", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .center
            onRoot().performMouseInput {
                moveTo(position = scheduled)
                press()
                moveTo(position = scheduled + Offset(x = 28f, y = 0f))
                moveTo(position = target)
            }
            onNodeWithTag(testTag = "calendar-day-$targetDate", useUnmergedTree = true).assertIsSelected()
            screenshot(name = "calendar-drag-between-days")
            onRoot().performMouseInput { release() }
            runOnIdle {
                assertEquals(expected = targetDate, actual = state.value.tasks.first { task -> task.id == 121 }.dueDate)
            }
            onNodeWithText(text = "Undo").performClick()
            runOnIdle {
                assertEquals(
                    expected = LocalDate(year = 2026, month = 10, day = 28),
                    actual = state.value.tasks.first { task -> task.id == 121 }.dueDate,
                )
            }

            onRoot().performMouseInput {
                moveTo(position = source)
                press()
                moveTo(position = source + Offset(x = 28f, y = 0f))
                moveTo(position = Offset(x = 10f, y = 10f))
                release()
            }
            runOnIdle { assertNull(actual = state.value.tasks.first { task -> task.id == 1 }.dueDate) }
            onNodeWithTag(testTag = "calendar-undo").assertDoesNotExist()
            onNodeWithText(text = "Organize project notes", useUnmergedTree = true).performClick()
            runOnIdle { assertEquals(expected = listOf(element = 1), actual = openedTasks) }
            onNodeWithTag(testTag = "calendar-day-2026-10-30", useUnmergedTree = true).performClick()
            runOnIdle {
                assertTrue(actual = createdDates.isEmpty())
                assertEquals(expected = listOf(element = targetDate), actual = selectedDays)
            }
            onNodeWithTag(testTag = "calendar-day-number-$targetDate", useUnmergedTree = true).performClick()
            runOnIdle {
                assertEquals(expected = listOf(targetDate, targetDate), actual = selectedDays)
                assertTrue(actual = createdDates.isEmpty())
            }
            val adjacentDate = LocalDate(year = 2026, month = 9, day = 28)
            onNodeWithTag(testTag = "calendar-day-$adjacentDate", useUnmergedTree = true).performClick()
            runOnIdle {
                assertEquals(expected = listOf(targetDate, targetDate, adjacentDate), actual = selectedDays)
                assertTrue(actual = createdDates.isEmpty())
            }
            onNodeWithText(text = "Task").performClick()
            runOnIdle { assertEquals(expected = listOf<LocalDate?>(element = null), actual = createdDates) }
        }
    }

    @Test
    fun `calendar wheel and touch swipes change months while sidebar wheel remains local`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 1000f),
        ) {
            val state = mutableStateOf(value = unscheduledPreviewState())
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = state.value,
                        onIntent = { intent ->
                            state.value = when (intent) {
                                CalendarIntent.NextMonth -> reduceCalendar(
                                    state = state.value,
                                    message = CalendarMessage.Month(
                                        value = kotlinx.datetime.YearMonth(year = 2026, month = 11)
                                    ),
                                )
                                CalendarIntent.PreviousMonth -> reduceCalendar(
                                    state = state.value,
                                    message = CalendarMessage.Month(
                                        value = kotlinx.datetime.YearMonth(year = 2026, month = 10)
                                    ),
                                )
                                CalendarIntent.Today -> unscheduledPreviewState()
                                else -> state.value
                            }
                        },
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            onNodeWithTag(testTag = "calendar-sidebar-list").performMouseInput {
                moveTo(position = center)
                scroll(delta = 1f)
            }
            onNodeWithText(text = "October 2026").assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-grid").performMouseInput {
                moveTo(position = center)
                scroll(delta = 1f)
            }
            onNodeWithText(text = "November 2026").assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-grid").performTouchInput { swipeRight() }
            onNodeWithText(text = "October 2026").assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-grid").performTouchInput { swipeLeft() }
            onNodeWithText(text = "November 2026").assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-today").performClick()
            onNodeWithText(text = "October 2026").assertIsDisplayed()
        }
    }

    @Test
    fun `long press dragging on touch schedules a task without swiping the month`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 1000f),
        ) {
            val moves = mutableListOf<CalendarIntent>()
            val opened = mutableListOf<Int>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = unscheduledPreviewState(),
                        onIntent = moves::add,
                        onBack = {},
                        onOpenTask = opened::add,
                        onCreateTask = {},
                    )
                }
            }
            val source = onNodeWithTag(testTag = "calendar-task-1", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .center
            val target = onNodeWithTag(testTag = "calendar-day-2026-10-30", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .center
            onRoot().performTouchInput {
                down(position = source)
                advanceEventTime(durationMillis = 700)
                moveTo(position = source + Offset(x = 0f, y = 1f))
                moveTo(position = target)
            }
            onNodeWithTag(testTag = "calendar-day-2026-10-30", useUnmergedTree = true).assertIsSelected()
            onRoot().performTouchInput { up() }
            runOnIdle {
                assertEquals(
                    expected = listOf<CalendarIntent>(
                        element = CalendarIntent.MoveTask(id = 1, date = LocalDate(year = 2026, month = 10, day = 30))
                    ),
                    actual = moves,
                )
                assertTrue(actual = opened.isEmpty())
            }
        }
    }

    @Test
    fun `dark wide view keeps overflow and day actions accessible in short windows`() {
        runSkikoComposeUiTest(
            size = Size(width = 1280f, height = 600f),
        ) {
            val state = unscheduledPreviewState()
            setContent {
                TaskBTheme(darkTheme = true) {
                    CalendarScreen(state = state, onIntent = {}, onBack = {}, onOpenTask = {}, onCreateTask = {})
                }
            }
            onNodeWithText(text = "Task").assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-day-2026-10-28", useUnmergedTree = true).assertIsDisplayed()
            onNode(
                    matcher = hasText(text = "more", substring = true) and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-10-28")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            screenshot(name = "calendar-wide-dark-short")
        }
    }

    @Test
    fun `wrapped titles keep the overflow action visible and clickable at different window sizes`() {
        for (size in listOf(Size(width = 1440f, height = 900f), Size(width = 1280f, height = 720f))) {
            runSkikoComposeUiTest(size = size) {
                val state = unscheduledPreviewState()
                val selectedDays = mutableListOf<LocalDate>()
                val createdDates = mutableListOf<LocalDate?>()
                setContent {
                    TaskBTheme(darkTheme = false) {
                        CalendarScreen(
                            state = state,
                            onIntent = { intent ->
                                if (intent is CalendarIntent.SelectDay) selectedDays.add(element = intent.date)
                            },
                            onBack = {},
                            onOpenTask = {},
                            onCreateTask = createdDates::add,
                        )
                    }
                }

                onNodeWithText(text = "No date").assertIsDisplayed()
                val longTitle = onNodeWithText(
                    text = state.tasks.first { task -> task.id == 8 }.name,
                    useUnmergedTree = true,
                )
                    .textLayout()
                assertEquals(expected = 2, actual = longTitle.lineCount)
                assertTrue(actual = longTitle.multiParagraph.didExceedMaxLines)
                val calendarTitle = onNodeWithText(text = "Kotlin study session", useUnmergedTree = true).textLayout()
                assertEquals(expected = 2, actual = calendarTitle.lineCount)

                val date = LocalDate(year = 2026, month = 10, day = 28)
                val dayTag = "calendar-day-$date"
                val day = onNodeWithTag(testTag = dayTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val more = onNode(
                    matcher = hasText(text = "more", substring = true) and
                        hasAnyAncestor(matcher = hasTestTag(testTag = dayTag)),
                    useUnmergedTree = true,
                )
                more.assertIsDisplayed()
                val moreBounds = more.fetchSemanticsNode().boundsInRoot
                assertTrue(actual = moreBounds.height > 0f && moreBounds.bottom <= day.bottom)
                if (size == Size(width = 1440f, height = 900f)) {
                    more.assertTextEquals("+2 more")
                    onNodeWithTag(testTag = "calendar-task-123", useUnmergedTree = true).assertIsDisplayed()
                    onNodeWithTag(testTag = "calendar-task-124", useUnmergedTree = true).assertIsNotDisplayed()
                    val titleBounds = onNodeWithText(text = "Review calendar design", useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .boundsInRoot
                    val moreLayout = more.textLayout()
                    val moreTextStart = moreBounds.right - moreLayout.size.width + moreLayout.getLineLeft(lineIndex = 0)
                    assertEquals(expected = titleBounds.left, actual = moreTextStart, absoluteTolerance = 0.5f)
                    val today = onNodeWithTag(testTag = "calendar-day-2026-10-29", useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .boundsInRoot
                    val todayTask = onNodeWithTag(testTag = "calendar-task-126", useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .boundsInRoot
                    val firstTask = onNodeWithTag(testTag = "calendar-task-121", useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .boundsInRoot
                    assertEquals(
                        expected = firstTask.top - day.top,
                        actual = todayTask.top - today.top,
                        absoluteTolerance = 0.5f,
                    )
                    val dateBounds = onNode(
                            matcher = hasText(text = "28") and hasAnyAncestor(matcher = hasTestTag(testTag = dayTag)),
                            useUnmergedTree = true,
                        )
                        .fetchSemanticsNode()
                        .boundsInRoot
                    val todayBounds = onNode(
                            matcher = hasText(text = "29") and
                                hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-10-29")),
                            useUnmergedTree = true,
                        )
                        .fetchSemanticsNode()
                        .boundsInRoot
                    assertEquals(
                        expected = dateBounds.top - day.top,
                        actual = todayBounds.top - today.top,
                        absoluteTolerance = 0.5f,
                    )
                    assertEquals(
                        expected = dateBounds.left - day.left,
                        actual = todayBounds.left - today.left,
                        absoluteTolerance = 0.5f,
                    )
                }
                var previousBottom = day.top
                for (id in 121..125) {
                    val node = onNodeWithTag(testTag = "calendar-task-$id", useUnmergedTree = true)
                    if (
                        onAllNodesWithTag(testTag = "calendar-task-$id", useUnmergedTree = true)
                            .fetchSemanticsNodes()
                            .isEmpty() || !node.isDisplayed()
                    )
                        continue
                    val row = node.fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        actual = row.top >= previousBottom && row.bottom <= moreBounds.top,
                        message =
                            "Window $size, task $id: $row, previous bottom $previousBottom, overflow $moreBounds",
                    )
                    previousBottom = row.bottom
                }
                screenshot(name = "calendar-design-${size.width.toInt()}x${size.height.toInt()}")
                onRoot().performMouseInput { click(position = Offset(x = day.right - 4f, y = moreBounds.center.y)) }
                runOnIdle {
                    assertTrue(actual = createdDates.isEmpty())
                    assertEquals(expected = listOf(element = date), actual = selectedDays)
                }
                more.performClick()
                runOnIdle {
                    assertEquals(expected = listOf(date, date), actual = selectedDays)
                    assertTrue(actual = createdDates.isEmpty())
                }
            }
        }
    }

    @Test
    fun `six week overflow uses all available space while reserving the more action`() {
        for ((height, expectedVisible) in listOf(900f to 2, 840f to 1)) {
            runSkikoComposeUiTest(size = Size(width = 1440f, height = height)) {
                val date = LocalDate(year = 2026, month = 10, day = 30)
                val title = "Проверить перенос длинного названия задачи"
                val created = unscheduledPreviewState().now.toEpochMilliseconds()
                val tasks = List(size = 6) { index ->
                    Task(
                        id = 1000 + index,
                        name = title,
                        dueDate = date,
                        createdAt = Instant.fromEpochMilliseconds(epochMilliseconds = created - index),
                    )
                }
                val previousDay = LocalDate(year = 2026, month = 10, day = 29)
                val state = unscheduledPreviewState()
                    .copy(
                        month = YearMonth(year = 2026, month = 11),
                        tasks = tasks +
                            List(size = 2) { index ->
                                Task(
                                    id = 2000 + index,
                                    name = title,
                                    dueDate = previousDay,
                                    completedAt = tasks.first().createdAt,
                                )
                            },
                    )
                setContent {
                    TaskBTheme(darkTheme = false) {
                        CalendarScreen(
                            state = state,
                            onIntent = {},
                            onBack = {},
                            onOpenTask = {},
                            onCreateTask = {},
                        )
                    }
                }

                val dayTag = "calendar-day-$date"
                val day = onNodeWithTag(testTag = dayTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val rows = (1000..1005).map { taskId -> onNodeWithTag(
                    testTag = "calendar-task-$taskId",
                    useUnmergedTree = true,
                ) }
                val displayed = rows.filterIndexed { index, node ->
                    onAllNodesWithTag(testTag = "calendar-task-${1000 + index}", useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty() && node.isDisplayed()
                }
                assertEquals(expected = expectedVisible, actual = displayed.size)
                val more = onNode(
                    matcher = hasText(text = "more", substring = true) and
                        hasAnyAncestor(matcher = hasTestTag(testTag = dayTag)),
                    useUnmergedTree = true,
                )
                more.assertTextEquals("+${tasks.size - expectedVisible} more")
                val firstRow = displayed.first().fetchSemanticsNode().boundsInRoot
                val lastRow = displayed.last().fetchSemanticsNode().boundsInRoot
                val moreBounds = more.fetchSemanticsNode().boundsInRoot
                assertTrue(actual = lastRow.bottom <= moreBounds.top && moreBounds.bottom <= day.bottom)
                assertTrue(
                    actual = day.bottom - moreBounds.bottom < firstRow.height,
                    message = "Another row would still fit below the overflow action",
                )
                onNodeWithTag(testTag = "calendar-task-2000", useUnmergedTree = true).assertIsDisplayed()
                onNodeWithTag(testTag = "calendar-task-2001", useUnmergedTree = true).assertIsDisplayed()
                screenshot(name = "calendar-six-week-overflow-${height.toInt()}")
            }
        }
    }

    @Test
    fun `a tall calendar shows more than five tasks when they all fit`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 1100f),
        ) {
            val date = LocalDate(year = 2026, month = 10, day = 30)
            val state = unscheduledPreviewState()
                .copy(
                    tasks = List(size = 6) { index ->
                        Task(id = 1000 + index, name = "Task ${index + 1}", dueDate = date)
                    }
                )
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(state = state, onIntent = {}, onBack = {}, onOpenTask = {}, onCreateTask = {})
                }
            }

            for (id in 1000..1005) onNodeWithTag(testTag = "calendar-task-$id", useUnmergedTree = true)
                .assertIsDisplayed()
            onNode(
                    matcher = hasText(text = "more", substring = true) and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-$date")),
                    useUnmergedTree = true,
                )
                .assertDoesNotExist()
            screenshot(name = "calendar-six-fitting-tasks")
        }
    }

    @Test
    fun `calendar tasks hug complete titles but truncated titles use the available width`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 900f),
        ) {
            val shortDate = LocalDate(year = 2026, month = 10, day = 5)
            val wrappedDate = LocalDate(year = 2026, month = 10, day = 6)
            val longDate = LocalDate(year = 2026, month = 10, day = 7)
            val wrappedName = "Configure autoreply"
            val longName = "Prepare a detailed release plan and review all remaining development tasks"
            val state = unscheduledPreviewState()
                .copy(
                    tasks = listOf(
                        Task(id = 1000, name = "Read", dueDate = shortDate),
                        Task(id = 1001, name = wrappedName, dueDate = wrappedDate),
                        Task(id = 1002, name = longName, dueDate = longDate),
                    )
                )
            val selectedDays = mutableListOf<LocalDate>()
            val openedTasks = mutableListOf<Int>()
            val createdDates = mutableListOf<LocalDate?>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = state,
                        onIntent = { intent ->
                            if (intent is CalendarIntent.SelectDay) selectedDays.add(element = intent.date)
                        },
                        onBack = {},
                        onOpenTask = openedTasks::add,
                        onCreateTask = createdDates::add,
                    )
                }
            }

            val shortRow = onNodeWithTag(testTag = "calendar-task-1000", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val wrappedTask = onNodeWithTag(testTag = "calendar-task-1001", useUnmergedTree = true)
            val wrappedRow = wrappedTask.fetchSemanticsNode().boundsInRoot
            val wrappedDay = onNodeWithTag(testTag = "calendar-day-$wrappedDate", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val longRow = onNodeWithTag(testTag = "calendar-task-1002", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val longDay = onNodeWithTag(testTag = "calendar-day-$longDate", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val wrappedLayout = onNodeWithText(text = wrappedName, useUnmergedTree = true).textLayout()
            val longLayout = onNodeWithText(text = longName, useUnmergedTree = true).textLayout()
            assertEquals(expected = 2, actual = wrappedLayout.lineCount)
            assertFalse(actual = wrappedLayout.multiParagraph.didExceedMaxLines)
            assertEquals(expected = 2, actual = longLayout.lineCount)
            assertTrue(actual = longLayout.multiParagraph.didExceedMaxLines)
            assertTrue(actual = shortRow.width < wrappedRow.width)
            assertTrue(actual = wrappedRow.right < wrappedDay.right - 20f)
            assertTrue(actual = longDay.right - longRow.right <= 4f)

            onNodeWithText(text = wrappedName, useUnmergedTree = true).performMouseInput {
                moveTo(position = center)
            }
            mainClock.advanceTimeBy(milliseconds = 200)
            screenshot(name = "calendar-content-width-hover")
            onRoot().performMouseInput {
                click(position = Offset(x = wrappedRow.right + 8f, y = wrappedRow.center.y))
            }
            runOnIdle {
                assertTrue(actual = createdDates.isEmpty())
                assertEquals(expected = listOf(element = wrappedDate), actual = selectedDays)
                assertTrue(actual = openedTasks.isEmpty())
            }
            onNodeWithText(text = wrappedName, useUnmergedTree = true).performClick()
            runOnIdle { assertEquals(expected = listOf(element = 1001), actual = openedTasks) }
        }
    }

    @Test
    fun `drag preview matches horizontal padding to checkbox gap and preserves title layout`() {
        for (size in listOf(Size(width = 1440f, height = 900f), Size(width = 1280f, height = 720f))) {
            runSkikoComposeUiTest(size = size) {
                val tasks = listOf(
                    Task(id = 1000, name = "Read", dueDate = LocalDate(year = 2026, month = 10, day = 5)),
                    Task(
                        id = 1001,
                        name = "Configure autoreply",
                        dueDate = LocalDate(year = 2026, month = 10, day = 6),
                    ),
                    Task(
                        id = 1002,
                        name = "Prepare a detailed release plan and review all remaining development tasks",
                        dueDate = LocalDate(year = 2026, month = 10, day = 7),
                    ),
                    Task(id = 1003, name = "Unscheduled task", groupId = 1),
                )
                val state = unscheduledPreviewState().copy(tasks = tasks)
                setContent {
                    TaskBTheme(darkTheme = false) {
                        CalendarScreen(
                            state = state,
                            onIntent = {},
                            onBack = {},
                            onOpenTask = {},
                            onCreateTask = {},
                        )
                    }
                }
                val target = onNodeWithTag(testTag = "calendar-day-2026-10-08", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .center
                for (task in tasks) {
                    val source = onNodeWithTag(testTag = "calendar-task-${task.id}", useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .boundsInRoot
                    val sourceTitle = onNodeWithText(text = task.name, useUnmergedTree = true).textLayout()
                    onRoot().performMouseInput {
                        moveTo(position = source.center)
                        press()
                        moveTo(position = source.center + Offset(x = 28f, y = 0f))
                        moveTo(position = target)
                    }
                    val preview = onNodeWithTag(testTag = "calendar-drag-preview", useUnmergedTree = true)
                    val previewBounds = preview.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertEquals(
                        expected = source.height + 4f,
                        actual = previewBounds.height,
                        absoluteTolerance = 0.5f,
                        message = "Task ${task.id} at $size: height",
                    )
                    val previewTitleNode = onNode(
                        matcher = hasText(text = task.name) and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-drag-preview")),
                        useUnmergedTree = true,
                    )
                    val titleBounds = previewTitleNode.fetchSemanticsNode().boundsInRoot
                    val checkboxBounds = onNode(
                            matcher = hasContentDescription(value = "Complete ${task.name}") and
                                hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-drag-preview")),
                            useUnmergedTree = true,
                        )
                        .fetchSemanticsNode()
                        .boundsInRoot
                    val gap = titleBounds.left - checkboxBounds.right
                    assertEquals(
                        expected = gap,
                        actual = checkboxBounds.left - previewBounds.left,
                        absoluteTolerance = 0.5f,
                        message = "Task ${task.id} at $size: left padding",
                    )
                    assertEquals(
                        expected = gap,
                        actual = previewBounds.right - titleBounds.right,
                        absoluteTolerance = 0.5f,
                        message = "Task ${task.id} at $size: right padding",
                    )
                    val previewTitle = previewTitleNode.textLayout()
                    assertEquals(
                        expected = sourceTitle.size,
                        actual = previewTitle.size,
                        message = "Task ${task.id} at $size: title size",
                    )
                    assertEquals(
                        expected = sourceTitle.lineCount,
                        actual = previewTitle.lineCount,
                        message = "Task ${task.id} at $size: title wrapping",
                    )
                    assertEquals(
                        expected = sourceTitle.multiParagraph.didExceedMaxLines,
                        actual = previewTitle.multiParagraph.didExceedMaxLines,
                    )
                    if (task.id == 1000 || task.id == 1001)
                        screenshot(name = "calendar-drag-size-${task.id}-${size.width.toInt()}")
                    onRoot().performMouseInput { release() }
                    preview.assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun `mouse hover highlights the whole task without highlighting its day and checkbox stays independent`() {
        runSkikoComposeUiTest(size = Size(width = 1440f, height = 1000f)) {
            val intents = mutableListOf<CalendarIntent>()
            val openedTasks = mutableListOf<Int>()
            val createdDates = mutableListOf<LocalDate?>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = unscheduledPreviewState(),
                        onIntent = intents::add,
                        onBack = {},
                        onOpenTask = openedTasks::add,
                        onCreateTask = createdDates::add,
                    )
                }
            }

            val emptyDay = onNodeWithTag(testTag = "calendar-day-2026-10-10", useUnmergedTree = true)
            val emptyBefore = emptyDay.captureToImage().toPixelMap()
            emptyDay.performMouseInput { moveTo(position = center) }
            mainClock.advanceTimeBy(milliseconds = 200)
            val emptyAfter = emptyDay.captureToImage().toPixelMap()
            assertEquals(
                expected = emptyBefore[emptyBefore.width / 2, emptyBefore.height - 8],
                actual = emptyAfter[emptyAfter.width / 2, emptyAfter.height - 8],
            )

            val task = onNodeWithTag(testTag = "calendar-task-121", useUnmergedTree = true)
            val day = onNodeWithTag(testTag = "calendar-day-2026-10-28", useUnmergedTree = true)
            val taskBefore = task.captureToImage().toPixelMap()
            val dayBefore = day.captureToImage().toPixelMap()
            onNodeWithText(text = "Review calendar design", useUnmergedTree = true).performMouseInput {
                moveTo(position = center)
            }
            mainClock.advanceTimeBy(milliseconds = 200)
            val taskAfter = task.captureToImage().toPixelMap()
            val dayAfter = day.captureToImage().toPixelMap()
            assertNotEquals(
                illegal = taskBefore[2, taskBefore.height / 2],
                actual = taskAfter[2, taskAfter.height / 2],
            )
            assertEquals(
                expected = dayBefore[dayBefore.width - 2, dayBefore.height / 2],
                actual = dayAfter[dayAfter.width - 2, dayAfter.height / 2],
            )
            screenshot(name = "calendar-task-hover")

            val checkbox = onNodeWithContentDescription(
                label = "Complete Review calendar design",
                useUnmergedTree = true,
            )
            assertEquals(
                expected = task.fetchSemanticsNode().boundsInRoot.center.y,
                actual = checkbox.fetchSemanticsNode().boundsInRoot.center.y,
                absoluteTolerance = 0.5f,
            )
            checkbox.performMouseInput { moveTo(position = center) }
            mainClock.advanceTimeBy(milliseconds = 200)
            val checkboxHover = task.captureToImage().toPixelMap()
            assertNotEquals(
                illegal = taskBefore[2, taskBefore.height / 2],
                actual = checkboxHover[2, checkboxHover.height / 2],
            )
            checkbox.performClick()
            runOnIdle {
                assertEquals(
                    expected = listOf<CalendarIntent>(element = CalendarIntent.ToggleCompleted(id = 121)),
                    actual = intents,
                )
                assertTrue(actual = openedTasks.isEmpty())
                assertTrue(actual = createdDates.isEmpty())
            }
            onNodeWithText(text = "Review calendar design", useUnmergedTree = true).performClick()
            runOnIdle {
                assertEquals(expected = listOf(element = 121), actual = openedTasks)
                assertTrue(actual = createdDates.isEmpty())
            }

            val sidebarTask = onNodeWithTag(testTag = "calendar-task-1", useUnmergedTree = true)
            val sidebarBefore = sidebarTask.captureToImage().toPixelMap()
            onNodeWithText(text = "Organize project notes", useUnmergedTree = true).performMouseInput {
                moveTo(position = center)
            }
            mainClock.advanceTimeBy(milliseconds = 200)
            val sidebarAfter = sidebarTask.captureToImage().toPixelMap()
            assertNotEquals(illegal = sidebarBefore[1, 1], actual = sidebarAfter[1, 1])
            screenshot(name = "calendar-sidebar-hover")
        }
    }

    private fun unscheduledPreviewState() = calendarPreviewState().copy(selectedDate = null)

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(key = SemanticsActions.GetTextLayoutResult) { getLayoutResult ->
            getLayoutResult(layouts)
        }
        return layouts.single()
    }

    private fun SkikoComposeUiTest.screenshot(name: String) {
        val path = Path.of("build/reports/screenshots")
        Files.createDirectories(path)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.resolve("$name.png").toFile())
    }
}
