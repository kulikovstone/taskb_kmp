package com.mkulikov.taskb.feature.item.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.item.api.database.EventEntity
import com.mkulikov.taskb.feature.item.api.database.EventOccurrenceEntity
import com.mkulikov.taskb.feature.item.api.database.EventScheduleEntity
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.RecurrenceFrequency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class EventTimingRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val clock =
        object : Clock {
            override fun now() = Instant.parse(input = "2026-11-04T10:00:00Z")
        }

    private fun day(day: Int) = LocalDate(year = 2026, month = 11, day = day)

    private suspend fun EventRepository.calendarEvents(): List<Event> {
        return observeCalendar(
            from = day(day = 1),
            through = day(day = 30),
        ).first().sortedBy(selector = Event::dueDate)
    }

    @Test
    fun `a timed save defaults missing duration to thirty minutes and rejects invalid durations`() {
        runBlocking<Unit> {
            val db = AppDatabase.open(file = folder.root.toPath().resolve("default-duration.db"))
            try {
                val repo = EventRepository(database = lazy { db }, clock = clock)
                repo.save(
                    event = Event(
                        name = "Default hour",
                        dueDate = day(day = 6),
                        startTime = LocalTime(hour = 12, minute = 0),
                        durationMinutes = null,
                    )
                )
                val saved = repo.events.first().single()
                assertEquals(expected = 30, actual = saved.durationMinutes)
                assertEquals(expected = 30, actual = db.eventDao().getById(id = saved.id)?.durationMinutes)
                assertFailsWith<IllegalArgumentException> { repo.save(event = saved.copy(durationMinutes = 0)) }
                assertFailsWith<IllegalArgumentException> { repo.save(event = saved.copy(durationMinutes = -1)) }
                assertFailsWith<IllegalArgumentException> {
                    repo.save(event = saved.copy(startTime = null, durationMinutes = 60))
                }
                assertTrue(actual = repo.save(event = saved.copy(startTime = null, durationMinutes = null)))
                assertNull(actual = repo.getById(id = saved.id)?.durationMinutes)
                assertNull(actual = db.eventDao().getById(id = saved.id)?.durationMinutes)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `legacy event schedule and occurrence timing normalize to one hour including overlap lookback`() {
        runBlocking<Unit> {
            val db = AppDatabase.open(file = folder.root.toPath().resolve("legacy-duration.db"))
            try {
                val dao = db.eventDao()
                val oneOff = dao.insert(
                        event = EventEntity(
                            name = "Legacy",
                            dueDate = day(day = 6).toEpochDays(),
                            startTimeMinutes = 1410,
                        )
                    )
                    .toInt()
                val series = dao.insert(
                        event = EventEntity(
                            name = "Legacy series",
                            dueDate = day(day = 1).toEpochDays(),
                            recurring = true,
                        )
                    )
                    .toInt()
                val schedule = dao.insertSchedule(
                    schedule = EventScheduleEntity(
                        eventId = series,
                        startDate = day(day = 1).toEpochDays(),
                        frequency = "Daily",
                        interval = 1,
                        weekdays = 0,
                        dayOfMonth = 1,
                        month = 11,
                        occurrenceCount = 3,
                        startTimeMinutes = 1410,
                    )
                )
                dao.saveOccurrence(
                    occurrence = EventOccurrenceEntity(
                        scheduleId = schedule,
                        scheduledDate = day(day = 2).toEpochDays(),
                        eventId = series,
                        overridesTiming = true,
                        startTimeMinutes = 1425,
                    )
                )
                val repo = EventRepository(database = lazy { db }, clock = clock)
                val legacy = assertNotNull(actual = repo.getById(id = oneOff))
                assertEquals(expected = 60, actual = legacy.durationMinutes)
                assertEquals(
                    expected = legacy.key,
                    actual = repo.observeCalendar(from = day(day = 7), through = day(day = 7)).first().single().key,
                )
                assertEquals(expected = 60, actual = repo.getById(id = series)?.durationMinutes)
                val events = repo.calendarEvents().filter { event -> event.id == series }
                assertTrue(actual = events.all { event -> event.durationMinutes == 60 })
                assertEquals(
                    expected = LocalTime(hour = 23, minute = 45),
                    actual = events.single { event -> event.dueDate == day(day = 2) }.startTime,
                )
                assertEquals(
                    expected = day(day = 3),
                    actual = repo.observeCalendar(from = day(day = 4), through = day(day = 4)).first().single().dueDate,
                )
                assertEquals(
                    expected = listOf(day(day = 1), day(day = 2)),
                    actual = repo
                        .observeCalendar(from = day(day = 2), through = day(day = 2))
                        .first()
                        .sortedBy(selector = Event::dueDate)
                        .map(transform = Event::dueDate),
                )
                assertTrue(actual = repo.save(event = legacy.copy(name = "Repaired", durationMinutes = null)))
                assertEquals(expected = 60, actual = dao.getById(id = oneOff)?.durationMinutes)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `timed details survive reopening and long events overlap a later calendar window`() {
        runBlocking<Unit> {
            val file = folder.root.toPath().resolve("trip.db")
            AppDatabase.open(file = file).let { db ->
                try {
                    val repo = EventRepository(database = lazy { db }, clock = clock)
                    repo.save(
                        event = Event(
                            name = "Business trip",
                            dueDate = day(day = 6),
                            startTime = LocalTime(hour = 18, minute = 0),
                            durationMinutes = 3840,
                        )
                    )
                    val trip = repo.events.first().single()
                    assertEquals(expected = 1080, actual = db.eventDao().getById(id = trip.id)?.startTimeMinutes)
                    assertEquals(
                        expected = trip,
                        actual = repo.observeCalendar(from = day(day = 8), through = day(day = 8)).first().single(),
                    )
                    assertTrue(
                        actual = repo.observeCalendar(from = day(day = 10), through = day(day = 10)).first().isEmpty()
                    )
                } finally {
                    db.close()
                }
            }
            AppDatabase.open(file = file).let { db ->
                try {
                    val repo = EventRepository(database = lazy { db }, clock = clock)
                    val trip = repo.events.first().single()
                    assertEquals(expected = day(day = 6), actual = trip.dueDate)
                    assertEquals(expected = LocalTime(hour = 18, minute = 0), actual = trip.startTime)
                    assertEquals(expected = 3840, actual = trip.durationMinutes)
                } finally {
                    db.close()
                }
            }
        }
    }

    @Test
    fun `date moves preserve timing and moving to no date clears timing atomically`() {
        runBlocking<Unit> {
            val db = AppDatabase.open(file = folder.root.toPath().resolve("move.db"))
            try {
                val repo = EventRepository(database = lazy { db }, clock = clock)
                repo.save(
                    event = Event(
                        name = "Flight",
                        dueDate = day(day = 6),
                        startTime = LocalTime(hour = 23, minute = 30),
                        durationMinutes = 30,
                    )
                )
                val flight = repo.events.first().single()
                assertTrue(
                    actual = repo.observeCalendar(from = day(day = 7), through = day(day = 7)).first().isEmpty()
                )
                assertTrue(
                    actual = repo.reschedule(id = flight.id, expectedDate = day(day = 6), date = day(day = 9))
                )
                val moved = assertNotNull(actual = repo.getById(id = flight.id))
                assertEquals(expected = LocalTime(hour = 23, minute = 30), actual = moved.startTime)
                assertEquals(expected = 30, actual = moved.durationMinutes)
                assertFalse(actual = repo.reschedule(id = flight.id, expectedDate = day(day = 6), date = null))
                assertEquals(expected = moved, actual = repo.getById(id = flight.id))
                assertTrue(actual = repo.reschedule(id = flight.id, expectedDate = day(day = 9), date = null))
                val undated = assertNotNull(actual = repo.getById(id = flight.id))
                assertNull(actual = undated.dueDate)
                assertNull(actual = undated.startTime)
                assertNull(actual = undated.durationMinutes)
                assertEquals(
                    expected = undated,
                    actual = repo.observeCalendar(from = day(day = 9), through = day(day = 9)).first().single(),
                )
                assertFailsWith<IllegalArgumentException> {
                    repo.save(event = undated.copy(startTime = LocalTime(hour = 12, minute = 0)))
                }
                assertFailsWith<IllegalArgumentException> { repo.save(event = moved.copy(durationMinutes = 0)) }
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `one occurrence changes only its timing and can become an untimed event`() {
        runBlocking<Unit> {
            val db = AppDatabase.open(file = folder.root.toPath().resolve("occurrence.db"))
            try {
                val repo = EventRepository(database = lazy { db }, clock = clock)
                repo.save(
                    event = Event(
                        name = "Daily",
                        dueDate = day(day = 1),
                        startTime = LocalTime(hour = 9, minute = 0),
                        durationMinutes = 60,
                        recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily, occurrenceCount = 6),
                    )
                )
                val before = repo.calendarEvents()
                val selected = before.single { event -> event.dueDate == day(day = 3) }
                assertTrue(
                    actual = repo.saveOccurrence(
                        event = selected.copy(startTime = LocalTime(hour = 12, minute = 0), durationMinutes = 30),
                        scope = ItemChangeScope.ThisOccurrence,
                    )
                )
                val changed = repo.calendarEvents().single { event -> event.key == selected.key }
                assertEquals(expected = LocalTime(hour = 12, minute = 0), actual = changed.startTime)
                assertEquals(expected = 30, actual = changed.durationMinutes)
                assertEquals(
                    expected = before.filter { event -> event.key != selected.key },
                    actual = repo.calendarEvents().filter { event -> event.key != selected.key },
                )
                assertTrue(
                    actual = repo.saveOccurrence(
                        event = changed.copy(startTime = null, durationMinutes = null),
                        scope = ItemChangeScope.ThisOccurrence,
                    )
                )
                val untimed = repo.calendarEvents().single { event -> event.key == selected.key }
                assertEquals(expected = selected.key, actual = untimed.key)
                assertNull(actual = untimed.startTime)
                assertTrue(
                    actual = repo
                        .calendarEvents()
                        .filter { event -> event.key != selected.key }
                        .all { event -> event.startTime == LocalTime(hour = 9, minute = 0) }
                )
                assertEquals(
                    expected = LocalTime(hour = 9, minute = 0),
                    actual = repo.getById(id = selected.id)?.startTime,
                )
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `future timing revisions preserve earlier history and moved long occurrences overlap later windows`() {
        runBlocking<Unit> {
            val db = AppDatabase.open(file = folder.root.toPath().resolve("revisions.db"))
            try {
                val repo = EventRepository(database = lazy { db }, clock = clock)
                repo.save(
                    event = Event(
                        name = "Routine",
                        dueDate = day(day = 1),
                        startTime = LocalTime(hour = 9, minute = 0),
                        durationMinutes = 60,
                        recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily, occurrenceCount = 6),
                    )
                )
                val first = repo.calendarEvents().first()

                val selected = repo.calendarEvents().single { event -> event.dueDate == day(day = 3) }
                assertTrue(
                    actual = repo.saveOccurrence(
                        event = selected.copy(startTime = LocalTime(hour = 18, minute = 0), durationMinutes = 3840),
                        scope = ItemChangeScope.ThisAndFollowing,
                    )
                )
                val events = repo.calendarEvents()
                assertEquals(expected = (1..6).map(transform = ::day), actual = events.map(transform = Event::dueDate))
                assertTrue(
                    actual = events
                        .filter { event -> requireNotNull(value = event.dueDate) < day(day = 3) }
                        .all { event -> event.startTime == LocalTime(
                            hour = 9,
                            minute = 0,
                        ) && event.durationMinutes == 60 }
                )
                assertTrue(
                    actual = events
                        .filter { event -> requireNotNull(value = event.dueDate) >= day(day = 3) }
                        .all { event -> event.startTime == LocalTime(
                            hour = 18,
                            minute = 0,
                        ) && event.durationMinutes == 3840 }
                )

                assertEquals(expected = 2, actual = db.eventDao().getRecord(id = first.id)?.schedules?.size)
                val overlapping = repo.observeCalendar(from = day(day = 8), through = day(day = 8)).first()
                assertEquals(
                    expected = listOf(day(day = 5), day(day = 6)),
                    actual = overlapping.sortedBy(selector = Event::dueDate).map(transform = Event::dueDate),
                )
                val moved = events.single { event -> event.dueDate == day(day = 3) }
                assertTrue(
                    actual = repo.moveOccurrence(
                        id = moved.id,
                        occurrence = moved.occurrenceId,
                        expectedDate = day(day = 3),
                        date = day(day = 20),
                        scope = ItemChangeScope.ThisOccurrence,
                    )
                )
                assertEquals(
                    expected = moved.key,
                    actual = repo.observeCalendar(from = day(day = 22), through = day(day = 22)).first().single().key,
                )
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `moving a timing override and following events retains the selected event time`() {
        runBlocking<Unit> {
            val db = AppDatabase.open(file = folder.root.toPath().resolve("override-move.db"))
            try {
                val repo = EventRepository(database = lazy { db }, clock = clock)
                repo.save(
                    event = Event(
                        name = "Daily",
                        dueDate = day(day = 1),
                        startTime = LocalTime(hour = 9, minute = 0),
                        durationMinutes = 60,
                        recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily, occurrenceCount = 5),
                    )
                )
                val selected = repo.calendarEvents().single { event -> event.dueDate == day(day = 3) }
                repo.saveOccurrence(
                    event = selected.copy(startTime = LocalTime(hour = 12, minute = 0), durationMinutes = 30),
                    scope = ItemChangeScope.ThisOccurrence,
                )
                val modified = repo.calendarEvents().single { event -> event.key == selected.key }
                assertTrue(
                    actual = repo.moveOccurrence(
                        id = modified.id,
                        occurrence = modified.occurrenceId,
                        expectedDate = day(day = 3),
                        date = day(day = 10),
                        scope = ItemChangeScope.ThisAndFollowing,
                    )
                )
                val after = repo.calendarEvents()
                assertEquals(expected = listOf(1, 2, 10, 11, 12), actual = after.map { event -> event.dueDate?.day })
                assertTrue(
                    actual = after.take(n = 2).all { event ->
                        event.startTime == LocalTime(hour = 9, minute = 0) && event.durationMinutes == 60
                    }
                )
                assertTrue(
                    actual = after.drop(n = 2).all { event ->
                        event.startTime == LocalTime(hour = 12, minute = 0) && event.durationMinutes == 30
                    }
                )
            } finally {
                db.close()
            }
        }
    }
}
