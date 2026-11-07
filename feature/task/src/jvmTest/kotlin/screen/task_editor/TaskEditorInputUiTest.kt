package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskEditorInputUiTest {
    @Test
    fun `loaded task keeps its draft and selection across layouts and saves with IME`() {
        val scheduler = TestCoroutineScheduler()
        val repository =
            FakeTaskRepository().apply { values.value = listOf(element = Task(id = 7, name = "Original task")) }
        val wide = mutableStateOf(value = false)
        TaskEditorStore(
                repository = repository,
                groups = FakeGroupRepository(),
                taskId = 7,
                dispatcher = StandardTestDispatcher(scheduler = scheduler),
            )
            .use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest {
                    setContent {
                        TaskBTheme {
                            val state = store.state.collectAsState().value
                            TaskEditorContent(
                                state = state,
                                onIntent = store::accept,
                                wide = wide.value,
                                onBack = {},
                                onChooseGroup = {},
                            )
                        }
                    }
                    val name = onNodeWithContentDescription(label = "Task name")
                    name.assertTextEquals("Original task")
                    name.performTextInputSelection(selection = TextRange(start = 0, end = 8))
                    name.performTextInput(text = "Updated")
                    for (isWide in listOf(true, false)) {
                        runOnIdle { wide.value = isWide }
                        waitForIdle()
                        name.assertTextEquals("Updated task")
                        name.assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.TextSelectionRange,
                                expectedValue = TextRange(index = 7),
                            )
                        )
                    }
                    name.performImeAction()
                    scheduler.runCurrent()
                    assertEquals(expected = "Updated task", actual = repository.lastWrite?.name)
                }
            }
    }

    @Test
    fun `typing and selection stay current while store processing is delayed`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(
                repository = repository,
                groups = FakeGroupRepository(),
                dispatcher = StandardTestDispatcher(scheduler = scheduler),
            )
            .use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest {
                    setContent {
                        TaskBTheme {
                            TaskEditorContent(
                                state = store.state.collectAsState().value,
                                onIntent = store::accept,
                                wide = false,
                                onBack = {},
                                onChooseGroup = {},
                            )
                        }
                    }
                    val name = onNodeWithContentDescription(label = "Task name")
                    val text = "Схема зависимости модулей"
                    for ((index, character) in text.withIndex()) {
                        name.performTextInput(text = character.toString())
                        waitForIdle()
                        name.assertTextEquals(text.take(n = index + 1))
                        name.assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.TextSelectionRange,
                                expectedValue = TextRange(index = index + 1),
                            )
                        )
                    }
                    name.performTextInputSelection(selection = TextRange(start = 6, end = 17))
                    name.performTextInput(text = "связей")
                    waitForIdle()
                    name.assertTextEquals("Схема связей модулей")
                    name.assert(
                        matcher = SemanticsMatcher.expectValue(
                            key = SemanticsProperties.TextSelectionRange,
                            expectedValue = TextRange(index = 12),
                        )
                    )

                    // An unrelated store update must not restore an earlier input or selection.
                    store.accept(
                        intent = TaskEditorIntent.DateChanged(date = LocalDate(year = 2026, month = 10, day = 27))
                    )
                    scheduler.runCurrent()
                    waitForIdle()
                    name.assertTextEquals("Схема связей модулей")
                    name.assert(
                        matcher = SemanticsMatcher.expectValue(
                            key = SemanticsProperties.TextSelectionRange,
                            expectedValue = TextRange(index = 12),
                        )
                    )
                    onNodeWithContentDescription(label = "Save task").performClick()
                    scheduler.runCurrent()
                    assertEquals(expected = "Схема связей модулей", actual = repository.lastWrite?.name)
                }
            }
    }

    @Test
    fun `clear then type keeps input focused and saves the latest text`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(
                repository = repository,
                groups = FakeGroupRepository(),
                dispatcher = StandardTestDispatcher(scheduler = scheduler),
            )
            .use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest {
                    setContent {
                        TaskBTheme {
                            TaskEditorContent(
                                state = store.state.collectAsState().value,
                                onIntent = store::accept,
                                wide = false,
                                onBack = {},
                                onChooseGroup = {},
                            )
                        }
                    }
                    val name = onNodeWithContentDescription(label = "Task name")
                    name.performTextInput(text = "Draft")
                    scheduler.runCurrent()
                    waitForIdle()
                    onNodeWithContentDescription(label = "Clear task name").performClick()
                    name
                        .assertIsFocused()
                        .assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.EditableText,
                                expectedValue = AnnotatedString(text = ""),
                            )
                        )
                    name.performTextInput(text = "New name")
                    waitForIdle()
                    name.assertTextEquals("New name")
                    onNodeWithContentDescription(label = "Save task").performClick()
                    scheduler.runCurrent()
                    assertEquals(expected = "New name", actual = repository.lastWrite?.name)
                }
            }
    }
}
