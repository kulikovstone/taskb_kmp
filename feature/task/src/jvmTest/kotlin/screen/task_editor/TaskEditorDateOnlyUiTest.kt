package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskEditorDateOnlyUiTest {
    @Test
    fun `create and edit keep dates and recurrence without timing controls in both layouts and themes`() {
        val today = LocalDate(2026, 11, 6)
        for (width in listOf(400, 1280)) for (dark in listOf(false, true)) for (editing in listOf(false, true)) {
            val scheduler = TestCoroutineScheduler()
            val repository = FakeTaskRepository().apply { if (editing) values.value = listOf(Task(7, "Original", today)) }
            TaskEditorStore(repository, FakeGroupRepository(), taskId = 7.takeIf { editing },
                dispatcher = StandardTestDispatcher(scheduler), today = { today }).use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(width.toFloat(), 900f)) {
                    setContent { TaskBTheme(dark) { Surface { TaskEditorContent(store.state.collectAsState().value,
                        store::accept, width >= 840, {}, {}) } } }
                    onNodeWithContentDescription("Task name").performTextReplacement("Date-only task")
                    onNodeWithText("Tomorrow").performScrollTo().performClick()
                    scheduler.runCurrent()
                    onNodeWithContentDescription("Choose repeat pattern").performScrollTo().assertIsDisplayed()
                    onNodeWithContentDescription("Choose start time").assertDoesNotExist()
                    onNodeWithContentDescription("Choose end time").assertDoesNotExist()
                    onNodeWithText("Start time").assertDoesNotExist()
                    onNodeWithText("Duration").assertDoesNotExist()
                    if (!editing) {
                        val output = Path.of("build/reports/screenshots/task-date-only-$width-${if (dark) "dark" else "light"}.png")
                        Files.createDirectories(output.parent)
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                    }
                    onNodeWithContentDescription("Save task").assertIsEnabled().performClick()
                    scheduler.runCurrent()
                    assertTrue(store.state.value.isFinished)
                    assertEquals("Date-only task", repository.lastWrite?.name)
                    assertEquals(today.plus(1, DateTimeUnit.DAY), repository.lastWrite?.dueDate)
                }
            }
        }
    }
}
