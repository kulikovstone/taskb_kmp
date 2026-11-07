package com.mkulikov.taskb.feature.event.api.repository

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface IEventRepository {
    val events: Flow<List<Event>>

    suspend fun getById(id: Int): Event?

    suspend fun save(event: Event): Boolean

    /** Changes only the date, if it still matches [expectedDate]. Returns false for stale or deleted events. */
    suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean

    suspend fun delete(id: Int)

    fun observeCalendar(from: LocalDate, through: LocalDate): Flow<List<Event>> = events

    suspend fun getOccurrence(id: Int, occurrence: EventOccurrenceId): Event? {
        return getById(
            id = id,
        )?.takeIf { event -> event.occurrenceId == occurrence }
    }

    suspend fun saveOccurrence(event: Event, scope: EventChangeScope): Boolean {
        return if (event.occurrenceId == null) save(event = event) else false
    }

    suspend fun moveOccurrence(
        id: Int,
        occurrence: EventOccurrenceId?,
        expectedDate: LocalDate?,
        date: LocalDate,
        scope: EventChangeScope,
    ): Boolean {
        return if (occurrence == null) reschedule(id = id, expectedDate = expectedDate, date = date) else false
    }

    suspend fun deleteOccurrence(id: Int, occurrence: EventOccurrenceId?, scope: EventChangeScope): Boolean {
        if (occurrence != null && scope != EventChangeScope.EntireEvent) return false
        delete(id = id)
        return true
    }
}
