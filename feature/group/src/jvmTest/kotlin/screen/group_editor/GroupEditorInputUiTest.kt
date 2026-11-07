package com.mkulikov.taskb.feature.group.screen.group_editor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.testing.FakeGroupRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class GroupEditorInputUiTest {
    @Test
    fun `group name and cursor stay current while store processing is delayed`() {
        for (wide in listOf(false, true)) {
            val scheduler = TestCoroutineScheduler()
            val repository = FakeGroupRepository()
            GroupEditorStore(repository = repository, dispatcher = StandardTestDispatcher(scheduler = scheduler))
                .use { store ->
                    scheduler.runCurrent()
                    runSkikoComposeUiTest(size = Size(width = if (wide) 1280f else 440f, height = 900f)) {
                        setContent {
                            TaskBTheme(darkTheme = false) {
                                val state = store.state.collectAsState().value
                                if (wide) {
                                    GroupEditorWideScreen(state = state, onIntent = store::accept, onBack = {})
                                } else {
                                    GroupEditorCompactScreen(state = state, onIntent = store::accept, onBack = {})
                                }
                            }
                        }
                        val name = onNodeWithContentDescription(label = "Group name")
                        val text = "Дни рождения"
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
                        name.performTextInputSelection(selection = TextRange(start = 4, end = 12))
                        name.performTextInput(text = "отдыха")
                        waitForIdle()
                        name.assertTextEquals("Дни отдыха")
                        name.assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.TextSelectionRange,
                                expectedValue = TextRange(index = 10),
                            )
                        )

                        store.accept(intent = GroupEditorIntent.ColorChanged(color = GroupColor.Green))
                        scheduler.runCurrent()
                        waitForIdle()
                        name.assertTextEquals("Дни отдыха")
                        name.assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.TextSelectionRange,
                                expectedValue = TextRange(index = 10),
                            )
                        )
                        name.performImeAction()
                        scheduler.runCurrent()
                        assertEquals(expected = "Дни отдыха", actual = repository.lastWrite?.name)
                        assertEquals(expected = GroupColor.Green, actual = repository.lastWrite?.color)
                    }
                }
        }
    }

    @Test
    fun `loaded group keeps selection across layouts and clear keeps focus`() {
        val scheduler = TestCoroutineScheduler()
        val gate = CompletableDeferred<Unit>()
        val repository = FakeGroupRepository().apply { onWrite = { gate.await() } }
        val wide = mutableStateOf(value = false)
        GroupEditorStore(
                repository = repository,
                groupId = 7,
                dispatcher = StandardTestDispatcher(scheduler = scheduler),
            )
            .use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(width = 1280f, height = 900f)) {
                    setContent {
                        TaskBTheme(darkTheme = false) {
                            val state = store.state.collectAsState().value
                            if (wide.value) {
                                GroupEditorWideScreen(state = state, onIntent = store::accept, onBack = {})
                            } else {
                                GroupEditorCompactScreen(state = state, onIntent = store::accept, onBack = {})
                            }
                        }
                    }
                    val name = onNodeWithContentDescription(label = "Group name")
                    name.assertTextEquals("Work")
                    name.performTextInputSelection(selection = TextRange(start = 0, end = 4))
                    name.performTextInput(text = "Дни рождения")
                    for (isWide in listOf(true, false)) {
                        runOnIdle { wide.value = isWide }
                        waitForIdle()
                        name.assertTextEquals("Дни рождения")
                        name.assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.TextSelectionRange,
                                expectedValue = TextRange(index = 12),
                            )
                        )
                    }
                    name.performClick()
                    onNodeWithContentDescription(label = "Clear group name").performClick()
                    name
                        .assertIsFocused()
                        .assert(
                            matcher = SemanticsMatcher.expectValue(
                                key = SemanticsProperties.EditableText,
                                expectedValue = AnnotatedString(text = ""),
                            )
                        )
                    name.performTextInput(text = "Новая группа")
                    onNodeWithContentDescription(label = "Save group").performClick()
                    scheduler.runCurrent()
                    waitForIdle()
                    name.assertIsNotEnabled()
                    gate.complete(value = Unit)
                    scheduler.runCurrent()
                    assertEquals(expected = "Новая группа", actual = repository.lastWrite?.name)
                }
            }
    }
}
