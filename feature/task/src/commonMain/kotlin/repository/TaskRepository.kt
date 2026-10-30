package com.mkulikov.taskb.feature.task.repository

import com.mkulikov.taskb.feature.task.api.database.ITaskDao
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.feature.task.model.Task
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

internal class TaskRepository(
    private val dao: Lazy<ITaskDao>,
    private val clock: Clock = Clock.System,
) : ITaskRepository {
    override val tasks = flow { emitAll(dao.value.observeTasks().map { list -> list.map { it.toTask() } }) }

    override suspend fun getById(id: Int): Task? = dao.value.getById(id)?.toTask()

    override suspend fun save(task: Task): Boolean {
        val name = task.name.trim().also { require(it.isNotEmpty()) }
        return if (task.id == 0) {
            dao.value.insert(TaskEntity(
                name = name,
                dueDate = task.dueDate?.toEpochDays(),
                groupId = task.groupId,
                createdAt = clock.now().toEpochMilliseconds(),
                completedAt = task.completedAt?.toEpochMilliseconds(),
            )) > 0
        } else dao.value.updateDetails(task.id, name, task.dueDate?.toEpochDays(), task.groupId) > 0
    }

    override suspend fun setCompleted(id: Int, completed: Boolean) =
        dao.value.setCompletedAt(id, if (completed) clock.now().toEpochMilliseconds() else null)
    override suspend fun delete(id: Int) = dao.value.deleteById(id)

    private fun TaskEntity.toTask() = Task(
        id = id,
        name = name,
        dueDate = dueDate?.let(LocalDate::fromEpochDays),
        groupId = groupId,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        completedAt = completedAt?.let(Instant::fromEpochMilliseconds),
    )
}
