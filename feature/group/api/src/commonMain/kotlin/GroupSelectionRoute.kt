package com.mkulikov.taskb.feature.group.api

import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.core.navigation.NavResultKey

data class GroupSelectionRoute(
    val resultKey: NavResultKey<GroupSelectionResult>,
    val selectedGroupId: Int? = null,
    val showNoGroup: Boolean = false,
    val noGroupSelected: Boolean = false,
) : NavKey
