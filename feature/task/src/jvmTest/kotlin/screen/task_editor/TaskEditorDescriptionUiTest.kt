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
import com.mkulikov.taskb.feature.task.screen.item_editor.TaskItemEditorContent
import com.mkulikov.taskb.feature.task.testing.FakeGroupRepository
import com.mkulikov.taskb.feature.task.testing.FakeTaskRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TaskEditorDescriptionUiTest {
    @Test
    fun `creation saves multiline description verbatim and Enter adds a newline`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(
                repository = repository,
                groups = FakeGroupRepository(),
                dispatcher = StandardTestDispatcher(scheduler = scheduler),
            )
            .use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(width = 400f, height = 640f)) {
                    setContent {
                        TaskBTheme(darkTheme = false) {
                            Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                                TaskItemEditorContent(
                                    state = store.state.collectAsState().value,
                                    onIntent = store::accept,
                                    onBack = {},
                                    onChooseGroup = {},
                                )
                            }
                        }
                    }
                    onNodeWithContentDescription(label = "Task name").performTextInput(text = "Draft task")
                    val description = onNodeWithContentDescription(label = "Task description")
                    description.performClick().performTextInput(text = "  First line")
                    description.performKeyInput { pressKey(key = Key.Enter) }
                    description.performTextInput(text = "\nSecond line  \n")
                    val expected = "  First line\n\nSecond line  \n"
                    description.assertTextEquals(expected)
                    assertNull(actual = repository.lastWrite)

                    onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).performClick()
                    scheduler.runCurrent()
                    assertEquals(expected = "Draft task", actual = repository.lastWrite?.name)
                    assertEquals(expected = expected, actual = repository.lastWrite?.description)
                }
            }
    }

    @Test
    fun `description editing and selection survive delayed store updates and layout changes and can be cleared`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository().apply {
            values.value = listOf(
                element = Task(id = 7, name = "Original task", description = "Old description\nSecond line")
            )
        }
        val wide = mutableStateOf(value = false)
        TaskEditorStore(
                repository = repository,
                groups = FakeGroupRepository(),
                taskId = 7,
                dispatcher = StandardTestDispatcher(scheduler = scheduler),
            )
            .use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(width = 1280f, height = 900f)) {
                    setContent {
                        TaskBTheme(darkTheme = false) {
                            Box(
                                modifier = Modifier
                                    .width(width = if (wide.value) 1280.dp else 400.dp)
                                    .fillMaxHeight()
                            ) {
                                TaskItemEditorContent(
                                    state = store.state.collectAsState().value,
                                    onIntent = store::accept,
                                    onBack = {},
                                    onChooseGroup = {},
                                )
                            }
                        }
                    }
                    val description = onNodeWithContentDescription(label = "Task description")
                    description.assertTextEquals("Old description\nSecond line")
                    description.performClick()
                    description.performTextInputSelection(selection = TextRange(start = 0, end = 3))
                    description.performTextInput(text = "New")
                    store.accept(
                        intent = TaskEditorIntent.DateChanged(date = LocalDate(year = 2026, month = 11, day = 1))
                    )
                    for (isWide in listOf(true, false)) {
                        runOnIdle { wide.value = isWide }
                        waitForIdle()
                        description.assertTextEquals("New description\nSecond line")
                        description.assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.TextSelectionRange,
                                expectedValue = TextRange(index = 3),
                            )
                        )
                    }
                    scheduler.runCurrent()
                    waitForIdle()
                    description.assertTextEquals("New description\nSecond line")
                    description.assert(
                        matcher = SemanticsMatcher.expectValue(
                            key = SemanticsProperties.TextSelectionRange,
                            expectedValue = TextRange(index = 3),
                        )
                    )
                    description.performTextClearance()
                    onNode(matcher = hasText(text = "Create item") or hasText(text = "Save changes")).performClick()
                    scheduler.runCurrent()
                    assertEquals(expected = "", actual = repository.lastWrite?.description)
                    assertEquals(expected = "Original task", actual = repository.lastWrite?.name)
                }
            }
    }

    @Test
    fun `long description grows and the form scrolls to later fields while save stays fixed`() {
        val scheduler = TestCoroutineScheduler()
        val repository = FakeTaskRepository()
        TaskEditorStore(
                repository = repository,
                groups = FakeGroupRepository(),
                dispatcher = StandardTestDispatcher(scheduler = scheduler),
            )
            .use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(width = 400f, height = 640f)) {
                    setContent {
                        TaskBTheme(darkTheme = false) {
                            Surface(modifier = Modifier.fillMaxSize(), color = TaskBTheme.colors.surface) {
                                TaskItemEditorContent(
                                    state = store.state.collectAsState().value,
                                    onIntent = store::accept,
                                    onBack = {},
                                    onChooseGroup = {},
                                )
                            }
                        }
                    }
                    onNodeWithContentDescription(label = "Task name").performTextInput(text = "Long task")
                    val description = onNodeWithContentDescription(label = "Task description")
                    val initialHeight = description.fetchSemanticsNode().boundsInRoot.height
                    val save = onNodeWithText(text = "Create item")
                    val saveTop = save.fetchSemanticsNode().boundsInRoot.top
                    val text = (1..24).joinToString(separator = "\n") { lineNumber -> "Description line $lineNumber" }
                    description.performTextReplacement(text = text)
                    waitForIdle()
                    assertTrue(actual = description.fetchSemanticsNode().boundsInRoot.height > initialHeight)
                    onNodeWithContentDescription(label = "Choose date").performScrollTo().assertIsDisplayed()
                    onNodeWithContentDescription(label = "Task name").assertIsNotDisplayed()
                    save.assertIsDisplayed()
                    assertEquals(expected = saveTop, actual = save.fetchSemanticsNode().boundsInRoot.top)
                    save.performClick()
                    scheduler.runCurrent()
                    assertEquals(expected = text, actual = repository.lastWrite?.description)
                }
            }
    }
}
