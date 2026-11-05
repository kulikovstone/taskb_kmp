package com.mkulikov.taskb.feature.event.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.event.api.model.*
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class EventRepositoryTest {
    @get:Rule val directory = TemporaryFolder()
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-05T10:00:00Z") }
    private fun day(day: Int) = LocalDate(2026, 11, day)
    private suspend fun EventRepository.calendar() = observeCalendar(day(1), day(30)).first().sortedBy { it.dueDate }

    @Test fun `events retain all fields on restart and edits and deletion leave tasks independent`() = runBlocking<Unit> {
        val file = directory.root.toPath().resolve("events.db")
        val db = AppDatabase.open(file)
        try {
            val repo = EventRepository(lazy { db }, clock)
            val group = db.groupDao().insert(GroupEntity(name = "Work", color = 0)).toInt()
            db.taskDao().insert(TaskEntity(name = "Task", completedAt = 123L))
            assertTrue(repo.save(Event(name = "  Conference  ", description = "Line one\nLine two", groupId = group,
                dueDate = day(6), startTime = LocalTime(23, 30), durationMinutes = 1560)))
            val event = repo.events.first().single()
            assertEquals("Conference", event.name)
            assertEquals(event, repo.observeCalendar(day(8), day(8)).first().single())
            assertTrue(repo.save(event.copy(name = "Updated conference")))
            assertEquals(123L, db.taskDao().getById(event.id)?.completedAt)
            assertFalse(repo.reschedule(event.id, day(1), day(10)))
            assertTrue(repo.reschedule(event.id, day(6), day(10)))
        } finally { db.close() }
        val reopened = AppDatabase.open(file)
        try {
            val repo = EventRepository(lazy { reopened }, clock)
            val event = repo.events.first().single()
            assertEquals("Updated conference", event.name)
            assertEquals("Line one\nLine two", event.description)
            assertEquals(day(10), event.dueDate)
            assertEquals(LocalTime(23, 30), event.startTime)
            assertEquals(1560, event.durationMinutes)
            repo.delete(event.id)
            assertTrue(repo.events.first().isEmpty())
            assertNotNull(reopened.taskDao().getById(event.id))
        } finally { reopened.close() }
    }

    @Test fun `recurring event exceptions and following revisions preserve earlier occurrences`() = runBlocking<Unit> {
        val db = AppDatabase.open(directory.root.toPath().resolve("repeating.db"))
        try {
            val repo = EventRepository(lazy { db }, clock)
            repo.save(Event(name = "Daily event", dueDate = day(1), startTime = LocalTime(9, 0),
                recurrence = EventRecurrence(RecurrenceFrequency.Daily, occurrenceCount = 5)))
            val second = repo.calendar()[1]
            assertTrue(repo.saveOccurrence(second.copy(startTime = LocalTime(12, 0), durationMinutes = 30), EventChangeScope.ThisOccurrence))
            assertEquals(LocalTime(12, 0), repo.getOccurrence(second.id, requireNotNull(second.occurrenceId))?.startTime)
            assertTrue(repo.moveOccurrence(second.id, second.occurrenceId, day(2), day(20), EventChangeScope.ThisOccurrence))
            assertEquals(second.key, repo.observeCalendar(day(20), day(20)).first().single().key)
            val third = repo.calendar().single { it.dueDate == day(3) }
            assertTrue(repo.saveOccurrence(third.copy(startTime = LocalTime(18, 0), durationMinutes = 90), EventChangeScope.ThisAndFollowing))
            val after = repo.calendar()
            assertEquals(listOf(1, 3, 4, 5, 20), after.map { it.dueDate?.day })
            assertEquals(LocalTime(9, 0), after.first().startTime)
            assertEquals(LocalTime(12, 0), after.last().startTime)
            assertTrue(after.filter { requireNotNull(it.dueDate) in day(3)..day(5) }.all { it.startTime == LocalTime(18, 0) })
            assertTrue(repo.deleteOccurrence(second.id, second.occurrenceId, EventChangeScope.ThisOccurrence))
            assertTrue(repo.observeCalendar(day(20), day(20)).first().isEmpty())
            assertEquals(4, repo.calendar().size)
            assertFalse(repo.saveOccurrence(second, EventChangeScope.ThisOccurrence))
            assertFailsWith<IllegalArgumentException> { repo.save(Event(name = "", dueDate = day(1))) }
            assertFailsWith<IllegalArgumentException> { repo.save(Event(name = "No date", startTime = LocalTime(9, 0))) }
        } finally { db.close() }
    }
}
