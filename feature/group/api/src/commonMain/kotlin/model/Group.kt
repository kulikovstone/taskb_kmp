package com.mkulikov.taskb.feature.group.api.model

data class Group(
    val id: Int,
    val name: String,
    val color: GroupColor,
    val tasks: GroupTaskSummary = GroupTaskSummary(),
)
