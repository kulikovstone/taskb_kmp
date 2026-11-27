package com.mkulikov.taskb.feature.item.testing

import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.repository.IEventRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate

internal class FakeEventRepository(private val clock: Clock = Clock.System) : IEventRepository {
    val values = MutableStateFlow(value = emptyList<Event>())
    var fail = false
    var writes = 0
    var deletions = 0
    var subscriptions = 0
    var lastWrite: Event? = null
    var lastScope: ItemChangeScope? = null
    var onWrite: suspend () -> Unit = {}
    override val events = flow {
        if (fail) error(message = "Read failed")
        subscriptions++
        try {
            emitAll(flow = values)
        } finally {
            subscriptions--
        }
    }

    override suspend fun getById(id: Int): Event? {
        if (fail) error(message = "Read failed")
        return values.value.find { event -> event.id == id }
    }

    override suspend fun save(event: Event): Boolean {
        writes++
        onWrite()
        if (fail) error(message = "Write failed")
        lastWrite = event
        if (event.id != 0 && values.value.none { candidateEvent -> candidateEvent.id == event.id }) return false
        values.value = if (event.id == 0) {
            values.value + event.copy(id = (values.value.maxOfOrNull(selector = Event::id) ?: 0) + 1)
        } else {
            values.value.map { candidateEvent ->
                if (candidateEvent.id == event.id) {
                    candidateEvent.copy(
                        name = event.name,
                        description = event.description,
                        dueDate = event.dueDate,
                        groupId = event.groupId,
                        recurrence = event.recurrence,
                        startTime = event.startTime,
                        durationMinutes = event.durationMinutes,
                        durationDays = event.durationDays,
                    )
                } else {
                    candidateEvent
                }
            }
        }
        return true
    }

    override suspend fun delete(id: Int) {
        deletions++
        onWrite()
        values.value = values.value.filter { event -> event.id != id }
    }

    override suspend fun saveOccurrence(event: Event, scope: ItemChangeScope): Boolean {
        lastScope = scope
        return save(event = event)
    }

    override suspend fun deleteOccurrence(
        id: Int,
        occurrence: ItemOccurrenceId?,
        scope: ItemChangeScope,
    ): Boolean {
        lastScope = scope
        delete(id = id)
        return true
    }

    override suspend fun reschedule(id: Int, expectedDate: LocalDate?, date: LocalDate?): Boolean {
        onWrite()
        if (fail) error(message = "Write failed")
        if (values.value.none { event -> event.id == id && event.dueDate == expectedDate }) return false
        values.value = values.value.map { event ->
            if (event.id == id) {
                event.copy(
                    dueDate = date,
                    startTime = event.startTime.takeIf { date != null },
                    durationMinutes = event.durationMinutes.takeIf { date != null },
                )
            } else {
                event
            }
        }
        return true
    }
}
