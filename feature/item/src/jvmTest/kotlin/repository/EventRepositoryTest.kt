package com.mkulikov.taskb.feature.item.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.item.api.database.TaskEntity
import com.mkulikov.taskb.feature.item.api.model.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class EventRepositoryTest {
    @get:Rule val directory = TemporaryFolder()
    private val clock =
        object : Clock {
            override fun now() = Instant.parse(input = "2026-11-05T10:00:00Z")
        }

    private fun day(day: Int) = LocalDate(year = 2026, month = 11, day = day)

    private suspend fun EventRepository.calendar(): List<Event> {
        return observeCalendar(
            from = day(day = 1),
            through = day(day = 30),
        ).first().sortedBy(selector = Event::dueDate)
    }

    @Test
    fun `events retain all fields on restart and edits and deletion leave tasks independent`() {
        runBlocking<Unit> {
            val file = directory.root.toPath().resolve("events.db")
            val db = AppDatabase.open(file = file)
            try {
                val repo = EventRepository(database = lazy { db }, clock = clock)
                val group = db.groupDao().insert(group = GroupEntity(name = "Work", color = 0)).toInt()
                db.taskDao().insert(task = TaskEntity(name = "Task", completedAt = 123L))
                assertTrue(
                    actual = repo.save(
                        event = Event(
                            name = "  Conference  ",
                            description = "Line one\nLine two",
                            groupId = group,
                            dueDate = day(day = 6),
                            startTime = LocalTime(hour = 23, minute = 30),
                            durationMinutes = 1560,
                        )
                    )
                )
                val event = repo.events.first().single()
                assertEquals(expected = "Conference", actual = event.name)
                assertEquals(
                    expected = event,
                    actual = repo.observeCalendar(from = day(day = 8), through = day(day = 8)).first().single(),
                )
                assertTrue(actual = repo.save(event = event.copy(name = "Updated conference")))
                assertEquals(expected = 123L, actual = db.taskDao().getById(id = event.id)?.completedAt)
                assertFalse(
                    actual = repo.reschedule(id = event.id, expectedDate = day(day = 1), date = day(day = 10))
                )
                assertTrue(
                    actual = repo.reschedule(id = event.id, expectedDate = day(day = 6), date = day(day = 10))
                )
            } finally {
                db.close()
            }
            val reopened = AppDatabase.open(file = file)
            try {
                val repo = EventRepository(database = lazy { reopened }, clock = clock)
                val event = repo.events.first().single()
                assertEquals(expected = "Updated conference", actual = event.name)
                assertEquals(expected = "Line one\nLine two", actual = event.description)
                assertEquals(expected = day(day = 10), actual = event.dueDate)
                assertEquals(expected = LocalTime(hour = 23, minute = 30), actual = event.startTime)
                assertEquals(expected = 1560, actual = event.durationMinutes)
                repo.delete(id = event.id)
                assertTrue(actual = repo.events.first().isEmpty())
                assertNotNull(actual = reopened.taskDao().getById(id = event.id))
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun `recurring event exceptions and following revisions preserve earlier occurrences`() {
        runBlocking<Unit> {
            val db = AppDatabase.open(file = directory.root.toPath().resolve("repeating.db"))
            try {
                val repo = EventRepository(database = lazy { db }, clock = clock)
                repo.save(
                    event = Event(
                        name = "Daily event",
                        dueDate = day(day = 1),
                        startTime = LocalTime(hour = 9, minute = 0),
                        recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily, occurrenceCount = 5),
                    )
                )
                val second = repo.calendar()[1]
                assertTrue(
                    actual = repo.saveOccurrence(
                        event = second.copy(startTime = LocalTime(hour = 12, minute = 0), durationMinutes = 30),
                        scope = ItemChangeScope.ThisOccurrence,
                    )
                )
                assertEquals(
                    expected = LocalTime(hour = 12, minute = 0),
                    actual = repo
                        .getOccurrence(id = second.id, occurrence = requireNotNull(value = second.occurrenceId))
                        ?.startTime,
                )
                assertTrue(
                    actual = repo.moveOccurrence(
                        id = second.id,
                        occurrence = second.occurrenceId,
                        expectedDate = day(day = 2),
                        date = day(day = 20),
                        scope = ItemChangeScope.ThisOccurrence,
                    )
                )
                assertEquals(
                    expected = second.key,
                    actual = repo.observeCalendar(from = day(day = 20), through = day(day = 20)).first().single().key,
                )
                val third = repo.calendar().single { event -> event.dueDate == day(day = 3) }
                assertTrue(
                    actual = repo.saveOccurrence(
                        event = third.copy(startTime = LocalTime(hour = 18, minute = 0), durationMinutes = 90),
                        scope = ItemChangeScope.ThisAndFollowing,
                    )
                )
                val after = repo.calendar()
                assertEquals(expected = listOf(1, 3, 4, 5, 20), actual = after.map { event -> event.dueDate?.day })
                assertEquals(expected = LocalTime(hour = 9, minute = 0), actual = after.first().startTime)
                assertEquals(expected = LocalTime(hour = 12, minute = 0), actual = after.last().startTime)
                assertTrue(
                    actual = after
                        .filter { event -> requireNotNull(value = event.dueDate) in day(day = 3)..day(day = 5) }
                        .all { event -> event.startTime == LocalTime(hour = 18, minute = 0) }
                )
                assertTrue(
                    actual = repo.deleteOccurrence(
                        id = second.id,
                        occurrence = second.occurrenceId,
                        scope = ItemChangeScope.ThisOccurrence,
                    )
                )
                assertTrue(
                    actual = repo.observeCalendar(from = day(day = 20), through = day(day = 20)).first().isEmpty()
                )
                assertEquals(expected = 4, actual = repo.calendar().size)
                assertFalse(actual = repo.saveOccurrence(event = second, scope = ItemChangeScope.ThisOccurrence))
                assertFailsWith<IllegalArgumentException> {
                    repo.save(event = Event(name = "", dueDate = day(day = 1)))
                }
                assertFailsWith<IllegalArgumentException> {
                    repo.save(event = Event(name = "No date", startTime = LocalTime(hour = 9, minute = 0)))
                }
            } finally {
                db.close()
            }
        }
    }
}
