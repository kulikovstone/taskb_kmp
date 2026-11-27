package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.model.GroupTaskSummary
import com.mkulikov.taskb.feature.group.testing.FakeGroupRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsStoreTest {
    private val sample = listOf(
        Group(
            id = 1,
            name = "Work",
            color = GroupColor.Blue,
            tasks = GroupTaskSummary(planned = 15, completed = 9),
        ),
        Group(id = 2, name = "Ideas", color = GroupColor.Amber, tasks = GroupTaskSummary(completed = 4)),
        Group(id = 3, name = "Health", color = GroupColor.Teal, tasks = GroupTaskSummary(planned = 6)),
        Group(id = 4, name = "Reading", color = GroupColor.Indigo, eventCount = 2),
    )

    @Test
    fun `search filters and sort use planned totals and keep completed-only groups empty`(): TestResult {
        return runTest {
            val repository = FakeGroupRepository().apply { values.value = sample }
            GroupsStore(repository = repository, dispatcher = StandardTestDispatcher(scheduler = testScheduler)).use {
                store ->
                advanceUntilIdle()
                assertEquals(
                    expected = listOf("Health", "Ideas", "Reading", "Work"),
                    actual = store.state.value.visibleGroups.map(transform = Group::name),
                )
                assertEquals(expected = 2, actual = store.state.value.withTasksCount)
                assertEquals(expected = 2, actual = store.state.value.emptyCount)
                store.accept(intent = GroupsIntent.Filter(filter = GroupFilter.Empty))
                advanceUntilIdle()
                assertEquals(
                    expected = listOf("Ideas", "Reading"),
                    actual = store.state.value.visibleGroups.map(transform = Group::name),
                )
                store.state.value.query.setTextAndPlaceCursorAtEnd(text = "  IDEA ")
                advanceUntilIdle()
                assertEquals(
                    expected = listOf(element = "Ideas"),
                    actual = store.state.value.visibleGroups.map(transform = Group::name),
                )
                assertEquals(expected = "1 of 4 groups", actual = store.state.value.summary)
                store.state.value.query.setTextAndPlaceCursorAtEnd(text = "")
                store.accept(intent = GroupsIntent.Filter(filter = GroupFilter.All))
                store.accept(intent = GroupsIntent.Sort(sort = GroupSort.Tasks))
                advanceUntilIdle()
                assertEquals(
                    expected = listOf("Work", "Health", "Ideas", "Reading"),
                    actual = store.state.value.visibleGroups.map(transform = Group::name),
                )
                store.accept(intent = GroupsIntent.Sort(sort = GroupSort.Tasks))
                advanceUntilIdle()
                assertEquals(
                    expected = listOf("Reading", "Ideas", "Health", "Work"),
                    actual = store.state.value.visibleGroups.map(transform = Group::name),
                )
                store.accept(intent = GroupsIntent.Sort(sort = GroupSort.Events))
                advanceUntilIdle()
                assertEquals(
                    expected = listOf("Reading", "Health", "Ideas", "Work"),
                    actual = store.state.value.visibleGroups.map(transform = Group::name),
                )
            }
            advanceUntilIdle()
            assertEquals(expected = 0, actual = repository.subscriptions)
        }
    }

    @Test
    fun `failed observation retries and list filters survive live updates`(): TestResult {
        return runTest {
            val repository = FakeGroupRepository().apply { failRead = true }
            GroupsStore(repository = repository, dispatcher = StandardTestDispatcher(scheduler = testScheduler)).use {
                store ->
                advanceUntilIdle()
                assertNotNull(actual = store.state.value.loadError)
                repository.failRead = false
                repository.values.value = sample
                store.accept(intent = GroupsIntent.RetryLoad)
                advanceUntilIdle()
                assertNull(actual = store.state.value.loadError)
                store.state.value.query.setTextAndPlaceCursorAtEnd(text = "wor")
                advanceUntilIdle()
                repository.values.value = sample.dropLast(n = 1)
                advanceUntilIdle()
                assertEquals(expected = "wor", actual = store.state.value.query.text.toString())
                assertEquals(
                    expected = listOf(element = "Work"),
                    actual = store.state.value.visibleGroups.map(transform = Group::name),
                )
                assertEquals(expected = 3, actual = store.state.value.groups.size)
            }
        }
    }
}
