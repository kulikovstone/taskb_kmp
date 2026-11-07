package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CalendarSidebarSectionsUiTest {
    @Test
    fun `only the section header contents toggle tasks without highlighting on hover`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 900f),
        ) {
            val state = timedCalendarPreviewState()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(state = state, onIntent = {}, onBack = {}, onOpenTask = {}, onCreateTask = {})
                }
            }
            fun taskNode(task: CalendarItem): SemanticsNodeInteraction {
                return onNode(
                    matcher = hasTestTag(
                        testTag = if (task.isEvent) "calendar-${task.key}" else "calendar-task-${task.key}"
                    ) and hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
            }
            val daily = onNodeWithTag(testTag = "calendar-sidebar-daily-heading")
            val timed = onNodeWithTag(testTag = "calendar-sidebar-events-heading")
            val untimed = onNodeWithTag(testTag = "calendar-sidebar-tasks-heading")
            for (heading in listOf(daily, timed, untimed)) {
                heading
                    .assertIsDisplayed()
                    .assert(
                        matcher = SemanticsMatcher.expectValue(
                            key = SemanticsProperties.StateDescription,
                            expectedValue = "Expanded",
                        )
                    )
                val beforeHover = heading.captureToImage().toPixelMap()
                heading.performMouseInput { moveTo(position = center) }
                mainClock.advanceTimeBy(milliseconds = 200)
                val afterHover = heading.captureToImage().toPixelMap()
                assertEquals(expected = beforeHover.width, actual = afterHover.width)
                assertEquals(expected = beforeHover.height, actual = afterHover.height)
                for (y in 0 until beforeHover.height) for (x in 0 until beforeHover.width) {
                    assertEquals(
                        expected = beforeHover[x, y],
                        actual = afterHover[x, y],
                        message = "Header changed on hover at ($x, $y)",
                    )
                }
            }
            for (task in state.sidebarItems) taskNode(task = task).assertIsDisplayed()
            val timedTopBefore = timed.fetchSemanticsNode().boundsInRoot.top
            val dailyBounds = daily.fetchSemanticsNode().boundsInRoot
            val listBounds = onNodeWithTag(testTag = "calendar-sidebar-list").fetchSemanticsNode().boundsInRoot
            assertTrue(actual = dailyBounds.right < listBounds.right)
            onRoot().performMouseInput {
                click(position = Offset(x = (dailyBounds.right + listBounds.right) / 2f, y = dailyBounds.center.y))
            }
            daily.assert(
                matcher = SemanticsMatcher.expectValue(
                    key = SemanticsProperties.StateDescription,
                    expectedValue = "Expanded",
                )
            )
            val dailyTitleBounds = onNodeWithText(
                text = "Daily · 2",
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            onRoot().performMouseInput {
                click(
                    position = Offset(x = (dailyBounds.left + dailyTitleBounds.left) / 2f, y = dailyBounds.center.y)
                )
            }
            daily.assert(
                matcher = SemanticsMatcher.expectValue(
                    key = SemanticsProperties.StateDescription,
                    expectedValue = "Collapsed",
                )
            )
            for (task in state.sidebarDailyItems) taskNode(task = task).assertDoesNotExist()
            for (task in state.sidebarEventItems + state.sidebarMainTaskItems) taskNode(task = task)
                .assertIsDisplayed()
            assertTrue(actual = timed.fetchSemanticsNode().boundsInRoot.top < timedTopBefore)
            onNodeWithText(text = "Daily · 2").assertIsDisplayed()
            onNodeWithText(text = "Friday · 3 tasks · 3 events").assertIsDisplayed()

            timed.performClick()
            timed.assert(
                matcher = SemanticsMatcher.expectValue(
                    key = SemanticsProperties.StateDescription,
                    expectedValue = "Collapsed",
                )
            )
            for (task in state.sidebarEventItems) taskNode(task = task).assertDoesNotExist()
            for (task in state.sidebarMainTaskItems) taskNode(task = task).assertIsDisplayed()
            onNode(
                    matcher = hasTestTag(testTag = "calendar-event:300") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-day-2026-11-06")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            untimed.performClick()
            untimed.assert(
                matcher = SemanticsMatcher.expectValue(
                    key = SemanticsProperties.StateDescription,
                    expectedValue = "Collapsed",
                )
            )
            for (task in state.sidebarItems) taskNode(task = task).assertDoesNotExist()
            for (heading in listOf(daily, timed, untimed)) heading.assertIsDisplayed()

            onNodeWithText(text = "Daily · 2", useUnmergedTree = true).performMouseInput { click() }
            for (task in state.sidebarDailyItems) taskNode(task = task).assertIsDisplayed()
            for (task in state.sidebarEventItems + state.sidebarMainTaskItems) taskNode(task = task)
                .assertDoesNotExist()
            timed.performClick()
            for (task in state.sidebarEventItems) taskNode(task = task).assertIsDisplayed()
            for (task in state.sidebarMainTaskItems) taskNode(task = task).assertDoesNotExist()
            untimed.performClick()
            for (task in state.sidebarItems) taskNode(task = task).assertIsDisplayed()
            for (heading in listOf(daily, timed, untimed)) {
                heading.assert(
                    matcher = SemanticsMatcher.expectValue(
                        key = SemanticsProperties.StateDescription,
                        expectedValue = "Expanded",
                    )
                )
            }
        }
    }

    @Test
    fun `empty sidebar space stays outside the header click area when the list overflows`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 900f),
        ) {
            val preview = timedCalendarPreviewState()
            val state = preview.copy(
                tasks = preview.tasks +
                    List(size = 30) { index ->
                        Task(
                            id = 400 + index,
                            name = "Additional task ${index + 1}",
                            dueDate = preview.selectedDate,
                        )
                    }
            )
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(state = state, onIntent = {}, onBack = {}, onOpenTask = {}, onCreateTask = {})
                }
            }
            val scrollRange =
                onNodeWithTag(testTag = "calendar-sidebar-list")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(actual = scrollRange.maxValue() > 0f)
            val daily = onNodeWithTag(testTag = "calendar-sidebar-daily-heading").assertIsDisplayed()
            val bounds = daily.fetchSemanticsNode().boundsInRoot
            val listBounds = onNodeWithTag(testTag = "calendar-sidebar-list").fetchSemanticsNode().boundsInRoot
            assertTrue(actual = bounds.right < listBounds.right)
            onRoot().performMouseInput {
                click(position = Offset(x = (bounds.right + listBounds.right) / 2f, y = bounds.center.y))
            }
            daily.assert(
                matcher = SemanticsMatcher.expectValue(
                    key = SemanticsProperties.StateDescription,
                    expectedValue = "Expanded",
                )
            )
            onNodeWithText(text = "Daily · 2", useUnmergedTree = true).performMouseInput { click() }
            daily.assert(
                matcher = SemanticsMatcher.expectValue(
                    key = SemanticsProperties.StateDescription,
                    expectedValue = "Collapsed",
                )
            )
            onNodeWithText(text = "Daily · 2").assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-sidebar-events-heading")
                .assert(
                    matcher = SemanticsMatcher.expectValue(
                        key = SemanticsProperties.StateDescription,
                        expectedValue = "Expanded",
                    )
                )
            onNodeWithText(text = "Events · 3").assertIsDisplayed()
        }
    }

    @Test
    fun `sections start expanded on each selected day and the no date list stays ungrouped`() {
        runSkikoComposeUiTest(
            size = Size(width = 1440f, height = 900f),
        ) {
            val state = mutableStateOf(
                value = timedCalendarPreviewState().let { state ->
                    state.copy(tasks = state.tasks + Task(id = 320, name = "Plan the weekend"))
                }
            )
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = state.value,
                        onIntent = { intent ->
                            state.value = when (intent) {
                                is CalendarIntent.SelectDay -> state.value.copy(selectedDate = intent.date)
                                CalendarIntent.ShowUnscheduled -> state.value.copy(selectedDate = null)
                                else -> state.value
                            }
                        },
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            val headingTags = listOf(
                "calendar-sidebar-daily-heading",
                "calendar-sidebar-events-heading",
                "calendar-sidebar-tasks-heading",
            )
            for (tag in headingTags) onNodeWithTag(testTag = tag).performClick()
            onNodeWithTag(testTag = "calendar-day-number-2026-11-04", useUnmergedTree = true).performClick()
            onNodeWithTag(testTag = "calendar-sidebar-events-heading")
                .assert(
                    matcher = SemanticsMatcher.expectValue(
                        key = SemanticsProperties.StateDescription,
                        expectedValue = "Expanded",
                    )
                )
            onNode(
                    matcher = hasTestTag(testTag = "calendar-event:304") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-day-number-2026-11-06", useUnmergedTree = true).performClick()
            for (tag in headingTags) onNodeWithTag(testTag = tag)
                .assert(
                    matcher = SemanticsMatcher.expectValue(
                        key = SemanticsProperties.StateDescription,
                        expectedValue = "Expanded",
                    )
                )
            for (tag in headingTags) onNodeWithTag(testTag = tag).performClick()
            onNodeWithTag(testTag = "calendar-sidebar-toggle").performClick()
            onNodeWithTag(testTag = "calendar-sidebar-title").assertTextEquals("No date")
            for (tag in headingTags) onNodeWithTag(testTag = tag).assertDoesNotExist()
            onNode(
                    matcher = hasText(text = "Plan the weekend") and
                        hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-day-number-2026-11-06", useUnmergedTree = true).performClick()
            for (tag in headingTags) onNodeWithTag(testTag = tag)
                .assert(
                    matcher = SemanticsMatcher.expectValue(
                        key = SemanticsProperties.StateDescription,
                        expectedValue = "Expanded",
                    )
                )
            for (task in state.value.sidebarItems) onNode(
                    matcher = hasTestTag(
                        testTag = if (task.isEvent) "calendar-${task.key}" else "calendar-task-${task.key}"
                    ) and hasAnyAncestor(matcher = hasTestTag(testTag = "calendar-sidebar-list")),
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
        }
    }
}
