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
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskRecurrenceEditorUiTest {
    @Test
    fun `task date and recurrence end date share the distant year picker`() {
        val scheduler = TestCoroutineScheduler()
        val repo = FakeTaskRepository()
        TaskEditorStore(
                repository = repo,
                groups = FakeGroupRepository(),
                initialDate = LocalDate(year = 2026, month = 11, day = 3),
                dispatcher = StandardTestDispatcher(scheduler = scheduler),
            )
            .use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(width = 400f, height = 980f)) {
                    setContent {
                        TaskBTheme(darkTheme = false) {
                            val state = store.state.collectAsState().value
                            Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                                Box {
                                    TaskEditorContent(
                                        state = state,
                                        onIntent = store::accept,
                                        wide = false,
                                        onBack = {},
                                        onChooseGroup = {},
                                    )
                                    TaskEditorDialogs(
                                        state = state,
                                        today = state.today,
                                        onIntent = store::accept,
                                        availableHeight = 980.dp,
                                    )
                                }
                            }
                        }
                    }
                    onNodeWithContentDescription(label = "Task name")
                        .performTextReplacement(text = "Wedding anniversary")
                    onNodeWithContentDescription(label = "Choose date").performClick()
                    scheduler.runCurrent()
                    waitForIdle()
                    onNodeWithContentDescription(label = "Choose year").performClick()
                    onNodeWithContentDescription(label = "Choose year 2036").performClick()
                    onNodeWithContentDescription(label = "Choose month").performClick()
                    onNodeWithContentDescription(label = "Choose June 2036").performClick()
                    onNodeWithContentDescription(label = "Choose June 12, 2036").performClick()
                    scheduler.runCurrent()
                    waitForIdle()
                    assertEquals(
                        expected = LocalDate(year = 2036, month = 6, day = 12),
                        actual = store.state.value.dueDate,
                    )
                    onNodeWithContentDescription(label = "Choose repeat pattern").performScrollTo().performClick()
                    scheduler.runCurrent()
                    waitForIdle()
                    onNodeWithText(text = "On date").performScrollTo().performClick()
                    scheduler.runCurrent()
                    waitForIdle()
                    onNodeWithContentDescription(label = "Choose repeat end date").performScrollTo().performClick()
                    scheduler.runCurrent()
                    waitForIdle()
                    onNodeWithContentDescription(label = "Choose year").performClick()
                    onNodeWithContentDescription(label = "Next 20 years").performClick()
                    onNodeWithContentDescription(label = "Choose year 2046").performClick()
                    onNodeWithContentDescription(label = "Choose June 12, 2046").performClick()
                    scheduler.runCurrent()
                    waitForIdle()
                    assertEquals(
                        expected = LocalDate(year = 2036, month = 6, day = 12),
                        actual = store.state.value.dueDate,
                    )
                    assertEquals(
                        expected = LocalDate(year = 2046, month = 6, day = 12),
                        actual = store.state.value.recurrence?.endDate,
                    )
                    onNodeWithContentDescription(label = "Save task").performClick()
                    scheduler.runCurrent()
                    assertEquals(
                        expected = LocalDate(year = 2036, month = 6, day = 12),
                        actual = repo.lastWrite?.dueDate,
                    )
                    assertEquals(
                        expected = LocalDate(year = 2046, month = 6, day = 12),
                        actual = repo.lastWrite?.recurrence?.endDate,
                    )
                }
            }
    }

    @Test
    fun `monthly default persistent selector and conditional endings work at both layouts`() {
        for (wide in listOf(true, false)) {
            val scheduler = TestCoroutineScheduler()
            val repo = FakeTaskRepository()
            val groups = FakeGroupRepository().apply {
                values.value = listOf(element = Group(id = 7, name = "Health", color = GroupColor.Blue))
            }
            TaskEditorStore(
                    repository = repo,
                    groups = groups,
                    initialDate = LocalDate(year = 2026, month = 11, day = 3),
                    initialGroup = 7,
                    dispatcher = StandardTestDispatcher(scheduler = scheduler),
                )
                .use { store ->
                    scheduler.runCurrent()
                    runSkikoComposeUiTest(size = Size(width = if (wide) 1280f else 400f, height = 980f)) {
                        setContent {
                            TaskBTheme(darkTheme = false) {
                                Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                                    TaskEditorContent(
                                        state = store.state.collectAsState().value,
                                        onIntent = store::accept,
                                        wide = wide,
                                        onBack = {},
                                        onChooseGroup = {},
                                    )
                                }
                            }
                        }
                        onNodeWithContentDescription(label = "Task name").performTextReplacement(text = "Workout")
                        onNodeWithContentDescription(label = "Choose repeat pattern")
                            .performScrollTo()
                            .performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        assertEquals(
                            expected = RecurrenceFrequency.Monthly,
                            actual = store.state.value.recurrence?.frequency,
                        )
                        onNodeWithText(text = "Every month on day 3").assertIsDisplayed()
                        onNodeWithContentDescription(label = "Clear repeat pattern").assertIsDisplayed()
                        onNodeWithText(text = "Never").performScrollTo().assertIsDisplayed()
                        onNodeWithText(text = "On date").performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithContentDescription(label = "Choose repeat end date")
                            .performScrollTo()
                            .assertIsDisplayed()
                        onNodeWithText(text = "After").performScrollTo().performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithContentDescription(label = "Repeat occurrence count")
                            .performScrollTo()
                            .performTextReplacement(text = "6")
                        scheduler.runCurrent()
                        waitForIdle()
                        assertEquals(expected = 6, actual = store.state.value.editedRecurrence?.occurrenceCount)
                        onNodeWithContentDescription(label = "Choose repeat end date").assertDoesNotExist()
                        onNodeWithText(text = "Never").performScrollTo().performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithText(text = "Weekly").performScrollTo().performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithText(text = "Thu").performScrollTo().performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithText(text = "Every week on Tue and Thu").assertIsDisplayed()
                        onNodeWithText(text = "Upcoming: Nov 3, 5, 10").assertIsDisplayed()
                        val output = Path.of(
                            "build/reports/screenshots/recurrence-editor-${if (wide) "wide" else "compact"}.png"
                        )
                        Files.createDirectories(output.parent)
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output.toFile())
                        onNodeWithContentDescription(label = "Clear repeat pattern")
                            .performScrollTo()
                            .performClick()
                        scheduler.runCurrent()
                        waitForIdle()
                        onNodeWithText(text = "Does not repeat").assertIsDisplayed()
                        onNodeWithText(text = "Repeat ends").assertDoesNotExist()
                        onNodeWithContentDescription(label = "Save task").performClick()
                        scheduler.runCurrent()
                        assertNull(actual = repo.lastWrite?.recurrence)
                    }
                }
        }
    }
}
