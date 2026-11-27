package com.mkulikov.taskb.feature.item.api.repository

import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface IEventRepository : IItemRepository<Event> {
    val events: Flow<List<Event>>

    suspend fun save(event: Event): Boolean

    override fun observeCalendar(from: LocalDate, through: LocalDate): Flow<List<Event>> = events

    suspend fun saveOccurrence(event: Event, scope: ItemChangeScope): Boolean {
        return if (event.occurrenceId == null) save(event = event) else false
    }
}
