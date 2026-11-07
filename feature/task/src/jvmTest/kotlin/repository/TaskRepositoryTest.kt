package com.mkulikov.taskb.feature.task.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.task.api.model.Task
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class TaskRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val directory: Path
        get() = temporaryFolder.root.toPath()

    @Test
    fun `multiline description persists exactly across edits reopening and clearing`() {
        runBlocking {
            val file = directory.resolve("description.db")
            val initial = "  Wedding anniversary\n\nBook a table for two.\nBring flowers 💐\n  "
            var taskId = 0
            AppDatabase.open(file = file).let { database ->
                try {
                    val repository = TaskRepository(database = lazy { database })
                    assertTrue(actual = repository.save(task = Task(name = "Anniversary", description = initial)))
                    val task = repository.tasks.first().single()
                    taskId = task.id
                    assertEquals(expected = initial, actual = task.description)
                    assertEquals(expected = initial, actual = database.taskDao().getById(id = taskId)?.description)
                    repository.setCompleted(id = taskId, completed = true)
                    assertTrue(
                        actual = repository.reschedule(
                            id = taskId,
                            expectedDate = null,
                            date = LocalDate(year = 2036, month = 6, day = 12),
                        )
                    )
                    assertEquals(expected = initial, actual = repository.getById(id = taskId)?.description)
                    assertTrue(
                        actual = repository.save(
                            task = assertNotNull(actual = repository.getById(id = taskId))
                                .copy(description = "$initial\nUpdated")
                        )
                    )
                } finally {
                    database.close()
                }
            }
            AppDatabase.open(file = file).let { database ->
                try {
                    val repository = TaskRepository(database = lazy { database })
                    val task = assertNotNull(actual = repository.getById(id = taskId))
                    assertEquals(expected = "$initial\nUpdated", actual = task.description)
                    assertTrue(actual = task.isCompleted)
                    assertTrue(actual = repository.save(task = task.copy(description = "")))
                    assertEquals(expected = "", actual = repository.tasks.first().single().description)
                } finally {
                    database.close()
                }
            }
            AppDatabase.open(file = file).let { database ->
                try {
                    assertEquals(expected = "", actual = database.taskDao().getById(id = taskId)?.description)
                } finally {
                    database.close()
                }
            }
        }
    }

    @Test
    fun `rescheduling changes only the date and stale undo never overwrites another date`() {
        runBlocking {
            val file = directory.resolve("reschedule.db")
            val database = AppDatabase.open(file = file)
            val date = LocalDate(year = 2026, month = 10, day = 30)
            val newerDate = LocalDate(year = 2026, month = 11, day = 1)
            try {
                val repository = TaskRepository(database = lazy { database })
                val group = database.groupDao().insert(group = GroupEntity(name = "Work", color = 0)).toInt()
                repository.save(task = Task(name = "Task", groupId = group))
                val task = repository.tasks.first().single()
                repository.setCompleted(id = task.id, completed = true)
                val completed = assertNotNull(actual = repository.getById(id = task.id))
                assertTrue(actual = repository.reschedule(id = task.id, expectedDate = null, date = date))
                assertEquals(expected = completed.copy(dueDate = date), actual = repository.getById(id = task.id))
                assertTrue(actual = repository.save(task = task.copy(name = "Renamed", dueDate = date)))
                assertTrue(actual = repository.reschedule(id = task.id, expectedDate = date, date = null))
                val undone = assertNotNull(actual = repository.getById(id = task.id))
                assertEquals(expected = "Renamed", actual = undone.name)
                assertEquals(expected = completed.completedAt, actual = undone.completedAt)
                assertEquals(expected = completed.createdAt, actual = undone.createdAt)
                assertNull(actual = undone.dueDate)
                assertTrue(actual = repository.reschedule(id = task.id, expectedDate = null, date = newerDate))
                assertFalse(actual = repository.reschedule(id = task.id, expectedDate = date, date = null))
                assertEquals(expected = newerDate, actual = repository.getById(id = task.id)?.dueDate)
                assertFalse(actual = repository.reschedule(id = 999, expectedDate = null, date = date))
            } finally {
                database.close()
            }
            val reopened = AppDatabase.open(file = file)
            try {
                assertEquals(
                    expected = newerDate.toEpochDays(),
                    actual = reopened.taskDao().observeTasks().first().single().dueDate,
                )
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun `tasks persist with dates groups and completion and details save preserves newer completion`() {
        runBlocking {
            val file = directory.resolve("tasks.db")
            val date = LocalDate(year = 2028, month = 2, day = 29)
            val createdAt = Instant.parse(input = "2026-10-30T10:00:00.123Z")
            val completedAt = Instant.parse(input = "2026-10-30T11:00:00.456Z")
            var now = createdAt
            val clock =
                object : Clock {
                    override fun now(): Instant = now
                }
            val database = AppDatabase.open(file = file)
            try {
                val group = database.groupDao().insert(group = GroupEntity(name = "Work", color = 0)).toInt()
                val repository = TaskRepository(database = lazy { database }, clock = clock)
                assertTrue(
                    actual = repository.save(
                        task = Task(
                            name = " Task ",
                            dueDate = date,
                            groupId = group,
                            createdAt = Instant.parse(input = "2000-01-01T00:00:00Z"),
                        )
                    )
                )
                val task = repository.tasks.first().single()
                assertEquals(expected = "Task", actual = task.name)
                assertEquals(expected = date, actual = task.dueDate)
                assertEquals(expected = createdAt, actual = task.createdAt)
                assertNull(actual = task.completedAt)
                assertFalse(actual = task.isCompleted)
                assertEquals(
                    expected = createdAt.toEpochMilliseconds(),
                    actual = database.taskDao().getById(id = task.id)?.createdAt,
                )
                now = completedAt
                repository.setCompleted(id = task.id, completed = true)
                now = Instant.parse(input = "2026-10-30T12:00:00Z")
                assertTrue(actual = repository.save(task = task.copy(name = "Renamed", createdAt = now)))
                val edited = assertNotNull(actual = repository.getById(id = task.id))
                assertEquals(expected = createdAt, actual = edited.createdAt)
                assertEquals(expected = completedAt, actual = edited.completedAt)
                assertTrue(actual = edited.isCompleted)
                assertFalse(actual = repository.save(task = task.copy(id = 999)))
                assertFailsWith<IllegalArgumentException> { repository.save(task = Task(name = "  ")) }
            } finally {
                database.close()
            }
            val reopened = AppDatabase.open(file = file)
            try {
                val repository = TaskRepository(database = lazy { reopened })
                val task = repository.tasks.first().single()
                assertEquals(expected = "Renamed", actual = task.name)
                assertEquals(expected = date, actual = task.dueDate)
                assertEquals(expected = createdAt, actual = task.createdAt)
                assertEquals(expected = completedAt, actual = task.completedAt)
                assertTrue(actual = task.isCompleted)
                reopened.groupDao().deleteById(id = requireNotNull(value = task.groupId))
                assertNull(actual = assertNotNull(actual = repository.getById(id = task.id)).groupId)
                repository.delete(id = task.id)
                assertTrue(actual = repository.tasks.first().isEmpty())
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun `completion records its time once until reopened and preserves creation time`() {
        runBlocking {
            val database = AppDatabase.open(file = directory.resolve("completion.db"))
            var now = Instant.parse(input = "2026-10-30T10:00:00Z")
            val clock =
                object : Clock {
                    override fun now(): Instant = now
                }
            try {
                val repository = TaskRepository(database = lazy { database }, clock = clock)
                repository.save(task = Task(name = "Task"))
                val task = repository.tasks.first().single()
                val createdAt = task.createdAt
                now = Instant.parse(input = "2026-10-30T11:00:00Z")
                repository.setCompleted(id = task.id, completed = true)
                val firstCompletion = now
                assertEquals(expected = firstCompletion, actual = repository.getById(id = task.id)?.completedAt)
                assertEquals(
                    expected = firstCompletion.toEpochMilliseconds(),
                    actual = database.taskDao().getById(id = task.id)?.completedAt,
                )

                now = Instant.parse(input = "2026-10-30T12:00:00Z")
                repository.setCompleted(id = task.id, completed = true)
                assertEquals(expected = firstCompletion, actual = repository.tasks.first().single().completedAt)
                repository.setCompleted(id = task.id, completed = false)
                val reopened = assertNotNull(actual = repository.getById(id = task.id))
                assertNull(actual = reopened.completedAt)
                assertFalse(actual = reopened.isCompleted)

                repository.setCompleted(id = task.id, completed = true)
                assertEquals(expected = now, actual = repository.getById(id = task.id)?.completedAt)
                repository.setCompleted(id = task.id, completed = false)
                assertTrue(
                    actual = repository.save(
                        task = task.copy(name = "Edited after reopening", completedAt = firstCompletion)
                    )
                )
                val edited = repository.tasks.first().single()
                assertEquals(expected = createdAt, actual = edited.createdAt)
                assertNull(actual = edited.completedAt)
            } finally {
                database.close()
            }
        }
    }
}
