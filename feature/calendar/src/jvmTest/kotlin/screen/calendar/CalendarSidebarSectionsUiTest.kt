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
    fun `only the section header contents toggle tasks without highlighting on hover`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val state = timedCalendarPreviewState()
        setContent { TaskBTheme(false) { CalendarScreen(state, {}, {}, {}, {}) } }
        fun taskNode(task: CalendarItem) = onNode(
            hasTestTag(if (task.isEvent) "calendar-${task.key}" else "calendar-task-${task.key}") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
            useUnmergedTree = true,
        )
        val daily = onNodeWithTag("calendar-sidebar-daily-heading")
        val timed = onNodeWithTag("calendar-sidebar-events-heading")
        val untimed = onNodeWithTag("calendar-sidebar-tasks-heading")
        for (heading in listOf(daily, timed, untimed)) {
            heading.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
            val beforeHover = heading.captureToImage().toPixelMap()
            heading.performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(200)
            val afterHover = heading.captureToImage().toPixelMap()
            assertEquals(beforeHover.width, afterHover.width)
            assertEquals(beforeHover.height, afterHover.height)
            for (y in 0 until beforeHover.height) for (x in 0 until beforeHover.width) {
                assertEquals(beforeHover[x, y], afterHover[x, y], "Header changed on hover at ($x, $y)")
            }
        }
        for (task in state.sidebarItems) taskNode(task).assertIsDisplayed()
        val timedTopBefore = timed.fetchSemanticsNode().boundsInRoot.top
        val dailyBounds = daily.fetchSemanticsNode().boundsInRoot
        val listBounds = onNodeWithTag("calendar-sidebar-list").fetchSemanticsNode().boundsInRoot
        assertTrue(dailyBounds.right < listBounds.right)
        onRoot().performMouseInput { click(Offset((dailyBounds.right + listBounds.right) / 2f, dailyBounds.center.y)) }
        daily.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        val dailyTitleBounds = onNodeWithText("Daily · 2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        onRoot().performMouseInput { click(Offset((dailyBounds.left + dailyTitleBounds.left) / 2f, dailyBounds.center.y)) }
        daily.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        for (task in state.sidebarDailyItems) taskNode(task).assertDoesNotExist()
        for (task in state.sidebarEventItems + state.sidebarMainTaskItems) taskNode(task).assertIsDisplayed()
        assertTrue(timed.fetchSemanticsNode().boundsInRoot.top < timedTopBefore)
        onNodeWithText("Daily · 2").assertIsDisplayed()
        onNodeWithText("Friday · 3 tasks · 3 events").assertIsDisplayed()

        timed.performClick()
        timed.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        for (task in state.sidebarEventItems) taskNode(task).assertDoesNotExist()
        for (task in state.sidebarMainTaskItems) taskNode(task).assertIsDisplayed()
        onNode(
            hasTestTag("calendar-event:300") and hasAnyAncestor(hasTestTag("calendar-day-2026-11-06")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        untimed.performClick()
        untimed.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        for (task in state.sidebarItems) taskNode(task).assertDoesNotExist()
        for (heading in listOf(daily, timed, untimed)) heading.assertIsDisplayed()

        onNodeWithText("Daily · 2", useUnmergedTree = true).performMouseInput { click() }
        for (task in state.sidebarDailyItems) taskNode(task).assertIsDisplayed()
        for (task in state.sidebarEventItems + state.sidebarMainTaskItems) taskNode(task).assertDoesNotExist()
        timed.performClick()
        for (task in state.sidebarEventItems) taskNode(task).assertIsDisplayed()
        for (task in state.sidebarMainTaskItems) taskNode(task).assertDoesNotExist()
        untimed.performClick()
        for (task in state.sidebarItems) taskNode(task).assertIsDisplayed()
        for (heading in listOf(daily, timed, untimed)) {
            heading.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        }
    }

    @Test
    fun `empty sidebar space stays outside the header click area when the list overflows`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val preview = timedCalendarPreviewState()
        val state = preview.copy(tasks = preview.tasks + List(30) { index ->
            Task(400 + index, "Additional task ${index + 1}", dueDate = preview.selectedDate)
        })
        setContent { TaskBTheme(false) { CalendarScreen(state, {}, {}, {}, {}) } }
        val scrollRange = onNodeWithTag("calendar-sidebar-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scrollRange.maxValue() > 0f)
        val daily = onNodeWithTag("calendar-sidebar-daily-heading").assertIsDisplayed()
        val bounds = daily.fetchSemanticsNode().boundsInRoot
        val listBounds = onNodeWithTag("calendar-sidebar-list").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.right < listBounds.right)
        onRoot().performMouseInput { click(Offset((bounds.right + listBounds.right) / 2f, bounds.center.y)) }
        daily.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        onNodeWithText("Daily · 2", useUnmergedTree = true).performMouseInput { click() }
        daily.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        onNodeWithText("Daily · 2").assertIsDisplayed()
        onNodeWithTag("calendar-sidebar-events-heading")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        onNodeWithText("Events · 3").assertIsDisplayed()
    }

    @Test
    fun `sections start expanded on each selected day and the no date list stays ungrouped`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val state = mutableStateOf(timedCalendarPreviewState().let { it.copy(tasks = it.tasks + Task(320, "Plan the weekend")) })
        setContent {
            TaskBTheme(false) {
                CalendarScreen(state.value, { intent ->
                    state.value = when (intent) {
                        is CalendarIntent.SelectDay -> state.value.copy(selectedDate = intent.date)
                        CalendarIntent.ShowUnscheduled -> state.value.copy(selectedDate = null)
                        else -> state.value
                    }
                }, {}, {}, {})
            }
        }
        val headingTags = listOf(
            "calendar-sidebar-daily-heading",
            "calendar-sidebar-events-heading",
            "calendar-sidebar-tasks-heading",
        )
        for (tag in headingTags) onNodeWithTag(tag).performClick()
        onNodeWithTag("calendar-day-number-2026-11-04", useUnmergedTree = true).performClick()
        onNodeWithTag("calendar-sidebar-events-heading")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        onNode(
            hasTestTag("calendar-event:304") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithTag("calendar-day-number-2026-11-06", useUnmergedTree = true).performClick()
        for (tag in headingTags) onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        for (tag in headingTags) onNodeWithTag(tag).performClick()
        onNodeWithTag("calendar-sidebar-toggle").performClick()
        onNodeWithTag("calendar-sidebar-title").assertTextEquals("No date")
        for (tag in headingTags) onNodeWithTag(tag).assertDoesNotExist()
        onNode(hasText("Plan the weekend") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithTag("calendar-day-number-2026-11-06", useUnmergedTree = true).performClick()
        for (tag in headingTags) onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        for (task in state.value.sidebarItems) onNode(
            hasTestTag(if (task.isEvent) "calendar-${task.key}" else "calendar-task-${task.key}") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }
}
