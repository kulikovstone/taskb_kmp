package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class TaskListUiTest {
    @Test
    fun `descriptions and wide repeat column stay aligned and checkbox hover belongs to the whole row`() {
        val today = LocalDate(2026, 11, 6)
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val description = "Confirm release notes, remaining blockers and the deployment plan."
        val tasks = listOf(
            Task(1, "Review launch checklist", today, 1, createdAt = now, description = description,
                recurrence = TaskRecurrence(RecurrenceFrequency.Daily)),
            Task(2, "Send the update", today, 1, createdAt = now - 1.hours),
            Task(3, "Team retrospective", today, 1, createdAt = now - 2.hours, description = "Discuss what worked and what to improve.",
                recurrence = TaskRecurrence(RecurrenceFrequency.Weekly, weekdays = setOf(DayOfWeek.FRIDAY))),
            Task(4, "Pay utility bills", today, 2, createdAt = now - 3.hours, recurrence = TaskRecurrence.monthly(today)),
            Task(5, "Renew insurance", today, 2, createdAt = now - 4.hours,
                recurrence = TaskRecurrence(RecurrenceFrequency.Yearly, dayOfMonth = 6, month = 11)),
        )
        val state = TaskState(tasks = tasks, groups = listOf(Group(1, "Work", GroupColor.Blue), Group(2, "Personal", GroupColor.Green)),
            today = today, now = now, isLoading = false)
        for (width in listOf(1280, 1281, 840, 400, 300)) for (dark in listOf(false, true)) {
            runSkikoComposeUiTest(size = Size(width.toFloat(), 900f)) {
                val wide = width >= 840
                val intents = mutableListOf<TaskIntent>()
                val opened = mutableListOf<Int>()
                setContent {
                    TaskBTheme(dark) {
                        Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                            TaskListScreen(state, intents::add, wide, {}, {}, {}, opened::add)
                        }
                    }
                }
                val titleBounds = onNodeWithText(tasks.first().name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val descriptionBounds = onNodeWithText(description, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(descriptionBounds.top >= titleBounds.bottom)
                onNode(hasTestTag("task-summary") and hasAnyAncestor(hasTestTag("task-toolbar")), useUnmergedTree = true)
                    .assertIsDisplayed().assertTextEquals("5 tasks")
                for (task in tasks) {
                    onNodeWithTag("task-row-${task.key}", useUnmergedTree = true).assertWidthIsEqualTo(width.dp)
                    if (wide) onNodeWithTag("task-repeat-${task.key}", useUnmergedTree = true)
                        .assertIsDisplayed().assertTextEquals(task.recurrence?.frequency?.name.orEmpty())
                    else onNodeWithTag("task-repeat-${task.key}", useUnmergedTree = true).assertDoesNotExist()
                }
                if (wide) {
                    onNodeWithTag("task-columns-divider").assertWidthIsEqualTo(width.dp)
                    val nameBounds = onNodeWithContentDescription("Sort by Name").fetchSemanticsNode().boundsInRoot
                    val groupBounds = onNodeWithContentDescription("Sort by Group").fetchSemanticsNode().boundsInRoot
                    val dateBounds = onNodeWithContentDescription("Sort by Date").fetchSemanticsNode().boundsInRoot
                    val repeatBounds = onNodeWithText("Repeat", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertEquals(68f, nameBounds.left, 1f)
                    assertEquals(width * 0.5f - 12f, nameBounds.right, 1f)
                    assertEquals(width * 0.5f + 12f, groupBounds.left, 1f)
                    assertEquals(width * 0.7f - 12f, groupBounds.right, 1f)
                    assertEquals(width * 0.7f + 12f, dateBounds.left, 1f)
                    assertEquals(width * 0.85f - 12f, dateBounds.right, 1f)
                    assertEquals(width * 0.85f + 12f, repeatBounds.left, 1f)
                    assertEquals(width - 12f, repeatBounds.right, 1f)
                    assertTrue(repeatBounds.left > dateBounds.right)
                    assertEquals(repeatBounds.left, onNodeWithTag("task-repeat-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left)
                } else onNodeWithText("Repeat", useUnmergedTree = true).assertDoesNotExist()

                val row = onNodeWithTag("task-row-1", useUnmergedTree = true)
                val before = row.captureToImage().toPixelMap()
                val checkbox = onNodeWithContentDescription("Complete ${tasks.first().name}", useUnmergedTree = true)
                checkbox.performMouseInput { moveTo(center) }
                mainClock.advanceTimeBy(200)
                val after = row.captureToImage().toPixelMap()
                val rowBounds = row.fetchSemanticsNode().boundsInRoot
                val checkboxBounds = checkbox.fetchSemanticsNode().boundsInRoot
                assertEquals(checkboxBounds.left - rowBounds.left, titleBounds.left - checkboxBounds.right, 0.5f)
                val sampleY = (checkboxBounds.top - rowBounds.top).toInt() + 1
                val checkboxX = (checkboxBounds.left - rowBounds.left).toInt() + 1
                assertNotEquals(before[1, sampleY], after[1, sampleY])
                assertEquals(after[1, sampleY], after[checkboxX, sampleY])
                val output = Path.of("build/reports/screenshots/task-list-$width-${if (dark) "dark" else "light"}.png")
                Files.createDirectories(output.parent)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())

                checkbox.performClick()
                runOnIdle {
                    assertEquals(listOf<TaskIntent>(TaskIntent.ToggleCompleted(1)), intents)
                    assertTrue(opened.isEmpty())
                }
                onNodeWithText(description, useUnmergedTree = true).performClick()
                runOnIdle { assertEquals(listOf(1), opened) }
            }
        }
    }
}
