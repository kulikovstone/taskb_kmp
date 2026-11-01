package com.mkulikov.taskb.feature.task.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.task.api.model.Task
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.time.Clock
import kotlin.time.Instant

class TaskRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val directory: Path get() = temporaryFolder.root.toPath()

    @Test
    fun `rescheduling changes only the date and stale undo never overwrites another date`() = runBlocking {
        val file = directory.resolve("reschedule.db")
        val database = AppDatabase.open(file)
        val date = LocalDate(2026, 10, 30)
        val newerDate = LocalDate(2026, 11, 1)
        try {
            val repository = TaskRepository(lazy { database })
            val group = database.groupDao().insert(GroupEntity(name = "Work", color = 0)).toInt()
            repository.save(Task(name = "Task", groupId = group))
            val task = repository.tasks.first().single()
            repository.setCompleted(task.id, true)
            val completed = assertNotNull(repository.getById(task.id))
            assertTrue(repository.reschedule(task.id, null, date))
            assertEquals(completed.copy(dueDate = date), repository.getById(task.id))
            assertTrue(repository.save(task.copy(name = "Renamed", dueDate = date)))
            assertTrue(repository.reschedule(task.id, date, null))
            val undone = assertNotNull(repository.getById(task.id))
            assertEquals("Renamed", undone.name)
            assertEquals(completed.completedAt, undone.completedAt)
            assertEquals(completed.createdAt, undone.createdAt)
            assertNull(undone.dueDate)
            assertTrue(repository.reschedule(task.id, null, newerDate))
            assertFalse(repository.reschedule(task.id, date, null))
            assertEquals(newerDate, repository.getById(task.id)?.dueDate)
            assertFalse(repository.reschedule(999, null, date))
        } finally { database.close() }
        val reopened = AppDatabase.open(file)
        try {
            assertEquals(newerDate.toEpochDays(), reopened.taskDao().observeTasks().first().single().dueDate)
        } finally { reopened.close() }
    }
    @Test
    fun `tasks persist with dates groups and completion and details save preserves newer completion`() = runBlocking {
        val file = directory.resolve("tasks.db")
        val date = LocalDate(2028, 2, 29)
        val createdAt = Instant.parse("2026-10-30T10:00:00.123Z")
        val completedAt = Instant.parse("2026-10-30T11:00:00.456Z")
        var now = createdAt
        val clock = object : Clock { override fun now(): Instant = now }
        val database = AppDatabase.open(file)
        try {
            val group = database.groupDao().insert(GroupEntity(name = "Work", color = 0)).toInt()
            val repository = TaskRepository(lazy { database }, clock)
            assertTrue(repository.save(Task(name = " Task ", dueDate = date, groupId = group,
                createdAt = Instant.parse("2000-01-01T00:00:00Z"))))
            val task = repository.tasks.first().single()
            assertEquals("Task", task.name)
            assertEquals(date, task.dueDate)
            assertEquals(createdAt, task.createdAt)
            assertNull(task.completedAt)
            assertFalse(task.isCompleted)
            assertEquals(createdAt.toEpochMilliseconds(), database.taskDao().getById(task.id)?.createdAt)
            now = completedAt
            repository.setCompleted(task.id, true)
            now = Instant.parse("2026-10-30T12:00:00Z")
            assertTrue(repository.save(task.copy(name = "Renamed", createdAt = now)))
            val edited = assertNotNull(repository.getById(task.id))
            assertEquals(createdAt, edited.createdAt)
            assertEquals(completedAt, edited.completedAt)
            assertTrue(edited.isCompleted)
            assertFalse(repository.save(task.copy(id = 999)))
            assertFailsWith<IllegalArgumentException> { repository.save(Task(name = "  ")) }
        } finally { database.close() }
        val reopened = AppDatabase.open(file)
        try {
            val repository = TaskRepository(lazy { reopened })
            val task = repository.tasks.first().single()
            assertEquals("Renamed", task.name)
            assertEquals(date, task.dueDate)
            assertEquals(createdAt, task.createdAt)
            assertEquals(completedAt, task.completedAt)
            assertTrue(task.isCompleted)
            reopened.groupDao().deleteById(requireNotNull(task.groupId))
            assertNull(assertNotNull(repository.getById(task.id)).groupId)
            repository.delete(task.id)
            assertTrue(repository.tasks.first().isEmpty())
        } finally { reopened.close() }
    }

    @Test
    fun `completion records its time once until reopened and preserves creation time`() = runBlocking {
        val database = AppDatabase.open(directory.resolve("completion.db"))
        var now = Instant.parse("2026-10-30T10:00:00Z")
        val clock = object : Clock { override fun now(): Instant = now }
        try {
            val repository = TaskRepository(lazy { database }, clock)
            repository.save(Task(name = "Task"))
            val task = repository.tasks.first().single()
            val createdAt = task.createdAt
            now = Instant.parse("2026-10-30T11:00:00Z")
            repository.setCompleted(task.id, true)
            val firstCompletion = now
            assertEquals(firstCompletion, repository.getById(task.id)?.completedAt)
            assertEquals(firstCompletion.toEpochMilliseconds(), database.taskDao().getById(task.id)?.completedAt)

            now = Instant.parse("2026-10-30T12:00:00Z")
            repository.setCompleted(task.id, true)
            assertEquals(firstCompletion, repository.tasks.first().single().completedAt)
            repository.setCompleted(task.id, false)
            val reopened = assertNotNull(repository.getById(task.id))
            assertNull(reopened.completedAt)
            assertFalse(reopened.isCompleted)

            repository.setCompleted(task.id, true)
            assertEquals(now, repository.getById(task.id)?.completedAt)
            repository.setCompleted(task.id, false)
            assertTrue(repository.save(task.copy(name = "Edited after reopening", completedAt = firstCompletion)))
            val edited = repository.tasks.first().single()
            assertEquals(createdAt, edited.createdAt)
            assertNull(edited.completedAt)
        } finally { database.close() }
    }
}
