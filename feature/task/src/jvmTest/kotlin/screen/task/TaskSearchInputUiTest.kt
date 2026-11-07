package com.mkulikov.taskb.feature.task.screen.task

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextRange
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskSearchInputUiTest {
    @Test
    fun `search filters immediately and preserves selection through layouts and store updates`() {
        val scheduler = TestCoroutineScheduler()
        val wide = mutableStateOf(value = false)
        val repository = FakeTaskRepository().apply {
            values.value = listOf(Task(id = 1, name = "Дни рождения"), Task(id = 2, name = "Work"))
        }
        TaskStore(
                repository = repository,
                groups = FakeGroupRepository(),
                dispatcher = StandardTestDispatcher(scheduler = scheduler),
            )
            .use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(width = 1280f, height = 900f)) {
                    setContent {
                        TaskBTheme(darkTheme = false) {
                            TaskListScreen(
                                state = store.state.collectAsState().value,
                                onIntent = store::accept,
                                wide = wide.value,
                                onBack = {},
                                onChooseGroup = {},
                                onNewTask = {},
                                onOpenTask = {},
                            )
                        }
                    }
                    val search = onNodeWithContentDescription(label = "Search tasks")
                    for ((index, character) in "Дни".withIndex()) {
                        search.performTextInput(text = character.toString())
                        waitForIdle()
                        search.assertTextEquals("Дни".take(n = index + 1))
                        search.assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.TextSelectionRange,
                                expectedValue = TextRange(index = index + 1),
                            )
                        )
                    }
                    onNodeWithText(text = "Дни рождения").assertIsDisplayed()
                    onNodeWithText(text = "Work").assertDoesNotExist()
                    search.performTextInputSelection(selection = TextRange(index = 1))
                    runOnIdle { wide.value = true }
                    waitForIdle()
                    search.assertTextEquals("Дни")
                    search.assert(
                        matcher = SemanticsMatcher.expectValue(
                            key = SemanticsProperties.TextSelectionRange,
                            expectedValue = TextRange(index = 1),
                        )
                    )
                    search.performTextInputSelection(selection = TextRange(start = 1, end = 3))
                    store.accept(intent = TaskIntent.Sort(sort = TaskSort.Name))
                    scheduler.runCurrent()
                    waitForIdle()
                    search.assertTextEquals("Дни")
                    search.assert(
                        matcher = SemanticsMatcher.expectValue(
                            key = SemanticsProperties.TextSelectionRange,
                            expectedValue = TextRange(start = 1, end = 3),
                        )
                    )
                    search.performClick()
                    onNodeWithContentDescription(label = "Clear search tasks").performClick()
                    search.assertIsFocused()
                    onNodeWithText(text = "Work").assertIsDisplayed()
                }
            }
    }
}
