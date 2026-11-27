package com.mkulikov.taskb.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.api.repository.IEventRepository
import com.mkulikov.taskb.feature.item.api.repository.ITaskRepository
import com.mkulikov.taskb.feature.settings.api.IItemGroupSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.core.Koin

class LastSelectedGroupTest {
    @get:Rule val folder = TemporaryFolder()

    private fun openApp() = AppDependencyGraph {
        AppDatabase.open(file = folder.root.toPath().resolve("taskb.db"))
    }

    private suspend fun Koin.createGroup(): Int {
        val groups = get<IGroupRepository>()
        groups.add(name = "Work", color = GroupColor.Blue)
        return groups.groups.first().single().id
    }

    @Test
    fun `tasks and events share the group of the last successful creation across restarts`() {
        runBlocking {
            val work = openApp().use { graph ->
                val koin = graph.application.koin
                val group = koin.createGroup()
                val settings = koin.get<IItemGroupSettings>()
                assertTrue(actual = settings.rememberLastGroupEnabled())
                assertNull(actual = settings.lastSelectedGroupId())
                assertTrue(actual = koin.get<ITaskRepository>().save(task = Task(name = "Call", groupId = group)))
                group
            }
            openApp().use { graph ->
                val koin = graph.application.koin
                val settings = koin.get<IItemGroupSettings>()
                val tasks = koin.get<ITaskRepository>()
                val events = koin.get<IEventRepository>()
                assertEquals(expected = work, actual = settings.lastSelectedGroupId())
                val task = tasks.tasks.first().single()
                assertTrue(actual = tasks.save(task = task.copy(groupId = null)))
                assertEquals(expected = work, actual = settings.lastSelectedGroupId())
                assertFailsWith<IllegalArgumentException> { events.save(event = Event(name = "")) }
                assertEquals(expected = work, actual = settings.lastSelectedGroupId())
                assertTrue(actual = events.save(event = Event(name = "Lunch")))
                assertNull(actual = settings.lastSelectedGroupId())
                assertTrue(actual = events.save(event = Event(name = "Standup", groupId = work)))
                assertEquals(expected = work, actual = settings.lastSelectedGroupId())
                assertTrue(actual = tasks.save(task = Task(name = "Walk")))
                assertNull(actual = settings.lastSelectedGroupId())
            }
        }
    }

    @Test
    fun `disabling clears the group and suspends remembering until reenabled`() {
        runBlocking {
            val work = openApp().use { graph ->
                val koin = graph.application.koin
                val group = koin.createGroup()
                assertTrue(actual = koin.get<IEventRepository>().save(event = Event(name = "Review", groupId = group)))
                koin.get<IItemGroupSettings>().setRememberLastGroup(enabled = false)
                group
            }
            openApp().use { graph ->
                val koin = graph.application.koin
                val settings = koin.get<IItemGroupSettings>()
                val tasks = koin.get<ITaskRepository>()
                assertFalse(actual = settings.rememberLastGroupEnabled())
                assertNull(actual = settings.lastSelectedGroupId())
                assertTrue(actual = tasks.save(task = Task(name = "Plan", groupId = work)))
                settings.setRememberLastGroup(enabled = true)
                assertNull(actual = settings.lastSelectedGroupId())
                assertTrue(actual = tasks.save(task = Task(name = "Follow up", groupId = work)))
                assertEquals(expected = work, actual = settings.lastSelectedGroupId())
            }
        }
    }
}
