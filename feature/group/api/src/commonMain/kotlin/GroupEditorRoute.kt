package com.mkulikov.taskb.feature.group.api

import androidx.navigation3.runtime.NavKey

/** Opens a group editor independently of the groups list. */
sealed interface GroupEditorRoute : NavKey {
    data object Create : GroupEditorRoute

    data class Edit(val groupId: Int) : GroupEditorRoute
}
