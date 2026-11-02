package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskTimeEditorUiTest {
    @Test fun `date and time reveal dependent fields and clearing time hides duration`() {
        val scheduler = TestCoroutineScheduler()
        TaskEditorStore(FakeTaskRepository(), FakeGroupRepository(), dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest(size = Size(1280f, 980f)) {
                setContent { TaskBTheme(false) { TaskEditorContent(store.state.collectAsState().value, store::accept, true, {}, {}) } }
                onNodeWithContentDescription("Choose start time").assertDoesNotExist()
                store.accept(TaskEditorIntent.DateChanged(LocalDate(2026, 11, 6)))
                scheduler.runCurrent(); waitForIdle()
                onNodeWithContentDescription("Choose start time").assertIsDisplayed()
                onNodeWithText("Duration").assertDoesNotExist()
                store.accept(TaskEditorIntent.StartTimeChanged(LocalTime(12, 0)))
                scheduler.runCurrent(); waitForIdle()
                onNodeWithText("Duration").assertIsDisplayed()
                onNodeWithText("1 hour").assertIsSelected()
                onNodeWithText("Ends at 13:00 · same day").assertIsDisplayed()
                onNodeWithContentDescription("Clear duration").assertDoesNotExist()
                onNodeWithText("Optional").assertDoesNotExist()
                onNodeWithText("15 min").performClick()
                scheduler.runCurrent(); waitForIdle()
                onNodeWithText("Ends at 12:15 · same day").assertIsDisplayed()
                onNodeWithText("15 min").assertIsSelected()
                onNodeWithContentDescription("Clear start time").performClick()
                scheduler.runCurrent(); waitForIdle()
                onNodeWithText("Duration").assertDoesNotExist()
                onNodeWithContentDescription("Choose start time").assertIsDisplayed()
            }
        }
    }

    @Test fun `standard 24 hour picker and custom duration save at desktop and compact widths`() {
        for (wide in listOf(true, false)) {
            val scheduler = TestCoroutineScheduler()
            val repo = FakeTaskRepository()
            TaskEditorStore(repo, FakeGroupRepository(), initialDate = LocalDate(2026, 11, 6), dispatcher = StandardTestDispatcher(scheduler)).use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(if (wide) 1280f else 400f, 980f)) {
                    setContent {
                        TaskBTheme(false) {
                            val state = store.state.collectAsState().value
                            Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                                Box {
                                    TaskEditorContent(state, store::accept, wide, {}, {})
                                    TaskEditorDialogs(state, state.today, store::accept, 980.dp)
                                }
                            }
                        }
                    }
                    onNodeWithContentDescription("Task name").performTextReplacement("Business trip")
                    onNodeWithContentDescription("Choose start time").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Switch to keyboard input").performClick()
                    onNodeWithContentDescription("Apply time").performClick()
                    scheduler.runCurrent(); waitForIdle()
                    assertEquals(LocalTime(9, 0), store.state.value.startTime)
                    assertEquals(60, store.state.value.editedDurationMinutes)
                    onNodeWithText("1 hour").performScrollTo().assertIsSelected()
                    onNodeWithText("15 min").assertIsDisplayed()
                    onNodeWithText("30 min").assertIsDisplayed()
                    onNodeWithText("Custom").assertIsDisplayed()
                    onNodeWithText("Custom").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Duration days").performScrollTo().performTextReplacement("2")
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Duration hours").performScrollTo().performTextReplacement("16")
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithText("2 days 16 hr").performScrollTo().assertIsDisplayed()
                    val output = Path.of("build/reports/screenshots/task-time-editor-${if (wide) "wide" else "compact"}.png")
                    Files.createDirectories(output.parent)
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                    onNodeWithContentDescription("Save task").performClick()
                    scheduler.runCurrent()
                    assertEquals(LocalTime(9, 0), repo.lastWrite?.startTime)
                    assertEquals(3840, repo.lastWrite?.durationMinutes)
                }
            }
        }
    }
}
