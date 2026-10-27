package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.feature.task.model.Task
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskEditorInputUiTest {
    @Test
    fun `loaded task keeps its draft and selection across layouts and saves with IME`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository().apply { values.value = listOf(Task(7, "Original task")) }
        val wide = mutableStateOf(false)
        TaskEditorStore(repository, 7, dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.advanceUntilIdle()
            runSkikoComposeUiTest {
                setContent {
                    TaskBTheme {
                        val state = store.state.collectAsState().value
                        if (wide.value) {
                            TaskEditorWidePanel(state, emptyList(), LocalDate(2026, 10, 27), store::accept, {}, {}, Modifier.fillMaxSize())
                        } else {
                            TaskEditorCompactScreen(state, emptyList(), LocalDate(2026, 10, 27), store::accept, {}, {})
                        }
                    }
                }
                val name = onNodeWithContentDescription("Task name")
                name.assertTextEquals("Original task")
                name.performTextInputSelection(TextRange(0, 8))
                name.performTextInput("Updated")
                for (isWide in listOf(true, false)) {
                    runOnIdle { wide.value = isWide }
                    waitForIdle()
                    name.assertTextEquals("Updated task")
                    name.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(7)))
                }
                name.performImeAction()
                scheduler.advanceUntilIdle()
                assertEquals("Updated task", repository.lastWrite?.name)
            }
        }
    }

    @Test
    fun `typing and selection stay current while store processing is delayed`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(repository, dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            runSkikoComposeUiTest {
                setContent {
                    TaskBTheme {
                        TaskEditorCompactScreen(store.state.collectAsState().value, emptyList(), LocalDate(2026, 10, 27), store::accept, {}, {})
                    }
                }
                val name = onNodeWithContentDescription("Task name")
                val text = "Схема зависимости модулей"
                for ((index, character) in text.withIndex()) {
                    name.performTextInput(character.toString())
                    waitForIdle()
                    name.assertTextEquals(text.take(index + 1))
                    name.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(index + 1)))
                }
                name.performTextInputSelection(TextRange(6, 17))
                name.performTextInput("связей")
                waitForIdle()
                name.assertTextEquals("Схема связей модулей")
                name.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(12)))

                // An unrelated store update must not restore an earlier input or selection.
                store.accept(TaskEditorIntent.GroupChanged(7))
                scheduler.runCurrent()
                waitForIdle()
                name.assertTextEquals("Схема связей модулей")
                name.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(12)))
                onNodeWithContentDescription("Save task").performClick()
                scheduler.advanceUntilIdle()
                assertEquals("Схема связей модулей", repository.lastWrite?.name)
            }
        }
    }

    @Test
    fun `clear then type keeps input focused and saves the latest text`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(repository, dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            runSkikoComposeUiTest {
                setContent {
                    TaskBTheme {
                        TaskEditorCompactScreen(store.state.collectAsState().value, emptyList(), LocalDate(2026, 10, 27), store::accept, {}, {})
                    }
                }
                val name = onNodeWithContentDescription("Task name")
                name.performTextInput("Draft")
                scheduler.runCurrent()
                waitForIdle()
                onNodeWithContentDescription("Clear task name").performClick()
                name.assertIsFocused().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                name.performTextInput("New name")
                waitForIdle()
                name.assertTextEquals("New name")
                onNodeWithContentDescription("Save task").performClick()
                scheduler.advanceUntilIdle()
                assertEquals("New name", repository.lastWrite?.name)
            }
        }
    }
}
