package com.mkulikov.taskb.feature.group.screen.group_selection

import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.testing.FakeGroupRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSelectionStoreTest {
    private val sample = listOf(Group(7, "Work", GroupColor.Blue), Group(3, "Health", GroupColor.Teal))

    @Test
    fun `groups-only mode searches sorted groups and rejects no group`() = runTest {
        val repository = FakeGroupRepository().apply { values.value = sample }
        GroupSelectionStore(repository, selectedGroupId = 7, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            advanceUntilIdle()
            assertEquals(listOf(3, 7), store.state.value.matchingGroups.map { it.id })
            assertEquals(7, store.state.value.selectedGroupId)
            assertFalse(store.state.value.isNoGroupVisible)
            store.accept(GroupSelectionIntent.Select(null))
            store.accept(GroupSelectionIntent.Select(99))
            store.accept(GroupSelectionIntent.Search(" WOr "))
            advanceUntilIdle()
            assertNull(store.state.value.result)
            assertEquals(listOf(7), store.state.value.matchingGroups.map { it.id })
            store.accept(GroupSelectionIntent.Select(7))
            store.accept(GroupSelectionIntent.Select(3))
            advanceUntilIdle()
            assertEquals(GroupSelectionResult(7), store.state.value.result)
        }
        advanceUntilIdle()
        assertEquals(0, repository.subscriptions)
    }

    @Test
    fun `filter mode can explicitly select no group even with no groups`() = runTest {
        GroupSelectionStore(FakeGroupRepository(), showNoGroup = true, noGroupSelected = true,
            dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            advanceUntilIdle()
            assertTrue(store.state.value.isNoGroupVisible)
            assertTrue(store.state.value.noGroupSelected)
            store.accept(GroupSelectionIntent.Search("missing"))
            advanceUntilIdle()
            assertFalse(store.state.value.isNoGroupVisible)
            store.accept(GroupSelectionIntent.Search("  NO GR "))
            advanceUntilIdle()
            assertTrue(store.state.value.isNoGroupVisible)
            store.accept(GroupSelectionIntent.Select(null))
            advanceUntilIdle()
            assertEquals(GroupSelectionResult(null), store.state.value.result)
        }
    }

    @Test
    fun `load failure retries and repository updates preserve the query and prevent deleted selections`() = runTest {
        val repository = FakeGroupRepository().apply { failRead = true }
        GroupSelectionStore(repository, dispatcher = StandardTestDispatcher(testScheduler)).use { store ->
            advanceUntilIdle()
            assertNotNull(store.state.value.loadError)
            store.accept(GroupSelectionIntent.Select(7))
            repository.failRead = false
            repository.values.value = sample
            store.accept(GroupSelectionIntent.RetryLoad)
            store.accept(GroupSelectionIntent.Search("wor"))
            advanceUntilIdle()
            assertNull(store.state.value.loadError)
            assertEquals(listOf(7), store.state.value.matchingGroups.map { it.id })
            repository.values.value = emptyList()
            advanceUntilIdle()
            assertEquals("wor", store.state.value.query)
            store.accept(GroupSelectionIntent.Select(7))
            advanceUntilIdle()
            assertNull(store.state.value.result)
            assertTrue(store.state.value.matchingGroups.isEmpty())
        }
    }
}
