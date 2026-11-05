package com.mkulikov.taskb.feature.event.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.event.api.database.EventEntity
import com.mkulikov.taskb.feature.event.api.database.EventScheduleEntity
import com.mkulikov.taskb.feature.event.api.database.EventOccurrenceEntity
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class EventTimingRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-04T10:00:00Z") }
    private fun day(day: Int) = LocalDate(2026, 11, day)
    private suspend fun EventRepository.calendarEvents() = observeCalendar(day(1), day(30)).first().sortedBy { it.dueDate }

    @Test fun `a timed save defaults missing duration to one hour and rejects invalid durations`() = runBlocking<Unit> {
        val db = AppDatabase.open(folder.root.toPath().resolve("default-duration.db"))
        try {
            val repo = EventRepository(lazy { db }, clock)
            repo.save(Event(name = "Default hour", dueDate = day(6), startTime = LocalTime(12, 0), durationMinutes = null))
            val saved = repo.events.first().single()
            assertEquals(60, saved.durationMinutes)
            assertEquals(60, db.eventDao().getById(saved.id)?.durationMinutes)
            assertFailsWith<IllegalArgumentException> { repo.save(saved.copy(durationMinutes = 0)) }
            assertFailsWith<IllegalArgumentException> { repo.save(saved.copy(durationMinutes = -1)) }
            assertFailsWith<IllegalArgumentException> { repo.save(saved.copy(startTime = null, durationMinutes = 60)) }
            assertTrue(repo.save(saved.copy(startTime = null, durationMinutes = null)))
            assertNull(repo.getById(saved.id)?.durationMinutes)
            assertNull(db.eventDao().getById(saved.id)?.durationMinutes)
        } finally { db.close() }
    }

    @Test fun `legacy event schedule and occurrence timing normalize to one hour including overlap lookback`() = runBlocking<Unit> {
        val db = AppDatabase.open(folder.root.toPath().resolve("legacy-duration.db"))
        try {
            val dao = db.eventDao()
            val oneOff = dao.insert(EventEntity(name = "Legacy", dueDate = day(6).toEpochDays(), startTimeMinutes = 1410)).toInt()
            val series = dao.insert(EventEntity(name = "Legacy series", dueDate = day(1).toEpochDays(), recurring = true)).toInt()
            val schedule = dao.insertSchedule(EventScheduleEntity(eventId = series, startDate = day(1).toEpochDays(),
                frequency = "Daily", interval = 1, weekdays = 0, dayOfMonth = 1, month = 11,
                occurrenceCount = 3, startTimeMinutes = 1410))
            dao.saveOccurrence(EventOccurrenceEntity(schedule, day(2).toEpochDays(), series,
                overridesTiming = true, startTimeMinutes = 1425))
            val repo = EventRepository(lazy { db }, clock)
            val legacy = assertNotNull(repo.getById(oneOff))
            assertEquals(60, legacy.durationMinutes)
            assertEquals(legacy.key, repo.observeCalendar(day(7), day(7)).first().single().key)
            assertEquals(60, repo.getById(series)?.durationMinutes)
            val events = repo.calendarEvents().filter { it.id == series }
            assertTrue(events.all { it.durationMinutes == 60 })
            assertEquals(LocalTime(23, 45), events.single { it.dueDate == day(2) }.startTime)
            assertEquals(day(3), repo.observeCalendar(day(4), day(4)).first().single().dueDate)
            assertEquals(listOf(day(1), day(2)), repo.observeCalendar(day(2), day(2)).first().sortedBy { it.dueDate }.map { it.dueDate })
            assertTrue(repo.save(legacy.copy(name = "Repaired", durationMinutes = null)))
            assertEquals(60, dao.getById(oneOff)?.durationMinutes)
        } finally { db.close() }
    }

    @Test fun `timed details survive reopening and long events overlap a later calendar window`() = runBlocking<Unit> {
        val file = folder.root.toPath().resolve("trip.db")
        AppDatabase.open(file).let { db ->
            try {
                val repo = EventRepository(lazy { db }, clock)
                repo.save(Event(name = "Business trip", dueDate = day(6), startTime = LocalTime(18, 0), durationMinutes = 3840))
                val trip = repo.events.first().single()
                assertEquals(1080, db.eventDao().getById(trip.id)?.startTimeMinutes)
                assertEquals(trip, repo.observeCalendar(day(8), day(8)).first().single())
                assertTrue(repo.observeCalendar(day(10), day(10)).first().isEmpty())



            } finally { db.close() }
        }
        AppDatabase.open(file).let { db ->
            try {
                val repo = EventRepository(lazy { db }, clock)
                val trip = repo.events.first().single()
                assertEquals(day(6), trip.dueDate)
                assertEquals(LocalTime(18, 0), trip.startTime)
                assertEquals(3840, trip.durationMinutes)

            } finally { db.close() }
        }
    }

    @Test fun `date moves preserve timing and moving to no date clears timing atomically`() = runBlocking<Unit> {
        val db = AppDatabase.open(folder.root.toPath().resolve("move.db"))
        try {
            val repo = EventRepository(lazy { db }, clock)
            repo.save(Event(name = "Flight", dueDate = day(6), startTime = LocalTime(23, 30), durationMinutes = 30))
            val flight = repo.events.first().single()
            assertTrue(repo.observeCalendar(day(7), day(7)).first().isEmpty())
            assertTrue(repo.reschedule(flight.id, day(6), day(9)))
            val moved = assertNotNull(repo.getById(flight.id))
            assertEquals(LocalTime(23, 30), moved.startTime)
            assertEquals(30, moved.durationMinutes)
            assertFalse(repo.reschedule(flight.id, day(6), null))
            assertEquals(moved, repo.getById(flight.id))
            assertTrue(repo.reschedule(flight.id, day(9), null))
            val undated = assertNotNull(repo.getById(flight.id))
            assertNull(undated.dueDate)
            assertNull(undated.startTime)
            assertNull(undated.durationMinutes)
            assertEquals(undated, repo.observeCalendar(day(9), day(9)).first().single())
            assertFailsWith<IllegalArgumentException> { repo.save(undated.copy(startTime = LocalTime(12, 0))) }
            assertFailsWith<IllegalArgumentException> { repo.save(moved.copy(durationMinutes = 0)) }
        } finally { db.close() }
    }

    @Test fun `one occurrence changes only its timing and can become an untimed event`() = runBlocking<Unit> {
        val db = AppDatabase.open(folder.root.toPath().resolve("occurrence.db"))
        try {
            val repo = EventRepository(lazy { db }, clock)
            repo.save(Event(name = "Daily", dueDate = day(1), startTime = LocalTime(9, 0), durationMinutes = 60,
                recurrence = EventRecurrence(RecurrenceFrequency.Daily, occurrenceCount = 6)))
            val before = repo.calendarEvents()
            val selected = before.single { it.dueDate == day(3) }
            assertTrue(repo.saveOccurrence(selected.copy(startTime = LocalTime(12, 0), durationMinutes = 30), EventChangeScope.ThisOccurrence))
            val changed = repo.calendarEvents().single { it.key == selected.key }
            assertEquals(LocalTime(12, 0), changed.startTime)
            assertEquals(30, changed.durationMinutes)
            assertEquals(before.filter { it.key != selected.key }, repo.calendarEvents().filter { it.key != selected.key })
            assertTrue(repo.saveOccurrence(changed.copy(startTime = null, durationMinutes = null), EventChangeScope.ThisOccurrence))
            val untimed = repo.calendarEvents().single { it.key == selected.key }
            assertEquals(selected.key, untimed.key)
            assertNull(untimed.startTime)
            assertTrue(repo.calendarEvents().filter { it.key != selected.key }.all { it.startTime == LocalTime(9, 0) })
            assertEquals(LocalTime(9, 0), repo.getById(selected.id)?.startTime)
        } finally { db.close() }
    }

    @Test fun `future timing revisions preserve earlier history and moved long occurrences overlap later windows`() = runBlocking<Unit> {
        val db = AppDatabase.open(folder.root.toPath().resolve("revisions.db"))
        try {
            val repo = EventRepository(lazy { db }, clock)
            repo.save(Event(name = "Routine", dueDate = day(1), startTime = LocalTime(9, 0), durationMinutes = 60,
                recurrence = EventRecurrence(RecurrenceFrequency.Daily, occurrenceCount = 6)))
            val first = repo.calendarEvents().first()

            val selected = repo.calendarEvents().single { it.dueDate == day(3) }
            assertTrue(repo.saveOccurrence(selected.copy(startTime = LocalTime(18, 0), durationMinutes = 3840), EventChangeScope.ThisAndFollowing))
            val events = repo.calendarEvents()
            assertEquals((1..6).map(::day), events.map { it.dueDate })
            assertTrue(events.filter { requireNotNull(it.dueDate) < day(3) }.all { it.startTime == LocalTime(9, 0) && it.durationMinutes == 60 })
            assertTrue(events.filter { requireNotNull(it.dueDate) >= day(3) }.all { it.startTime == LocalTime(18, 0) && it.durationMinutes == 3840 })

            assertEquals(2, db.eventDao().getRecord(first.id)?.schedules?.size)
            val overlapping = repo.observeCalendar(day(8), day(8)).first()
            assertEquals(listOf(day(5), day(6)), overlapping.sortedBy { it.dueDate }.map { it.dueDate })
            val moved = events.single { it.dueDate == day(3) }
            assertTrue(repo.moveOccurrence(moved.id, moved.occurrenceId, day(3), day(20), EventChangeScope.ThisOccurrence))
            assertEquals(moved.key, repo.observeCalendar(day(22), day(22)).first().single().key)
        } finally { db.close() }
    }

    @Test fun `moving a timing override and following events retains the selected event time`() = runBlocking<Unit> {
        val db = AppDatabase.open(folder.root.toPath().resolve("override-move.db"))
        try {
            val repo = EventRepository(lazy { db }, clock)
            repo.save(Event(name = "Daily", dueDate = day(1), startTime = LocalTime(9, 0), durationMinutes = 60,
                recurrence = EventRecurrence(RecurrenceFrequency.Daily, occurrenceCount = 5)))
            val selected = repo.calendarEvents().single { it.dueDate == day(3) }
            repo.saveOccurrence(selected.copy(startTime = LocalTime(12, 0), durationMinutes = 30), EventChangeScope.ThisOccurrence)
            val modified = repo.calendarEvents().single { it.key == selected.key }
            assertTrue(repo.moveOccurrence(modified.id, modified.occurrenceId, day(3), day(10), EventChangeScope.ThisAndFollowing))
            val after = repo.calendarEvents()
            assertEquals(listOf(1, 2, 10, 11, 12), after.map { it.dueDate?.day })
            assertTrue(after.take(2).all { it.startTime == LocalTime(9, 0) && it.durationMinutes == 60 })
            assertTrue(after.drop(2).all { it.startTime == LocalTime(12, 0) && it.durationMinutes == 30 })
        } finally { db.close() }
    }
}
