package com.mkulikov.taskb.feature.item.api

import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.feature.item.api.model.ItemType
import kotlinx.datetime.LocalDate

data class ItemCreationRoute(
    val initialDate: LocalDate? = null,
    val initialGroupId: Int? = null,
    val initialType: ItemType = ItemType.Task,
) : NavKey
