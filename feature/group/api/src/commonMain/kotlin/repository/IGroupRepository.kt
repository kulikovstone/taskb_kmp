package com.mkulikov.taskb.feature.group.api.repository

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import kotlinx.coroutines.flow.Flow

interface IGroupRepository {
    val groups: Flow<List<Group>>

    suspend fun getById(id: Int): Group?

    suspend fun add(name: String, color: GroupColor): GroupWriteResult

    suspend fun update(group: Group): GroupWriteResult

    suspend fun delete(id: Int)
}
