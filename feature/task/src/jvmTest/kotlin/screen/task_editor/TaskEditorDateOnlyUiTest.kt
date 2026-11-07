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
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskEditorDateOnlyUiTest {
    @Test
    fun `create and edit keep dates and recurrence without timing controls in both layouts and themes`() {
        val today = LocalDate(year = 2026, month = 11, day = 6)
        for (width in listOf(400, 1280)) for (dark in listOf(false, true)) for (editing in listOf(false, true)) {
            val scheduler = TestCoroutineScheduler()
            val repository = FakeTaskRepository().apply {
                if (editing) values.value = listOf(element = Task(id = 7, name = "Original", dueDate = today))
            }
            TaskEditorStore(
                    repository = repository,
                    groups = FakeGroupRepository(),
                    taskId = 7.takeIf { editing },
                    dispatcher = StandardTestDispatcher(scheduler = scheduler),
                    today = { today },
                )
                .use { store ->
                    scheduler.runCurrent()
                    runSkikoComposeUiTest(size = Size(width = width.toFloat(), height = 900f)) {
                        setContent {
                            TaskBTheme(darkTheme = dark) {
                                Surface {
                                    TaskEditorContent(
                                        state = store.state.collectAsState().value,
                                        onIntent = store::accept,
                                        wide = width >= 840,
                                        onBack = {},
                                        onChooseGroup = {},
                                    )
                                }
                            }
                        }
                        onNodeWithContentDescription(label = "Task name")
                            .performTextReplacement(text = "Date-only task")
                        onNodeWithText(text = "Tomorrow").performScrollTo().performClick()
                        scheduler.runCurrent()
                        onNodeWithContentDescription(label = "Choose repeat pattern")
                            .performScrollTo()
                            .assertIsDisplayed()
                        onNodeWithContentDescription(label = "Choose start time").assertDoesNotExist()
                        onNodeWithContentDescription(label = "Choose end time").assertDoesNotExist()
                        onNodeWithText(text = "Start time").assertDoesNotExist()
                        onNodeWithText(text = "Duration").assertDoesNotExist()
                        if (!editing) {
                            val output = Path.of(
                                ("build/reports/screenshots/task-date-only-$width-" +
                                    "${if (dark) "dark" else "light"}.png")
                            )
                            Files.createDirectories(output.parent)
                            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                        }
                        onNodeWithContentDescription(label = "Save task").assertIsEnabled().performClick()
                        scheduler.runCurrent()
                        assertTrue(actual = store.state.value.isFinished)
                        assertEquals(expected = "Date-only task", actual = repository.lastWrite?.name)
                        assertEquals(
                            expected = today.plus(value = 1, unit = DateTimeUnit.DAY),
                            actual = repository.lastWrite?.dueDate,
                        )
                    }
                }
        }
    }
}
