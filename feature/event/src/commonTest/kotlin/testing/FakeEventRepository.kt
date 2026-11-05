package com.mkulikov.taskb.feature.event.testing

import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

internal class FakeEventRepository(private val clock: Clock = Clock.System) : IEventRepository {
    val values = MutableStateFlow(emptyList<Event>())
    var fail = false
    var writes = 0
    var deletions = 0
    var subscriptions = 0
    var lastWrite: Event? = null
    var lastScope: EventChangeScope? = null
    var onWrite: suspend () -> Unit = {}
    override val events = flow {
        if (fail) error("Read failed")
        subscriptions++
        try { emitAll(values) } finally { subscriptions-- }
    }
    override suspend fun getById(id: Int): Event? {
        if (fail) error("Read failed")
        return values.value.find { it.id == id }
    }
    override suspend fun save(event: Event): Boolean {
        writes++
        onWrite()
        if (fail) error("Write failed")
        lastWrite = event
        if (event.id != 0 && values.value.none { it.id == event.id }) return false
        values.value = if (event.id == 0) values.value + event.copy(id = (values.value.maxOfOrNull { it.id } ?: 0) + 1)
            else values.value.map { if (it.id == event.id) it.copy(name = event.name, description = event.description, dueDate = event.dueDate,
                groupId = event.groupId, recurrence = event.recurrence, startTime = event.startTime, durationMinutes = event.durationMinutes, durationDays = event.durationDays) else it }
        return true
    }
    override suspend fun delete(id: Int) { deletions++; onWrite(); values.value = values.value.filter { it.id != id } }
    override suspend fun saveOccurrence(event: Event, scope: EventChangeScope): Boolean { lastScope = scope; return save(event) }
    override suspend fun deleteOccurrence(id: Int, occurrence: EventOccurrenceId?, scope: EventChangeScope): Boolean { lastScope = scope; delete(id); return true }
    override suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean {
        onWrite()
        if (fail) error("Write failed")
        if (values.value.none { it.id == id && it.dueDate == expectedDate }) return false
        values.value = values.value.map { if (it.id == id) it.copy(dueDate = date,
            startTime = it.startTime.takeIf { date != null }, durationMinutes = it.durationMinutes.takeIf { date != null }) else it }
        return true
    }
}
