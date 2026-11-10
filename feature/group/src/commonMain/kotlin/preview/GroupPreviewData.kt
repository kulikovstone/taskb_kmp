package com.mkulikov.taskb.feature.group.preview

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.model.GroupTaskSummary

internal val previewGroups = listOf(
    Group(
        id = 1,
        name = "Work",
        color = GroupColor.Blue,
        tasks = GroupTaskSummary(planned = 8, completed = 12),
        eventCount = 3,
    ),
    Group(
        id = 2,
        name = "Personal",
        color = GroupColor.Green,
        tasks = GroupTaskSummary(planned = 4, completed = 2),
        eventCount = 5,
    ),
    Group(id = 3, name = "Reading", color = GroupColor.Purple, tasks = GroupTaskSummary(planned = 1)),
    Group(id = 4, name = "Travel", color = GroupColor.Orange, eventCount = 1),
)
