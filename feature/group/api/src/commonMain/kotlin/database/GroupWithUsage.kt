package com.mkulikov.taskb.feature.group.api.database

import androidx.room3.Embedded

data class GroupWithUsage(
    @Embedded val summary: GroupWithTaskCounts,
    val recentTaskCount: Int,
)
