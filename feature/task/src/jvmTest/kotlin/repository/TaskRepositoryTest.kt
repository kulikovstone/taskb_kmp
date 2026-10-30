package com.mkulikov.taskb.feature.task.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.task.model.Task
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class TaskRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val directory: Path get() = temporaryFolder.root.toPath()
    @Test
    fun `tasks persist with dates groups and completion and details save preserves newer completion`() = runBlocking {
        val file = directory.resolve("tasks.db")
        val date = LocalDate(2028, 2, 29)
        val database = AppDatabase.open(file)
        try {
            val group = database.groupDao().insert(GroupEntity(name = "Work", color = 0)).toInt()
            val repository = TaskRepository(lazy { database.taskDao() })
            assertTrue(repository.save(Task(name = " Task ", dueDate = date, groupId = group)))
            val task = repository.tasks.first().single()
            assertEquals("Task", task.name)
            assertEquals(date, task.dueDate)
            repository.setCompleted(task.id, true)
            assertTrue(repository.save(task.copy(name = "Renamed")))
            assertTrue(assertNotNull(repository.getById(task.id)).completed)
            assertFalse(repository.save(task.copy(id = 999)))
            assertFailsWith<IllegalArgumentException> { repository.save(Task(name = "  ")) }
        } finally { database.close() }
        val reopened = AppDatabase.open(file)
        try {
            val repository = TaskRepository(lazy { reopened.taskDao() })
            val task = repository.tasks.first().single()
            assertEquals("Renamed", task.name)
            assertEquals(date, task.dueDate)
            assertTrue(task.completed)
            reopened.groupDao().deleteById(requireNotNull(task.groupId))
            assertNull(assertNotNull(repository.getById(task.id)).groupId)
            repository.delete(task.id)
            assertTrue(repository.tasks.first().isEmpty())
        } finally { reopened.close() }
    }
}
