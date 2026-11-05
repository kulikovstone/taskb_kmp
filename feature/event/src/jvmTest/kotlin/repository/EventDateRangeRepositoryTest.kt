package com.mkulikov.taskb.feature.event.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.event.api.model.*
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.*

class EventDateRangeRepositoryTest {
    @get:Rule val directory = TemporaryFolder()
    private fun date(day: Int) = LocalDate(2026, 11, day)

    @Test fun `all day range survives reopening overlaps later windows and moves as a whole`() = runBlocking<Unit> {
        val file = directory.root.toPath().resolve("vacation.db")
        var id = 0
        AppDatabase.open(file).let { db ->
            try {
                val repo = EventRepository(lazy { db })
                assertTrue(repo.save(Event(name = "Vacation", dueDate = date(1), durationDays = 40)))
                val event = repo.events.first().single(); id = event.id
                assertEquals(date(1).plus(39, DateTimeUnit.DAY), event.endDate)
                assertEquals(id, repo.observeCalendar(LocalDate(2026, 12, 5), LocalDate(2026, 12, 5)).first().single().id)
                assertTrue(repo.observeCalendar(LocalDate(2026, 12, 11), LocalDate(2026, 12, 11)).first().isEmpty())
                assertFailsWith<IllegalArgumentException> { repo.save(event.copy(durationDays = 0)) }
            } finally { db.close() }
        }
        AppDatabase.open(file).let { db ->
            try {
                val repo = EventRepository(lazy { db })
                val event = assertNotNull(repo.getById(id))
                assertEquals(40, event.durationDays)
                assertNull(event.startTime)
                assertNull(event.durationMinutes)
                assertTrue(repo.reschedule(id, date(1), date(2)))
                assertEquals(date(2).plus(39, DateTimeUnit.DAY), repo.getById(id)?.endDate)
            } finally { db.close() }
        }
    }

    @Test fun `recurring all day spans support one occurrence edits moves and following revisions`() = runBlocking<Unit> {
        val db = AppDatabase.open(directory.root.toPath().resolve("recurring-range.db"))
        try {
            val repo = EventRepository(lazy { db })
            repo.save(Event(name = "Break", dueDate = date(1), durationDays = 3,
                recurrence = EventRecurrence(RecurrenceFrequency.Daily, interval = 7, occurrenceCount = 3)))
            val events = repo.observeCalendar(date(1), date(30)).first().sortedBy { it.dueDate }
            assertEquals(listOf(1, 8, 15), events.map { it.dueDate?.day })
            val second = events[1]
            assertTrue(repo.saveOccurrence(second.copy(durationDays = 5), EventChangeScope.ThisOccurrence))
            assertEquals(second.key, repo.observeCalendar(date(12), date(12)).first().single().key)
            assertTrue(repo.moveOccurrence(second.id, second.occurrenceId, date(8), date(20), EventChangeScope.ThisOccurrence))
            val moved = repo.observeCalendar(date(24), date(24)).first().single()
            assertEquals(second.key, moved.key)
            assertEquals(5, moved.durationDays)
            assertEquals(date(24), moved.endDate)
            val third = events[2]
            assertTrue(repo.saveOccurrence(third.copy(durationDays = 7), EventChangeScope.ThisAndFollowing))
            assertEquals(7, repo.getOccurrence(third.id, requireNotNull(repo.observeCalendar(date(15), date(15)).first().single().occurrenceId))?.durationDays)
            assertEquals(date(3), repo.observeCalendar(date(1), date(1)).first().single().endDate)
            assertEquals(second.key, repo.observeCalendar(date(24), date(24)).first().single().key)
        } finally { db.close() }
    }
}
