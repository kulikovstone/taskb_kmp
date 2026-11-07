package com.mkulikov.taskb.feature.group.repository

import androidx.compose.ui.graphics.toArgb
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.group.api.database.GroupWithTaskCounts
import com.mkulikov.taskb.feature.group.api.database.IGroupDao
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.model.GroupTaskSummary
import com.mkulikov.taskb.feature.group.api.repository.GroupWriteResult
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

internal class GroupRepository(private val dao: Lazy<IGroupDao>) : IGroupRepository {
    override val groups = flow {
        emitAll(
            flow = dao.value.observeGroupsWithTaskCounts().map { entities ->
                entities.map { record -> record.toGroup() }
            },
        )
    }

    override suspend fun getById(id: Int): Group? = dao.value.getWithTaskCounts(id = id)?.toGroup()

    override suspend fun add(name: String, color: GroupColor): GroupWriteResult {
        val normalized = name.trim().also { trimmedName -> require(value = trimmedName.isNotEmpty()) }
        return if (dao.value.insert(group = GroupEntity(name = normalized, color = color.color.toArgb())) == -1L) {
            GroupWriteResult.NameConflict
        } else {
            GroupWriteResult.Saved
        }
    }

    override suspend fun update(group: Group): GroupWriteResult {
        val normalized = group.name.trim().also { trimmedName -> require(value = trimmedName.isNotEmpty()) }
        val updated = dao.value.update(
            group = GroupEntity(id = group.id, name = normalized, color = group.color.color.toArgb())
        )
        return when {
            updated > 0 -> GroupWriteResult.Saved
            dao.value.getById(id = group.id) == null -> GroupWriteResult.NotFound
            else -> GroupWriteResult.NameConflict
        }
    }

    override suspend fun delete(id: Int) = dao.value.deleteById(id = id)

    private fun GroupWithTaskCounts.toGroup(): Group {
        return Group(
            id = group.id,
            name = group.name,
            color = GroupColor.fromArgb(argb = group.color),
            tasks = GroupTaskSummary(planned = planned, completed = completed),
        )
    }
}
