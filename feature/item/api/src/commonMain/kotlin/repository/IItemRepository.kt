package com.mkulikov.taskb.feature.item.api.repository

import com.mkulikov.taskb.feature.item.api.model.Item
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface IItemRepository<out T : Item> {
    suspend fun getById(id: Int): T?

    /** Changes only the date, if it still matches [expectedDate]. Returns false for stale or deleted items. */
    suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean

    suspend fun delete(id: Int)

    fun observeCalendar(from: LocalDate, through: LocalDate): Flow<List<T>>

    suspend fun getOccurrence(id: Int, occurrence: ItemOccurrenceId): T? {
        return getById(id = id)?.takeIf { item -> item.occurrenceId == occurrence }
    }

    suspend fun moveOccurrence(
        id: Int,
        occurrence: ItemOccurrenceId?,
        expectedDate: LocalDate?,
        date: LocalDate,
        scope: ItemChangeScope,
    ): Boolean {
        return if (occurrence == null) reschedule(id = id, expectedDate = expectedDate, date = date) else false
    }

    suspend fun deleteOccurrence(id: Int, occurrence: ItemOccurrenceId?, scope: ItemChangeScope): Boolean {
        if (occurrence != null && scope != ItemChangeScope.EntireItem) return false
        delete(id = id)
        return true
    }
}
