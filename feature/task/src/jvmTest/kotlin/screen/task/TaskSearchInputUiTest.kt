package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextRange
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskSearchInputUiTest {
    @Test
    fun `search filters immediately and preserves selection through layouts and store updates`() {
        val scheduler = TestCoroutineScheduler()
        val wide = mutableStateOf(false)
        val repository = FakeTaskRepository().apply { values.value = listOf(Task(1, "Дни рождения"), Task(2, "Work")) }
        TaskStore(repository, FakeGroupRepository(), StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest(size = Size(1280f, 900f)) {
                setContent {
                    TaskBTheme(false) {
                        TaskListScreen(store.state.collectAsState().value, store::accept, wide.value, {}, {}, {}, {})
                    }
                }
                val search = onNodeWithContentDescription("Search tasks")
                for ((index, character) in "Дни".withIndex()) {
                    search.performTextInput(character.toString())
                    waitForIdle()
                    search.assertTextEquals("Дни".take(index + 1))
                    search.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(index + 1)))
                }
                onNodeWithText("Дни рождения").assertIsDisplayed()
                onNodeWithText("Work").assertDoesNotExist()
                search.performTextInputSelection(TextRange(1))
                runOnIdle { wide.value = true }
                waitForIdle()
                search.assertTextEquals("Дни")
                search.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(1)))
                search.performTextInputSelection(TextRange(1, 3))
                store.accept(TaskIntent.Sort(TaskSort.Name))
                scheduler.runCurrent()
                waitForIdle()
                search.assertTextEquals("Дни")
                search.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(1, 3)))
                search.performClick()
                onNodeWithContentDescription("Clear search tasks").performClick()
                search.assertIsFocused()
                onNodeWithText("Work").assertIsDisplayed()
            }
        }
    }
}
