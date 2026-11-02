package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CalendarTimedTasksUiTest {
    @Test
    fun `timed rows match the calendar design and every selected-day task appears in the sidebar`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val opened = mutableListOf<Int>()
        setContent { TaskBTheme(false) { CalendarScreen(timedCalendarPreviewState(), {}, {}, opened::add, {}) } }
        onNodeWithText("Daily · 2").assertIsDisplayed()
        onNodeWithText("With time · 3").assertIsDisplayed()
        onNodeWithText("Without time · 1").assertIsDisplayed()
        onNodeWithText("Friday · 6 tasks").assertIsDisplayed()
        onNode(hasText("+1 more") and hasAnyAncestor(hasTestTag("calendar-day-2026-11-06")), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("12:00–13:00 (1 hour)").assertIsDisplayed()
        onNodeWithText("15:00–15:30 (30 min)").assertIsDisplayed()
        onNodeWithText("6 Nov, 18:00–9 Nov, 10:00 (2 days 16 hr)").assertIsDisplayed()
        for (id in 300..303) onNode(hasTestTag("calendar-task-$id") and hasAnyAncestor(hasTestTag("calendar-sidebar")), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("Complete Business trip", useUnmergedTree = true).assertDoesNotExist()
        onNode(hasContentDescription("Complete Pay utility bills") and hasAnyAncestor(hasTestTag("calendar-sidebar-list")), useUnmergedTree = true).assertExists()
        onAllNodesWithTag("calendar-task-marker-301", useUnmergedTree = true).assertCountEquals(0)

        for ((day, label) in listOf(6 to "18:00 Business trip", 7 to "Business trip", 8 to "Business trip", 9 to "Business trip 10:00")) {
            onNode(hasText(label) and hasAnyAncestor(hasTestTag("calendar-day-2026-11-${day.toString().padStart(2, '0')}")), useUnmergedTree = true).assertIsDisplayed()
        }
        val active = onNodeWithText("12:00 Team sync", useUnmergedTree = true).textLayout()
        assertEquals(Palette.BlueGrey.s600, active.layoutInput.text.spanStyles.single().item.color)
        assertEquals(Palette.BlueGrey.s900, active.layoutInput.style.color)
        val end = onNodeWithText("Business trip 10:00", useUnmergedTree = true).textLayout()
        assertEquals(14, end.layoutInput.text.spanStyles.single().start)
        assertEquals(Palette.BlueGrey.s600, end.layoutInput.text.spanStyles.single().item.color)
        val past = onNodeWithText("16:00 Sprint review", useUnmergedTree = true).textLayout()
        assertEquals(Palette.Grey.s400, past.layoutInput.text.spanStyles.single().item.color)
        assertEquals(Palette.Grey.s400, past.layoutInput.style.color)
        assertEquals(TextDecoration.None, past.layoutInput.style.textDecoration)
        assertEquals(2, onNodeWithText("11:00 Review mobile app design", useUnmergedTree = true).textLayout().lineCount)
        assertEquals(1, onNodeWithText("6 Nov, 18:00–9 Nov, 10:00 (2 days 16 hr)", useUnmergedTree = true).textLayout().lineCount)

        val coffee = onNodeWithTag("calendar-task-time-301", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val team = onNodeWithTag("calendar-task-time-300", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(24f, coffee.left, 0.5f)
        assertEquals(36f, team.left, 0.5f)
        val marker = onNode(hasTestTag("calendar-task-marker-300") and hasAnyAncestor(hasTestTag("calendar-sidebar")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(team.top + 3f, marker.top, 0.5f)
        assertEquals(52f, marker.height, 0.5f)

        onNode(hasText("Business trip") and hasAnyAncestor(hasTestTag("calendar-day-2026-11-07")), useUnmergedTree = true).performClick()
        runOnIdle { assertEquals(listOf(302), opened) }
        val path = Path.of("build/reports/screenshots")
        Files.createDirectories(path)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.resolve("calendar-timed-tasks.png").toFile())
    }

    @Test
    fun `dragging a continuing event shifts its start by the dragged day delta`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val intents = mutableListOf<CalendarIntent>()
        setContent { TaskBTheme(false) { CalendarScreen(timedCalendarPreviewState(), intents::add, {}, {}, {}) } }
        val source = onNode(hasTestTag("calendar-task-302") and hasAnyAncestor(hasTestTag("calendar-day-2026-11-07")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val target = onNodeWithTag("calendar-day-2026-11-12", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput { moveTo(source); press(); moveTo(source + Offset(28f, 0f)); moveTo(target) }
        onNode(hasText("Business trip") and hasAnyAncestor(hasTestTag("calendar-drag-preview")), useUnmergedTree = true).assertIsDisplayed()
        onRoot().performMouseInput { release() }
        runOnIdle {
            assertEquals(listOf<CalendarIntent>(CalendarIntent.MoveTask(302, LocalDate(2026, 11, 11))), intents)
            assertTrue(intents.none { it is CalendarIntent.ToggleCompleted })
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
