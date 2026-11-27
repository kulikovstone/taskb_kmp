package com.mkulikov.taskb.feature.group.repository

import androidx.compose.ui.graphics.toArgb
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.model.GroupTaskSummary
import com.mkulikov.taskb.feature.group.api.repository.GroupWriteResult
import com.mkulikov.taskb.feature.item.api.database.EventEntity
import com.mkulikov.taskb.feature.item.api.database.TaskEntity
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class GroupRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val directory: Path
        get() = temporaryFolder.root.toPath()

    @Test
    fun `flow publishes creation editing and deletion`() {
        runBlocking {
            val database = AppDatabase.open(file = directory.resolve("groups.db"))
            try {
                val repository = GroupRepository(dao = lazy { database.groupDao() })
                val updates = Channel<List<Group>>(capacity = Channel.UNLIMITED)
                val observer = launch { repository.groups.collect(collector = updates::send) }
                try {
                    assertEquals(expected = emptyList(), actual = withTimeout(timeMillis = 5_000) { updates.receive() })
                    assertEquals(
                        expected = GroupWriteResult.Saved,
                        actual = repository.add(name = " Work ", color = GroupColor.Blue),
                    )
                    val group = withTimeout(timeMillis = 5_000) { updates.receive() }.single()
                    assertEquals(expected = "Work", actual = group.name)
                    assertEquals(expected = GroupColor.Blue, actual = group.color)
                    assertEquals(
                        expected = GroupWriteResult.Saved,
                        actual = repository.update(group = group.copy(name = " Home ", color = GroupColor.Red)),
                    )
                    val edited = withTimeout(timeMillis = 5_000) { updates.receive() }.single()
                    assertEquals(expected = group.id, actual = edited.id)
                    assertEquals(expected = "Home", actual = edited.name)
                    assertEquals(expected = GroupColor.Red, actual = edited.color)
                    repository.delete(id = group.id)
                    assertEquals(expected = emptyList(), actual = withTimeout(timeMillis = 5_000) { updates.receive() })
                    assertNull(actual = repository.getById(id = group.id))
                } finally {
                    observer.cancelAndJoin()
                }
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun `task and event counts update independently and deleting a group preserves its tasks`() {
        runBlocking {
            val database = AppDatabase.open(file = directory.resolve("group-tasks.db"))
            try {
                val repository = GroupRepository(dao = lazy { database.groupDao() })
                repository.add(name = "Work", color = GroupColor.Blue)
                val updates = Channel<List<Group>>(capacity = Channel.UNLIMITED)
                val observer = launch { repository.groups.collect(collector = updates::send) }
                try {
                    val group = withTimeout(timeMillis = 5_000) { updates.receive() }.single()
                    assertEquals(expected = GroupTaskSummary(), actual = group.tasks)
                    val tasks = database.taskDao()
                    val taskId = tasks.insert(task = TaskEntity(name = "Task", groupId = group.id)).toInt()
                    assertEquals(
                        expected = GroupTaskSummary(planned = 1, completed = 0),
                        actual = withTimeout(timeMillis = 5_000) { updates.receive() }.single().tasks,
                    )
                    tasks.setCompletedAt(id = taskId, completedAt = 1_798_000_000_000L)
                    assertEquals(
                        expected = GroupTaskSummary(planned = 0, completed = 1),
                        actual = withTimeout(timeMillis = 5_000) { updates.receive() }.single().tasks,
                    )
                    assertEquals(
                        expected = GroupTaskSummary(planned = 0, completed = 1),
                        actual = repository.getById(id = group.id)?.tasks,
                    )
                    database.eventDao().insert(event = EventEntity(name = "Meeting", groupId = group.id))
                    val withEvent = withTimeout(timeMillis = 5_000) { updates.receive() }.single()
                    assertEquals(expected = 1, actual = withEvent.eventCount)
                    assertEquals(expected = GroupTaskSummary(planned = 0, completed = 1), actual = withEvent.tasks)

                    repository.delete(id = group.id)
                    assertEquals(expected = emptyList(), actual = withTimeout(timeMillis = 5_000) { updates.receive() })
                    val task = assertNotNull(actual = tasks.getById(id = taskId))
                    assertNull(actual = task.groupId)
                    assertEquals(expected = "Task", actual = task.name)
                    assertEquals(expected = 1_798_000_000_000L, actual = task.completedAt)
                } finally {
                    observer.cancelAndJoin()
                }
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun `usage ranks recent tasks and shows unfinished tasks of any age`() {
        runBlocking {
            val database = AppDatabase.open(file = directory.resolve("group-usage.db"))
            try {
                val timeZone = TimeZone.currentSystemDefault()
                var now = LocalDateTime.parse(input = "2026-03-31T12:00:00").toInstant(timeZone = timeZone)
                val clock = object : Clock {
                    override fun now() = now
                }
                val repository = GroupRepository(dao = lazy { database.groupDao() }, clock = clock)
                val cutoff = LocalDateTime.parse(input = "2026-02-28T12:00:00")
                    .toInstant(timeZone = timeZone)
                    .toEpochMilliseconds()
                val completion = now.toEpochMilliseconds()
                for (name in listOf("Gamma", "old", "Zulu", "beta", "Empty")) {
                    repository.add(name = name, color = GroupColor.Blue)
                }
                val groups = repository.groups.first().associateBy(keySelector = Group::name)
                val tasks = database.taskDao()

                suspend fun addTask(groupName: String, createdAt: Long, completedAt: Long? = null) {
                    tasks.insert(
                        task = TaskEntity(
                            name = "Task",
                            groupId = groups.getValue(key = groupName).id,
                            createdAt = createdAt,
                            completedAt = completedAt,
                        ),
                    )
                }

                addTask(groupName = "Zulu", createdAt = cutoff, completedAt = completion)
                addTask(groupName = "Zulu", createdAt = cutoff + 1, completedAt = completion)
                addTask(groupName = "beta", createdAt = cutoff + 1)
                addTask(groupName = "Gamma", createdAt = cutoff + 1, completedAt = completion)
                addTask(groupName = "old", createdAt = cutoff - 1, completedAt = completion)
                repeat(times = 2) { addTask(groupName = "old", createdAt = cutoff - 1) }

                val usage = repository.observeGroupsByUsage()
                val ranked = usage.first()
                assertEquals(
                    expected = listOf("Zulu", "beta", "Gamma", "Empty", "old"),
                    actual = ranked.map(transform = Group::name),
                )
                assertEquals(expected = listOf(0, 1, 0, 0, 2), actual = ranked.map { group -> group.tasks.planned })

                now = LocalDateTime.parse(input = "2026-04-01T12:00:00").toInstant(timeZone = timeZone)
                assertEquals(
                    expected = listOf("beta", "Empty", "Gamma", "old", "Zulu"),
                    actual = usage.first().map(transform = Group::name),
                )
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun `names and colors survive reopening`() {
        runBlocking {
            val file = directory.resolve("groups.db")
            val database = AppDatabase.open(file = file)
            try {
                val repository = GroupRepository(dao = lazy { database.groupDao() })
                repository.add(name = "Personal", color = GroupColor.LightGreen)
                repository.add(name = "Work", color = GroupColor.Blue)
            } finally {
                database.close()
            }
            val reopened = AppDatabase.open(file = file)
            try {
                val groups = GroupRepository(dao = lazy { reopened.groupDao() }).groups.first()
                assertEquals(expected = listOf("Personal", "Work"), actual = groups.map(transform = Group::name))
                assertEquals(
                    expected = listOf(GroupColor.LightGreen, GroupColor.Blue),
                    actual = groups.map(transform = Group::color),
                )
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun `legacy transparent groups use an opaque color and save it on editing`() {
        runBlocking {
            val database = AppDatabase.open(file = directory.resolve("groups.db"))
            try {
                val dao = database.groupDao()
                val id = dao.insert(group = GroupEntity(name = "Legacy", color = 0)).toInt()
                val repository = GroupRepository(dao = lazy { dao })
                val group = assertNotNull(actual = repository.getById(id = id))
                assertEquals(expected = GroupColor.Grey, actual = group.color)
                assertEquals(expected = group, actual = repository.groups.first().single())
                assertEquals(
                    expected = GroupWriteResult.Saved,
                    actual = repository.update(group = group.copy(name = "Renamed")),
                )
                assertEquals(
                    expected = GroupColor.Grey.color.toArgb(),
                    actual = assertNotNull(actual = dao.getById(id = id)).color,
                )
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun `case insensitive uniqueness protects inserts and updates`() {
        runBlocking {
            val database = AppDatabase.open(file = directory.resolve("groups.db"))
            try {
                val repository = GroupRepository(dao = lazy { database.groupDao() })
                assertEquals(
                    expected = GroupWriteResult.Saved,
                    actual = repository.add(name = "Work", color = GroupColor.Blue),
                )
                assertEquals(
                    expected = GroupWriteResult.NameConflict,
                    actual = repository.add(name = " work ", color = GroupColor.Red),
                )
                repository.add(name = "Home", color = GroupColor.Green)
                val groups = repository.groups.first()
                val home = groups.last()
                assertEquals(
                    expected = GroupWriteResult.NameConflict,
                    actual = repository.update(group = home.copy(name = "WORK", color = GroupColor.Red)),
                )
                assertEquals(expected = home, actual = repository.getById(id = home.id))
                val work = groups.first()
                assertEquals(
                    expected = GroupWriteResult.Saved,
                    actual = repository.update(group = work.copy(name = "work")),
                )
                assertEquals(expected = "work", actual = assertNotNull(actual = repository.getById(id = work.id)).name)
                assertEquals(
                    expected = GroupWriteResult.NotFound,
                    actual = repository.update(group = Group(id = 999, name = "Missing", color = GroupColor.Blue)),
                )
                assertFailsWith<IllegalArgumentException> { repository.add(name = "  ", color = GroupColor.Red) }
                assertEquals(expected = 2, actual = repository.groups.first().size)
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun `concurrent inserts cannot create duplicate groups`() {
        runBlocking {
            val database = AppDatabase.open(file = directory.resolve("groups.db"))
            try {
                val repository = GroupRepository(dao = lazy { database.groupDao() })
                val results = coroutineScope {
                    List(size = 12) { async { repository.add(name = "Work", color = GroupColor.Blue) } }.awaitAll()
                }
                assertEquals(expected = 1, actual = results.count { result -> result == GroupWriteResult.Saved })
                assertEquals(
                    expected = 11,
                    actual = results.count { result -> result == GroupWriteResult.NameConflict },
                )
                assertEquals(expected = 1, actual = repository.groups.first().size)
            } finally {
                database.close()
            }
        }
    }
}
