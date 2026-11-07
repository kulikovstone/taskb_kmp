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
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalTestApi::class)
class TaskListUiTest {
    @Test
    fun `descriptions and wide repeat column stay aligned and checkbox hover belongs to the whole row`() {
        val today = LocalDate(year = 2026, month = 11, day = 6)
        val now = Instant.parse(input = "2026-11-06T12:00:00Z")
        val description = "Confirm release notes, remaining blockers and the deployment plan."
        val tasks = listOf(
            Task(
                id = 1,
                name = "Review launch checklist",
                dueDate = today,
                groupId = 1,
                createdAt = now,
                description = description,
                recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily),
            ),
            Task(id = 2, name = "Send the update", dueDate = today, groupId = 1, createdAt = now - 1.hours),
            Task(
                id = 3,
                name = "Team retrospective",
                dueDate = today,
                groupId = 1,
                createdAt = now - 2.hours,
                description = "Discuss what worked and what to improve.",
                recurrence = TaskRecurrence(
                    frequency = RecurrenceFrequency.Weekly,
                    weekdays = setOf(element = DayOfWeek.FRIDAY),
                ),
            ),
            Task(
                id = 4,
                name = "Pay utility bills",
                dueDate = today,
                groupId = 2,
                createdAt = now - 3.hours,
                recurrence = TaskRecurrence.monthly(date = today),
            ),
            Task(
                id = 5,
                name = "Renew insurance",
                dueDate = today,
                groupId = 2,
                createdAt = now - 4.hours,
                recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Yearly, dayOfMonth = 6, month = 11),
            ),
        )
        val state = TaskState(
            tasks = tasks,
            groups = listOf(
                Group(id = 1, name = "Work", color = GroupColor.Blue),
                Group(id = 2, name = "Personal", color = GroupColor.Green),
            ),
            today = today,
            now = now,
            isLoading = false,
        )
        for (width in listOf(1280, 1281, 840, 400, 300)) for (dark in listOf(false, true)) {
            runSkikoComposeUiTest(size = Size(width = width.toFloat(), height = 900f)) {
                val wide = width >= 840
                val intents = mutableListOf<TaskIntent>()
                val opened = mutableListOf<Int>()
                setContent {
                    TaskBTheme(darkTheme = dark) {
                        Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                            TaskListScreen(
                                state = state,
                                onIntent = intents::add,
                                wide = wide,
                                onBack = {},
                                onChooseGroup = {},
                                onNewTask = {},
                                onOpenTask = opened::add,
                            )
                        }
                    }
                }
                val titleBounds = onNodeWithText(text = tasks.first().name, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                val descriptionBounds = onNodeWithText(text = description, useUnmergedTree = true)
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot
                assertTrue(actual = descriptionBounds.top >= titleBounds.bottom)
                onNode(
                        matcher = hasTestTag(testTag = "task-summary") and
                            hasAnyAncestor(matcher = hasTestTag(testTag = "task-toolbar")),
                        useUnmergedTree = true,
                    )
                    .assertIsDisplayed()
                    .assertTextEquals("5 tasks")
                for (task in tasks) {
                    onNodeWithTag(testTag = "task-row-${task.key}", useUnmergedTree = true)
                        .assertWidthIsEqualTo(expectedWidth = width.dp)
                    if (wide) {
                        onNodeWithTag(testTag = "task-repeat-${task.key}", useUnmergedTree = true)
                            .assertIsDisplayed()
                            .assertTextEquals(task.recurrence?.frequency?.name.orEmpty())
                    } else {
                        onNodeWithTag(testTag = "task-repeat-${task.key}", useUnmergedTree = true)
                            .assertDoesNotExist()
                    }
                }
                if (wide) {
                    onNodeWithTag(testTag = "task-columns-divider").assertWidthIsEqualTo(expectedWidth = width.dp)
                    val nameBounds = onNodeWithContentDescription(
                        label = "Sort by Name",
                    ).fetchSemanticsNode().boundsInRoot
                    val groupBounds = onNodeWithContentDescription(
                        label = "Sort by Group",
                    ).fetchSemanticsNode().boundsInRoot
                    val dateBounds = onNodeWithContentDescription(
                        label = "Sort by Date",
                    ).fetchSemanticsNode().boundsInRoot
                    val repeatBounds = onNodeWithText(text = "Repeat", useUnmergedTree = true)
                        .assertIsDisplayed()
                        .fetchSemanticsNode()
                        .boundsInRoot
                    assertEquals(expected = 68f, actual = nameBounds.left, absoluteTolerance = 1f)
                    assertEquals(expected = width * 0.5f - 12f, actual = nameBounds.right, absoluteTolerance = 1f)
                    assertEquals(expected = width * 0.5f + 12f, actual = groupBounds.left, absoluteTolerance = 1f)
                    assertEquals(expected = width * 0.7f - 12f, actual = groupBounds.right, absoluteTolerance = 1f)
                    assertEquals(expected = width * 0.7f + 12f, actual = dateBounds.left, absoluteTolerance = 1f)
                    assertEquals(expected = width * 0.85f - 12f, actual = dateBounds.right, absoluteTolerance = 1f)
                    assertEquals(expected = width * 0.85f + 12f, actual = repeatBounds.left, absoluteTolerance = 1f)
                    assertEquals(expected = width - 12f, actual = repeatBounds.right, absoluteTolerance = 1f)
                    assertTrue(actual = repeatBounds.left > dateBounds.right)
                    assertEquals(
                        expected = repeatBounds.left,
                        actual = onNodeWithTag(testTag = "task-repeat-1", useUnmergedTree = true)
                            .fetchSemanticsNode()
                            .boundsInRoot
                            .left,
                    )
                } else {
                    onNodeWithText(text = "Repeat", useUnmergedTree = true).assertDoesNotExist()
                }

                val row = onNodeWithTag(testTag = "task-row-1", useUnmergedTree = true)
                val before = row.captureToImage().toPixelMap()
                val checkbox = onNodeWithContentDescription(
                    label = "Complete ${tasks.first().name}",
                    useUnmergedTree = true,
                )
                checkbox.performMouseInput { moveTo(position = center) }
                mainClock.advanceTimeBy(milliseconds = 200)
                val after = row.captureToImage().toPixelMap()
                val rowBounds = row.fetchSemanticsNode().boundsInRoot
                val checkboxBounds = checkbox.fetchSemanticsNode().boundsInRoot
                assertEquals(
                    expected = checkboxBounds.left - rowBounds.left,
                    actual = titleBounds.left - checkboxBounds.right,
                    absoluteTolerance = 0.5f,
                )
                val sampleY = (checkboxBounds.top - rowBounds.top).toInt() + 1
                val checkboxX = (checkboxBounds.left - rowBounds.left).toInt() + 1
                assertNotEquals(illegal = before[1, sampleY], actual = after[1, sampleY])
                assertEquals(expected = after[1, sampleY], actual = after[checkboxX, sampleY])
                val output = Path.of("build/reports/screenshots/task-list-$width-${if (dark) "dark" else "light"}.png")
                Files.createDirectories(output.parent)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())

                checkbox.performClick()
                runOnIdle {
                    assertEquals(
                        expected = listOf<TaskIntent>(element = TaskIntent.ToggleCompleted(id = 1)),
                        actual = intents,
                    )
                    assertTrue(actual = opened.isEmpty())
                }
                onNodeWithText(text = description, useUnmergedTree = true).performClick()
                runOnIdle { assertEquals(expected = listOf(element = 1), actual = opened) }
            }
        }
    }
}
