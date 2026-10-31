package com.mkulikov.taskb.feature.group.api

/** A null groupId means the explicit "No group" option, rather than cancellation. */
data class GroupSelectionResult(val groupId: Int?)
