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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
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
        TaskEditorStore(repository, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest {
                setContent {
                    TaskBTheme {
                        val state = store.state.collectAsState().value
                        TaskEditorContent(state, store::accept, wide.value, {})
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
                scheduler.runCurrent()
                assertEquals("Updated task", repository.lastWrite?.name)
            }
        }
    }

    @Test
    fun `typing and selection stay current while store processing is delayed`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(repository, FakeGroupRepository(), dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest {
                setContent {
                    TaskBTheme {
                        TaskEditorContent(store.state.collectAsState().value, store::accept, false, {})
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
                store.accept(TaskEditorIntent.DateChanged(LocalDate(2026, 10, 27)))
                scheduler.runCurrent()
                waitForIdle()
                name.assertTextEquals("Схема связей модулей")
                name.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(12)))
                onNodeWithContentDescription("Save task").performClick()
                scheduler.runCurrent()
                assertEquals("Схема связей модулей", repository.lastWrite?.name)
            }
        }
    }

    @Test
    fun `clear then type keeps input focused and saves the latest text`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(repository, FakeGroupRepository(), dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest {
                setContent {
                    TaskBTheme {
                        TaskEditorContent(store.state.collectAsState().value, store::accept, false, {})
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
                scheduler.runCurrent()
                assertEquals("New name", repository.lastWrite?.name)
            }
        }
    }
}
