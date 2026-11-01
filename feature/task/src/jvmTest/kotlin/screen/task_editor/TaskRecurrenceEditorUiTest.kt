package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.runtime.collectAsState
import androidx.compose.material.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskRecurrenceEditorUiTest {
    @Test fun `task date and recurrence end date share the distant year picker`() {
        val scheduler = TestCoroutineScheduler()
        val repo = FakeTaskRepository()
        TaskEditorStore(repo, FakeGroupRepository(), initialDate = LocalDate(2026, 11, 3), dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest(size = Size(400f, 980f)) {
                setContent {
                    TaskBTheme(false) {
                        val state = store.state.collectAsState().value
                        Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                            Box {
                                TaskEditorContent(state, store::accept, false, {}, {})
                                TaskEditorDialogs(state, state.today, store::accept, 980.dp)
                            }
                        }
                    }
                }
                onNodeWithContentDescription("Task name").performTextReplacement("Wedding anniversary")
                onNodeWithContentDescription("Choose date").performClick()
                scheduler.runCurrent(); waitForIdle()
                onNodeWithContentDescription("Choose year").performClick()
                onNodeWithContentDescription("Choose year 2036").performClick()
                onNodeWithContentDescription("Choose month").performClick()
                onNodeWithContentDescription("Choose June 2036").performClick()
                onNodeWithContentDescription("Choose June 12, 2036").performClick()
                scheduler.runCurrent(); waitForIdle()
                assertEquals(LocalDate(2036, 6, 12), store.state.value.dueDate)
                onNodeWithContentDescription("Choose repeat pattern").performScrollTo().performClick()
                scheduler.runCurrent(); waitForIdle()
                onNodeWithText("On date").performScrollTo().performClick()
                scheduler.runCurrent(); waitForIdle()
                onNodeWithContentDescription("Choose repeat end date").performScrollTo().performClick()
                scheduler.runCurrent(); waitForIdle()
                onNodeWithContentDescription("Choose year").performClick()
                onNodeWithContentDescription("Next 20 years").performClick()
                onNodeWithContentDescription("Choose year 2046").performClick()
                onNodeWithContentDescription("Choose June 12, 2046").performClick()
                scheduler.runCurrent(); waitForIdle()
                assertEquals(LocalDate(2036, 6, 12), store.state.value.dueDate)
                assertEquals(LocalDate(2046, 6, 12), store.state.value.recurrence?.endDate)
                onNodeWithContentDescription("Save task").performClick()
                scheduler.runCurrent()
                assertEquals(LocalDate(2036, 6, 12), repo.lastWrite?.dueDate)
                assertEquals(LocalDate(2046, 6, 12), repo.lastWrite?.recurrence?.endDate)
            }
        }
    }

    @Test fun `monthly default persistent selector and conditional endings work at both layouts`() {
        for (wide in listOf(true, false)) {
            val scheduler = TestCoroutineScheduler()
            val repo = FakeTaskRepository()
            val groups = FakeGroupRepository().apply { values.value = listOf(Group(7, "Health", GroupColor.Blue)) }
            TaskEditorStore(repo, groups, initialDate = LocalDate(2026, 11, 3), initialGroup = 7, dispatcher = StandardTestDispatcher(scheduler)).use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(if (wide) 1280f else 400f, 980f)) {
                    setContent { TaskBTheme(false) { Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) { TaskEditorContent(store.state.collectAsState().value, store::accept, wide, {}, {}) } } }
                    onNodeWithContentDescription("Task name").performTextReplacement("Workout")
                    onNodeWithContentDescription("Choose repeat pattern").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    assertEquals(RecurrenceFrequency.Monthly, store.state.value.recurrence?.frequency)
                    onNodeWithText("Every month on day 3").assertIsDisplayed()
                    onNodeWithContentDescription("Clear repeat pattern").assertIsDisplayed()
                    onNodeWithText("Never").performScrollTo().assertIsDisplayed()
                    onNodeWithText("On date").performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Choose repeat end date").performScrollTo().assertIsDisplayed()
                    onNodeWithText("After").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithContentDescription("Repeat occurrence count").performScrollTo().performTextReplacement("6")
                    scheduler.runCurrent(); waitForIdle()
                    assertEquals(6, store.state.value.editedRecurrence?.occurrenceCount)
                    onNodeWithContentDescription("Choose repeat end date").assertDoesNotExist()
                    onNodeWithText("Never").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithText("Weekly").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithText("Thu").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithText("Every week on Tue and Thu").assertIsDisplayed()
                    onNodeWithText("Upcoming: Nov 3, 5, 10").assertIsDisplayed()
                    val output = Path.of("build/reports/screenshots/recurrence-editor-${if (wide) "wide" else "compact"}.png")
                    Files.createDirectories(output.parent)
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                    onNodeWithContentDescription("Clear repeat pattern").performScrollTo().performClick()
                    scheduler.runCurrent(); waitForIdle()
                    onNodeWithText("Does not repeat").assertIsDisplayed()
                    onNodeWithText("Repeat ends").assertDoesNotExist()
                    onNodeWithContentDescription("Save task").performClick()
                    scheduler.runCurrent()
                    assertNull(repo.lastWrite?.recurrence)
                }
            }
        }
    }
}
