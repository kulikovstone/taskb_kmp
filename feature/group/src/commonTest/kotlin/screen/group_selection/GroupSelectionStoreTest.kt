package com.mkulikov.taskb.feature.group.screen.group_selection

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.testing.FakeGroupRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSelectionStoreTest {
    private val sample = listOf(
        Group(id = 7, name = "Work", color = GroupColor.Blue),
        Group(id = 3, name = "Health", color = GroupColor.Teal),
    )

    @Test
    fun `groups-only mode preserves usage order and rejects no group`(): TestResult {
        return runTest {
            val repository = FakeGroupRepository().apply { values.value = sample }
            GroupSelectionStore(
                    groupsByUsage = repository.groups,
                    selectedGroupId = 7,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    advanceUntilIdle()
                    assertEquals(
                        expected = listOf(7, 3),
                        actual = store.state.value.matchingGroups.map(transform = Group::id),
                    )
                    assertEquals(expected = 7, actual = store.state.value.selectedGroupId)
                    assertFalse(actual = store.state.value.isNoGroupVisible)
                    store.accept(intent = GroupSelectionIntent.Select(groupId = null))
                    store.accept(intent = GroupSelectionIntent.Select(groupId = 99))
                    store.state.value.query.setTextAndPlaceCursorAtEnd(text = " WOr ")
                    advanceUntilIdle()
                    assertNull(actual = store.state.value.result)
                    assertEquals(
                        expected = listOf(element = 7),
                        actual = store.state.value.matchingGroups.map(transform = Group::id),
                    )
                    store.accept(intent = GroupSelectionIntent.Select(groupId = 7))
                    store.accept(intent = GroupSelectionIntent.Select(groupId = 3))
                    advanceUntilIdle()
                    assertEquals(expected = GroupSelectionResult(groupId = 7), actual = store.state.value.result)
                }
            advanceUntilIdle()
            assertEquals(expected = 0, actual = repository.subscriptions)
        }
    }

    @Test
    fun `filter mode can explicitly select no group even with no groups`(): TestResult {
        return runTest {
            GroupSelectionStore(
                    groupsByUsage = FakeGroupRepository().groups,
                    showNoGroup = true,
                    noGroupSelected = true,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    advanceUntilIdle()
                    assertTrue(actual = store.state.value.isNoGroupVisible)
                    assertTrue(actual = store.state.value.noGroupSelected)
                    store.state.value.query.setTextAndPlaceCursorAtEnd(text = "missing")
                    advanceUntilIdle()
                    assertFalse(actual = store.state.value.isNoGroupVisible)
                    store.state.value.query.setTextAndPlaceCursorAtEnd(text = "  NO GR ")
                    advanceUntilIdle()
                    assertTrue(actual = store.state.value.isNoGroupVisible)
                    store.accept(intent = GroupSelectionIntent.Select(groupId = null))
                    advanceUntilIdle()
                    assertEquals(expected = GroupSelectionResult(groupId = null), actual = store.state.value.result)
                }
        }
    }

    @Test
    fun `load failure retries and repository updates preserve the query and prevent deleted selections`(): TestResult {
        return runTest {
            val repository = FakeGroupRepository().apply { failRead = true }
            GroupSelectionStore(
                    groupsByUsage = repository.groups,
                    dispatcher = StandardTestDispatcher(scheduler = testScheduler),
                )
                .use { store ->
                    advanceUntilIdle()
                    assertNotNull(actual = store.state.value.loadError)
                    store.accept(intent = GroupSelectionIntent.Select(groupId = 7))
                    repository.failRead = false
                    repository.values.value = sample
                    store.accept(intent = GroupSelectionIntent.RetryLoad)
                    store.state.value.query.setTextAndPlaceCursorAtEnd(text = "wor")
                    advanceUntilIdle()
                    assertNull(actual = store.state.value.loadError)
                    assertEquals(
                        expected = listOf(element = 7),
                        actual = store.state.value.matchingGroups.map(transform = Group::id),
                    )
                    repository.values.value = emptyList()
                    advanceUntilIdle()
                    assertEquals(expected = "wor", actual = store.state.value.query.text.toString())
                    store.accept(intent = GroupSelectionIntent.Select(groupId = 7))
                    advanceUntilIdle()
                    assertNull(actual = store.state.value.result)
                    assertTrue(actual = store.state.value.matchingGroups.isEmpty())
                }
        }
    }
}
