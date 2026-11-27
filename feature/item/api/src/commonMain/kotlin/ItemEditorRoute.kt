package com.mkulikov.taskb.feature.item.api

import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.ItemType
import kotlinx.datetime.LocalDate

data class ItemEditorRoute(
    val type: ItemType = ItemType.Task,
    val itemId: Int? = null,
    val occurrenceId: ItemOccurrenceId? = null,
    val initialDate: LocalDate? = null,
    val initialGroupId: Int? = null,
    val useLastSelectedGroup: Boolean = true,
    val initialName: String = "",
) : NavKey
