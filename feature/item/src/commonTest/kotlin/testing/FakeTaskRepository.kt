package com.mkulikov.taskb.feature.item.testing

import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.api.repository.ITaskRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate

internal class FakeTaskRepository(private val clock: Clock = Clock.System) : ITaskRepository {
    val values = MutableStateFlow(value = emptyList<Task>())
    var fail = false
    var writes = 0
    var deletions = 0
    var subscriptions = 0
    var lastWrite: Task? = null
    var lastScope: ItemChangeScope? = null
    var onWrite: suspend () -> Unit = {}
    override val tasks = flow {
        if (fail) error(message = "Read failed")
        subscriptions++
        try {
            emitAll(flow = values)
        } finally {
            subscriptions--
        }
    }

    override suspend fun getById(id: Int): Task? {
        if (fail) error(message = "Read failed")
        return values.value.find { task -> task.id == id }
    }

    override suspend fun save(task: Task): Boolean {
        writes++
        onWrite()
        if (fail) error(message = "Write failed")
        lastWrite = task
        if (task.id != 0 && values.value.none { candidateTask -> candidateTask.id == task.id }) return false
        values.value = if (task.id == 0) {
            values.value + task.copy(id = (values.value.maxOfOrNull(selector = Task::id) ?: 0) + 1)
        } else {
            values.value.map { listedTask ->
                if (listedTask.id == task.id) {
                    listedTask.copy(
                        name = task.name,
                        description = task.description,
                        dueDate = task.dueDate,
                        groupId = task.groupId,
                        recurrence = task.recurrence,
                    )
                } else {
                    listedTask
                }
            }
        }
        return true
    }

    override suspend fun setCompleted(id: Int, completed: Boolean) {
        onWrite()
        if (fail) error(message = "Write failed")
        values.value = values.value.map { task ->
            if (task.id ==
                id) {
                task.copy(completedAt = if (completed) task.completedAt ?: clock.now() else null)
            } else {
                task
            }
        }
    }

    override suspend fun delete(id: Int) {
        deletions++
        onWrite()
        values.value = values.value.filter { task -> task.id != id }
    }

    override suspend fun saveOccurrence(task: Task, scope: ItemChangeScope): Boolean {
        lastScope = scope
        return save(task = task)
    }

    override suspend fun deleteOccurrence(id: Int, occurrence: ItemOccurrenceId?, scope: ItemChangeScope): Boolean {
        lastScope = scope
        delete(id = id)
        return true
    }

    override suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean {
        onWrite()
        if (fail) error(message = "Write failed")
        if (values.value.none { task -> task.id == id && task.dueDate == expectedDate }) return false
        values.value = values.value.map { task -> if (task.id == id) task.copy(dueDate = date) else task }
        return true
    }
}
