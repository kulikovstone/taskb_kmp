package com.mkulikov.taskb.feature.event.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.event.api.model.*
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class EventDateRangeRepositoryTest {
    @get:Rule val directory = TemporaryFolder()

    private fun date(day: Int) = LocalDate(year = 2026, month = 11, day = day)

    @Test
    fun `all day range survives reopening overlaps later windows and moves as a whole`() {
        runBlocking<Unit> {
            val file = directory.root.toPath().resolve("vacation.db")
            var id = 0
            AppDatabase.open(file = file).let { db ->
                try {
                    val repo = EventRepository(database = lazy { db })
                    assertTrue(
                        actual = repo.save(event = Event(name = "Vacation", dueDate = date(day = 1), durationDays = 40))
                    )
                    val event = repo.events.first().single()
                    id = event.id
                    assertEquals(
                        expected = date(day = 1).plus(value = 39, unit = DateTimeUnit.DAY),
                        actual = event.endDate,
                    )
                    assertEquals(
                        expected = id,
                        actual = repo
                            .observeCalendar(
                                from = LocalDate(year = 2026, month = 12, day = 5),
                                through = LocalDate(year = 2026, month = 12, day = 5),
                            )
                            .first()
                            .single()
                            .id,
                    )
                    assertTrue(
                        actual = repo
                            .observeCalendar(
                                from = LocalDate(year = 2026, month = 12, day = 11),
                                through = LocalDate(year = 2026, month = 12, day = 11),
                            )
                            .first()
                            .isEmpty()
                    )
                    assertFailsWith<IllegalArgumentException> { repo.save(event = event.copy(durationDays = 0)) }
                } finally {
                    db.close()
                }
            }
            AppDatabase.open(file = file).let { db ->
                try {
                    val repo = EventRepository(database = lazy { db })
                    val event = assertNotNull(actual = repo.getById(id = id))
                    assertEquals(expected = 40, actual = event.durationDays)
                    assertNull(actual = event.startTime)
                    assertNull(actual = event.durationMinutes)
                    assertTrue(
                        actual = repo.reschedule(id = id, expectedDate = date(day = 1), date = date(day = 2))
                    )
                    assertEquals(
                        expected = date(day = 2).plus(value = 39, unit = DateTimeUnit.DAY),
                        actual = repo.getById(id = id)?.endDate,
                    )
                } finally {
                    db.close()
                }
            }
        }
    }

    @Test
    fun `recurring all day spans support one occurrence edits moves and following revisions`() {
        runBlocking<Unit> {
            val db = AppDatabase.open(file = directory.root.toPath().resolve("recurring-range.db"))
            try {
                val repo = EventRepository(database = lazy { db })
                repo.save(
                    event = Event(
                        name = "Break",
                        dueDate = date(day = 1),
                        durationDays = 3,
                        recurrence = EventRecurrence(
                            frequency = RecurrenceFrequency.Daily,
                            interval = 7,
                            occurrenceCount = 3,
                        ),
                    )
                )
                val events = repo.observeCalendar(
                    from = date(day = 1),
                    through = date(day = 30),
                ).first().sortedBy(selector = Event::dueDate)
                assertEquals(expected = listOf(1, 8, 15), actual = events.map { event -> event.dueDate?.day })
                val second = events[1]
                assertTrue(
                    actual = repo.saveOccurrence(
                        event = second.copy(durationDays = 5),
                        scope = EventChangeScope.ThisOccurrence,
                    )
                )
                assertEquals(
                    expected = second.key,
                    actual = repo.observeCalendar(from = date(day = 12), through = date(day = 12)).first().single().key,
                )
                assertTrue(
                    actual = repo.moveOccurrence(
                        id = second.id,
                        occurrence = second.occurrenceId,
                        expectedDate = date(day = 8),
                        date = date(day = 20),
                        scope = EventChangeScope.ThisOccurrence,
                    )
                )
                val moved = repo.observeCalendar(from = date(day = 24), through = date(day = 24)).first().single()
                assertEquals(expected = second.key, actual = moved.key)
                assertEquals(expected = 5, actual = moved.durationDays)
                assertEquals(expected = date(day = 24), actual = moved.endDate)
                val third = events[2]
                assertTrue(
                    actual = repo.saveOccurrence(
                        event = third.copy(durationDays = 7),
                        scope = EventChangeScope.ThisAndFollowing,
                    )
                )
                assertEquals(
                    expected = 7,
                    actual = repo
                        .getOccurrence(
                            id = third.id,
                            occurrence = requireNotNull(
                                value = repo
                                    .observeCalendar(from = date(day = 15), through = date(day = 15))
                                    .first()
                                    .single()
                                    .occurrenceId
                            ),
                        )
                        ?.durationDays,
                )
                assertEquals(
                    expected = date(day = 3),
                    actual = repo
                        .observeCalendar(from = date(day = 1), through = date(day = 1))
                        .first()
                        .single()
                        .endDate,
                )
                assertEquals(
                    expected = second.key,
                    actual = repo.observeCalendar(from = date(day = 24), through = date(day = 24)).first().single().key,
                )
            } finally {
                db.close()
            }
        }
    }
}
