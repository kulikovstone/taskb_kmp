package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskEditorDescriptionUiTest {
    @Test
    fun `creation saves multiline description verbatim and Enter adds a newline`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(repository, FakeGroupRepository(), dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest(size = Size(400f, 640f)) {
                setContent {
                    TaskBTheme(false) {
                        Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                            TaskEditorContent(store.state.collectAsState().value, store::accept, false, {}, {})
                        }
                    }
                }
                onNodeWithContentDescription("Task name").performTextInput("Draft task")
                val description = onNodeWithContentDescription("Task description")
                description.performClick().performTextInput("  First line")
                description.performKeyInput { pressKey(Key.Enter) }
                description.performTextInput("\nSecond line  \n")
                val expected = "  First line\n\nSecond line  \n"
                description.assertTextEquals(expected)
                assertNull(repository.lastWrite)

                onNodeWithContentDescription("Save task").performClick()
                scheduler.runCurrent()
                assertEquals("Draft task", repository.lastWrite?.name)
                assertEquals(expected, repository.lastWrite?.description)
            }
        }
    }

    @Test
    fun `description editing and selection survive delayed store updates and layout changes and can be cleared`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository().apply {
            values.value = listOf(Task(7, "Original task", description = "Old description\nSecond line"))
        }
        val wide = mutableStateOf(false)
        TaskEditorStore(repository, FakeGroupRepository(), 7, dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest(size = Size(1280f, 900f)) {
                setContent {
                    TaskBTheme(false) {
                        Box(Modifier.width(if (wide.value) 1280.dp else 400.dp).fillMaxHeight()) {
                            TaskEditorContent(store.state.collectAsState().value, store::accept, wide.value, {}, {})
                        }
                    }
                }
                val description = onNodeWithContentDescription("Task description")
                description.assertTextEquals("Old description\nSecond line")
                description.performClick()
                description.performTextInputSelection(TextRange(0, 3))
                description.performTextInput("New")
                store.accept(TaskEditorIntent.DateChanged(LocalDate(2026, 11, 1)))
                for (isWide in listOf(true, false)) {
                    runOnIdle { wide.value = isWide }
                    waitForIdle()
                    description.assertTextEquals("New description\nSecond line")
                    description.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(3)))
                }
                scheduler.runCurrent()
                waitForIdle()
                description.assertTextEquals("New description\nSecond line")
                description.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(3)))
                description.performTextClearance()
                onNodeWithContentDescription("Save task").performClick()
                scheduler.runCurrent()
                assertEquals("", repository.lastWrite?.description)
                assertEquals("Original task", repository.lastWrite?.name)
            }
        }
    }

    @Test
    fun `long description grows and the form scrolls to later fields while save stays fixed`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(repository, FakeGroupRepository(), dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest(size = Size(400f, 640f)) {
                setContent {
                    TaskBTheme(false) {
                        Surface(Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                            TaskEditorContent(store.state.collectAsState().value, store::accept, false, {}, {})
                        }
                    }
                }
                onNodeWithContentDescription("Task name").performTextInput("Long task")
                val description = onNodeWithContentDescription("Task description")
                val initialHeight = description.fetchSemanticsNode().boundsInRoot.height
                val saveTop = onNodeWithContentDescription("Save task").fetchSemanticsNode().boundsInRoot.top
                val text = (1..24).joinToString("\n") { "Description line $it" }
                description.performTextReplacement(text)
                waitForIdle()
                assertTrue(description.fetchSemanticsNode().boundsInRoot.height > initialHeight)
                onNodeWithContentDescription("Choose date").performScrollTo().assertIsDisplayed()
                onNodeWithContentDescription("Task name").assertIsNotDisplayed()
                val save = onNodeWithContentDescription("Save task").assertIsDisplayed()
                assertEquals(saveTop, save.fetchSemanticsNode().boundsInRoot.top)
                save.performClick()
                scheduler.runCurrent()
                assertEquals(text, repository.lastWrite?.description)
            }
        }
    }
}
