package com.mkulikov.taskb.feature.group.screen.group_selection

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.testing.FakeGroupRepository

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class GroupSelectionInputUiTest {
    @Test
    fun `search filters immediately and preserves selection through layouts and store updates`() {
        val scheduler = TestCoroutineScheduler()
        val wide = mutableStateOf(false)
        val repository = FakeGroupRepository().apply { values.value = listOf(Group(1, "Дни рождения", GroupColor.Green), Group(2, "Work", GroupColor.Blue)) }
        GroupSelectionStore(repository, dispatcher = StandardTestDispatcher(scheduler)).use { store ->
            scheduler.runCurrent()
            runSkikoComposeUiTest(size = Size(1280f, 900f)) {
                setContent {
                    TaskBTheme(false) {
                        Box(Modifier.width(if (wide.value) 1280.dp else 440.dp)) {
                            GroupSelectionContent(store.state.collectAsState().value, store::accept, {}, {})
                        }
                    }
                }
                val search = onNodeWithContentDescription("Search groups")
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
                repository.values.value = repository.values.value + Group(3, "Other", GroupColor.Teal)
                scheduler.runCurrent()
                waitForIdle()
                search.assertTextEquals("Дни")
                search.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(1, 3)))
                search.performClick()
                onNodeWithContentDescription("Clear search groups").performClick()
                search.assertIsFocused()
                onNodeWithText("Work").assertIsDisplayed()
            }
        }
    }
}
