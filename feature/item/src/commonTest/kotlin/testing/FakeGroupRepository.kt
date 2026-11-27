package com.mkulikov.taskb.feature.item.testing

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.repository.GroupWriteResult
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal class FakeGroupRepository : IGroupRepository {
    val values = MutableStateFlow(value = emptyList<Group>())
    var group: Group? = Group(id = 7, name = "Work", color = GroupColor.Blue)
    var failRead = false
    var result = GroupWriteResult.Saved
    var writes = 0
    var deletions = 0
    var lastWrite: Group? = null
    var subscriptions = 0
    var onWrite: suspend () -> Unit = {}
    override val groups = flow {
        if (failRead) error(message = "Storage unavailable")
        subscriptions++
        try {
            emitAll(flow = values)
        } finally {
            subscriptions--
        }
    }

    override suspend fun getById(id: Int): Group? {
        if (failRead) error(message = "Storage unavailable")
        return group
    }

    override suspend fun add(name: String, color: GroupColor): GroupWriteResult {
        return update(
            group = Group(id = 0, name = name, color = color),
        )
    }

    override suspend fun update(group: Group): GroupWriteResult {
        writes++
        onWrite()
        lastWrite = group
        return result
    }

    override suspend fun delete(id: Int) {
        deletions++
    }
}
