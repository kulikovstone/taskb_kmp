package com.mkulikov.taskb.feature.item.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.item.api.model.Task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class TaskCompletionRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val zone = TimeZone.of(zoneId = "Asia/Yekaterinburg")

    private fun day(day: Int) = LocalDate(year = 2026, month = 11, day = day)

    @Test
    fun `completion uses the local planned day and survives duplicate requests reopening and database restart`() {
        runBlocking {
            val file = folder.root.toPath().resolve("completion-dates.db")
            // Thursday locally, while UTC is still Wednesday.
            var now = Instant.parse(input = "2026-11-04T20:30:15.123Z")
            val createdAt = now
            val clock =
                object : Clock {
                    override fun now() = now
                }
            val cases = listOf(
                day(day = 2) to Instant.parse(input = "2026-11-02T18:59:59.999Z"),
                day(day = 4) to Instant.parse(input = "2026-11-04T18:59:59.999Z"),
                day(day = 5) to now,
                day(day = 6) to now,
                null to now,
            )
            val completions = mutableMapOf<Int, Instant>()
            val db = AppDatabase.open(file = file)
            try {
                val repository = TaskRepository(database = lazy { db }, clock = clock, timeZone = { zone })
                for ((index, case) in cases.withIndex()) {
                    val (date, expected) = case
                    val name = "Task $index"
                    assertTrue(actual = repository.save(task = Task(name = name, dueDate = date)))
                    val task = repository.tasks.first().single { task -> task.name == name }
                    repository.setCompleted(id = task.id, completed = true)
                    assertEquals(expected = expected, actual = repository.getById(id = task.id)?.completedAt)
                    assertEquals(
                        expected = expected.toEpochMilliseconds(),
                        actual = db.taskDao().getById(id = task.id)?.completedAt,
                    )
                    completions[task.id] = expected
                }
                now += 5.days
                for ((id, expected) in completions) {
                    repository.setCompleted(id = id, completed = true)
                    assertEquals(expected = expected, actual = repository.getById(id = id)?.completedAt)
                    assertEquals(expected = createdAt, actual = repository.getById(id = id)?.createdAt)
                }
                val id = completions.keys.first()
                repository.setCompleted(id = id, completed = false)
                assertNull(actual = repository.getById(id = id)?.completedAt)
                repository.setCompleted(id = id, completed = true)
                assertEquals(
                    expected = completions.getValue(key = id),
                    actual = repository.getById(id = id)?.completedAt,
                )
            } finally {
                db.close()
            }
            val reopened = AppDatabase.open(file = file)
            try {
                for ((id, expected) in completions) assertEquals(
                    expected = expected.toEpochMilliseconds(),
                    actual = reopened.taskDao().getById(id = id)?.completedAt,
                )
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun `end of planned day follows daylight saving transitions instead of adding 24 hours`() {
        runBlocking {
            val berlin = TimeZone.of(zoneId = "Europe/Berlin")
            val cases = listOf(
                Triple(
                    first = LocalDate(year = 2026, month = 3, day = 29),
                    second = Instant.parse(input = "2026-03-30T10:00:00Z"),
                    third = Instant.parse(input = "2026-03-29T21:59:59.999Z"),
                ),
                Triple(
                    first = LocalDate(year = 2026, month = 10, day = 25),
                    second = Instant.parse(input = "2026-10-26T10:00:00Z"),
                    third = Instant.parse(input = "2026-10-25T22:59:59.999Z"),
                ),
            )
            val db = AppDatabase.open(file = folder.root.toPath().resolve("daylight-saving.db"))
            try {
                for ((date, now, expected) in cases) {
                    val clock =
                        object : Clock {
                            override fun now() = now
                        }
                    val repository = TaskRepository(database = lazy { db }, clock = clock, timeZone = { berlin })
                    assertTrue(actual = repository.save(task = Task(name = "$date", dueDate = date)))
                    val task = repository.tasks.first().single { task -> task.dueDate == date }
                    repository.setCompleted(id = task.id, completed = true)
                    assertEquals(expected = expected, actual = repository.getById(id = task.id)?.completedAt)
                }
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `recurring completion uses the moved date and preserves other occurrences when reopened`() {
        runBlocking {
            var now = Instant.parse(input = "2026-11-05T08:30:15.123Z")
            val clock =
                object : Clock {
                    override fun now() = now
                }
            val db = AppDatabase.open(file = folder.root.toPath().resolve("moved-completion.db"))
            try {
                val repository = TaskRepository(database = lazy { db }, clock = clock, timeZone = { zone })
                assertTrue(
                    actual = repository.save(
                        task = Task(
                            name = "Routine",
                            dueDate = day(day = 1),
                            recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily, occurrenceCount = 3),
                        )
                    )
                )
                val events = repository.observeCalendar(
                    from = day(day = 1),
                    through = day(day = 6),
                ).first().sortedBy(selector = Task::dueDate)
                val movedDates = listOf(day(day = 3), day(day = 6), day(day = 5))
                val expected = listOf(Instant.parse(input = "2026-11-03T18:59:59.999Z"), now, now)
                for ((index, event) in events.withIndex()) {
                    assertTrue(
                        actual = repository.moveOccurrence(
                            id = event.id,
                            occurrence = event.occurrenceId,
                            expectedDate = event.dueDate,
                            date = movedDates[index],
                            scope = ItemChangeScope.ThisOccurrence,
                        )
                    )
                    assertTrue(
                        actual = repository.setOccurrenceCompleted(
                            id = event.id,
                            occurrence = event.occurrenceId,
                            completed = true,
                        )
                    )
                    assertEquals(
                        expected = expected[index],
                        actual = repository
                            .getOccurrence(id = event.id, occurrence = requireNotNull(value = event.occurrenceId))
                            ?.completedAt,
                    )
                }
                now += 5.days
                for ((index, event) in events.withIndex()) {
                    assertTrue(
                        actual = repository.setOccurrenceCompleted(
                            id = event.id,
                            occurrence = event.occurrenceId,
                            completed = true,
                        )
                    )
                    assertEquals(
                        expected = expected[index],
                        actual = repository
                            .getOccurrence(id = event.id, occurrence = requireNotNull(value = event.occurrenceId))
                            ?.completedAt,
                    )
                }
                val first = events.first()
                val key = requireNotNull(value = first.occurrenceId)
                assertTrue(
                    actual = repository.setOccurrenceCompleted(id = first.id, occurrence = key, completed = false)
                )
                assertNull(actual = repository.getOccurrence(id = first.id, occurrence = key)?.completedAt)
                assertTrue(
                    actual = repository.moveOccurrence(
                        id = first.id,
                        occurrence = key,
                        expectedDate = day(day = 3),
                        date = day(day = 4),
                        scope = ItemChangeScope.ThisOccurrence,
                    )
                )
                assertTrue(
                    actual = repository.setOccurrenceCompleted(id = first.id, occurrence = key, completed = true)
                )
                val completed = assertNotNull(actual = repository.getOccurrence(id = first.id, occurrence = key))
                assertEquals(expected = day(day = 4), actual = completed.dueDate)
                assertEquals(
                    expected = Instant.parse(input = "2026-11-04T18:59:59.999Z"),
                    actual = completed.completedAt,
                )
                for (index in 1..2) assertEquals(
                    expected = expected[index],
                    actual = repository
                        .getOccurrence(
                            id = events[index].id,
                            occurrence = requireNotNull(value = events[index].occurrenceId),
                        )
                        ?.completedAt,
                )
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `finished series keeps the latest completion date regardless of the order in which events were checked off`() {
        runBlocking {
            val clock =
                object : Clock {
                    override fun now() = Instant.parse(input = "2026-11-05T08:30:00Z")
                }
            val db = AppDatabase.open(file = folder.root.toPath().resolve("series-completion.db"))
            try {
                val repository = TaskRepository(database = lazy { db }, clock = clock, timeZone = { zone })
                assertTrue(
                    actual = repository.save(
                        task = Task(
                            name = "Routine",
                            dueDate = day(day = 1),
                            recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily, occurrenceCount = 3),
                        )
                    )
                )
                val events = repository.observeCalendar(
                    from = day(day = 1),
                    through = day(day = 3),
                ).first().sortedBy(selector = Task::dueDate)
                for (index in listOf(2, 0, 1)) {
                    val event = events[index]
                    assertTrue(
                        actual = repository.setOccurrenceCompleted(
                            id = event.id,
                            occurrence = event.occurrenceId,
                            completed = true,
                        )
                    )
                }
                val expected = Instant.parse(input = "2026-11-03T18:59:59.999Z")
                val id = events.first().id
                assertEquals(expected = expected, actual = repository.getById(id = id)?.completedAt)
                assertEquals(
                    expected = expected.toEpochMilliseconds(),
                    actual = db.taskDao().getById(id = id)?.completedAt,
                )
                assertTrue(actual = repository.observeTaskList(today = day(day = 5)).first().isEmpty())
                assertEquals(
                    expected = 3,
                    actual = repository.observeCalendar(
                        from = day(day = 1),
                        through = day(day = 3),
                    ).first().count(predicate = Task::isCompleted),
                )
                val middle = events[1]
                assertTrue(
                    actual = repository.setOccurrenceCompleted(
                        id = id,
                        occurrence = middle.occurrenceId,
                        completed = false,
                    )
                )
                assertNull(actual = repository.getById(id = id)?.completedAt)
                assertTrue(
                    actual = repository.setOccurrenceCompleted(
                        id = id,
                        occurrence = middle.occurrenceId,
                        completed = true,
                    )
                )
                assertEquals(expected = expected, actual = repository.getById(id = id)?.completedAt)
            } finally {
                db.close()
            }
        }
    }
}
