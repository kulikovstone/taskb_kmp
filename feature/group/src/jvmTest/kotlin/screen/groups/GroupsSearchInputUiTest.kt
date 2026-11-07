package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextRange
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.testing.FakeGroupRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class GroupsSearchInputUiTest {
    @Test
    fun `search filters immediately and preserves selection through layouts and store updates`() {
        val scheduler = TestCoroutineScheduler()
        val wide = mutableStateOf(value = false)
        val repository = FakeGroupRepository().apply {
            values.value = listOf(
                Group(id = 1, name = "Дни рождения", color = GroupColor.Green),
                Group(id = 2, name = "Work", color = GroupColor.Blue),
            )
        }
        GroupsStore(repository = repository, dispatcher = StandardTestDispatcher(scheduler = scheduler)).use { store
            ->
            scheduler.runCurrent()
            runSkikoComposeUiTest(size = Size(width = 1280f, height = 900f)) {
                setContent {
                    TaskBTheme(darkTheme = false) {
                        val state = store.state.collectAsState().value
                        if (wide.value) {
                            GroupsWideScreen(
                                state = state,
                                onIntent = store::accept,
                                onBack = {},
                                onCreateGroup = {},
                                onEditGroup = {},
                            )
                        } else {
                            GroupsCompactScreen(
                                state = state,
                                onIntent = store::accept,
                                onBack = {},
                                onCreateGroup = {},
                                onEditGroup = {},
                            )
                        }
                    }
                }
                val search = onNodeWithContentDescription(label = "Search groups")
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
                store.accept(intent = GroupsIntent.Sort(sort = GroupSort.Tasks))
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
                onNodeWithContentDescription(label = "Clear search groups").performClick()
                search.assertIsFocused()
                onNodeWithText(text = "Work").assertIsDisplayed()
            }
        }
    }
}
