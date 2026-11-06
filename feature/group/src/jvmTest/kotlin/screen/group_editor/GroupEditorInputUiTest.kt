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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.testing.FakeGroupRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class GroupEditorInputUiTest {
    @Test
    fun `group name and cursor stay current while store processing is delayed`() {
        for (wide in listOf(false, true)) {
            val scheduler = TestCoroutineScheduler()
            val repository = FakeGroupRepository()
            GroupEditorStore(repository, dispatcher = StandardTestDispatcher(scheduler)).use { store ->
                scheduler.runCurrent()
                runSkikoComposeUiTest(size = Size(if (wide) 1280f else 440f, 900f)) {
                    setContent {
                        TaskBTheme(false) {
                            val state = store.state.collectAsState().value
                            if (wide) GroupEditorWideScreen(state, store::accept, {})
                            else GroupEditorCompactScreen(state, store::accept, {})
                        }
                    }
                    val name = onNodeWithContentDescription("Group name")
                    val text = "Дни рождения"
                    for ((index, character) in text.withIndex()) {
                        name.performTextInput(character.toString())
                        waitForIdle()
                        name.assertTextEquals(text.take(index + 1))
                        name.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(index + 1)))
                    }
                    name.performTextInputSelection(TextRange(4, 12))
                    name.performTextInput("отдыха")
                    waitForIdle()
                    name.assertTextEquals("Дни отдыха")
                    name.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(10)))

                    store.accept(GroupEditorIntent.ColorChanged(GroupColor.Green))
                    scheduler.runCurrent()
                    waitForIdle()
                    name.assertTextEquals("Дни отдыха")
                    name.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(10)))
                    name.performImeAction()
                    scheduler.runCurrent()
                    assertEquals("Дни отдыха", repository.lastWrite?.name)
                    assertEquals(GroupColor.Green, repository.lastWrite?.color)
                }
            }
        }
    }

    @Test
    fun `loaded group keeps selection across layouts and clear keeps focus`() {
        val scheduler = TestCoroutineScheduler()
        val gate = CompletableDeferred<Unit>()
        val repository = FakeGroupRepository().apply { onWrite = { gate.await() } }
        val wide = mutableStateOf(false)
        GroupEditorStore(repository, 7, StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest(size = Size(1280f, 900f)) {
                setContent {
                    TaskBTheme(false) {
                        val state = store.state.collectAsState().value
                        if (wide.value) GroupEditorWideScreen(state, store::accept, {})
                        else GroupEditorCompactScreen(state, store::accept, {})
                    }
                }
                val name = onNodeWithContentDescription("Group name")
                name.assertTextEquals("Work")
                name.performTextInputSelection(TextRange(0, 4))
                name.performTextInput("Дни рождения")
                for (isWide in listOf(true, false)) {
                    runOnIdle { wide.value = isWide }
                    waitForIdle()
                    name.assertTextEquals("Дни рождения")
                    name.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(12)))
                }
                name.performClick()
                onNodeWithContentDescription("Clear group name").performClick()
                name.assertIsFocused()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                name.performTextInput("Новая группа")
                onNodeWithContentDescription("Save group").performClick()
                scheduler.runCurrent()
                waitForIdle()
                name.assertIsNotEnabled()
                gate.complete(Unit)
                scheduler.runCurrent()
                assertEquals("Новая группа", repository.lastWrite?.name)
            }
        }
    }
}
