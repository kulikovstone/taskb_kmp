package com.mkulikov.taskb.feature.group.api.database

import androidx.room3.Embedded

data class GroupWithTaskCounts(
    @Embedded val group: GroupEntity,
    val planned: Int,
    val completed: Int,
    val eventCount: Int,
)
