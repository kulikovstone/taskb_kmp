package com.mkulikov.taskb.feature.task.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class TaskCompletionRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val zone = TimeZone.of("Asia/Yekaterinburg")
    private fun day(day: Int) = LocalDate(2026, 11, day)

    @Test
    fun `completion uses the local planned day and survives duplicate requests reopening and database restart`() = runBlocking {
        val file = folder.root.toPath().resolve("completion-dates.db")
        // Thursday locally, while UTC is still Wednesday.
        var now = Instant.parse("2026-11-04T20:30:15.123Z")
        val createdAt = now
        val clock = object : Clock { override fun now() = now }
        val cases = listOf(
            day(2) to Instant.parse("2026-11-02T18:59:59.999Z"),
            day(4) to Instant.parse("2026-11-04T18:59:59.999Z"),
            day(5) to now,
            day(6) to now,
            null to now,
        )
        val completions = mutableMapOf<Int, Instant>()
        val db = AppDatabase.open(file)
        try {
            val repository = TaskRepository(lazy { db }, clock, { zone })
            for ((index, case) in cases.withIndex()) {
                val (date, expected) = case
                val name = "Task $index"
                assertTrue(repository.save(Task(name = name, dueDate = date)))
                val task = repository.tasks.first().single { it.name == name }
                repository.setCompleted(task.id, true)
                assertEquals(expected, repository.getById(task.id)?.completedAt)
                assertEquals(expected.toEpochMilliseconds(), db.taskDao().getById(task.id)?.completedAt)
                completions[task.id] = expected
            }
            now += 5.days
            for ((id, expected) in completions) {
                repository.setCompleted(id, true)
                assertEquals(expected, repository.getById(id)?.completedAt)
                assertEquals(createdAt, repository.getById(id)?.createdAt)
            }
            val id = completions.keys.first()
            repository.setCompleted(id, false)
            assertNull(repository.getById(id)?.completedAt)
            repository.setCompleted(id, true)
            assertEquals(completions.getValue(id), repository.getById(id)?.completedAt)
        } finally { db.close() }
        val reopened = AppDatabase.open(file)
        try {
            for ((id, expected) in completions) assertEquals(expected.toEpochMilliseconds(), reopened.taskDao().getById(id)?.completedAt)
        } finally { reopened.close() }
    }

    @Test
    fun `end of planned day follows daylight saving transitions instead of adding 24 hours`() = runBlocking {
        val berlin = TimeZone.of("Europe/Berlin")
        val cases = listOf(
            Triple(LocalDate(2026, 3, 29), Instant.parse("2026-03-30T10:00:00Z"), Instant.parse("2026-03-29T21:59:59.999Z")),
            Triple(LocalDate(2026, 10, 25), Instant.parse("2026-10-26T10:00:00Z"), Instant.parse("2026-10-25T22:59:59.999Z")),
        )
        val db = AppDatabase.open(folder.root.toPath().resolve("daylight-saving.db"))
        try {
            for ((date, now, expected) in cases) {
                val clock = object : Clock { override fun now() = now }
                val repository = TaskRepository(lazy { db }, clock, { berlin })
                assertTrue(repository.save(Task(name = "$date", dueDate = date)))
                val task = repository.tasks.first().single { it.dueDate == date }
                repository.setCompleted(task.id, true)
                assertEquals(expected, repository.getById(task.id)?.completedAt)
            }
        } finally { db.close() }
    }

    @Test
    fun `recurring completion uses the moved date and preserves other occurrences when reopened`() = runBlocking {
        var now = Instant.parse("2026-11-05T08:30:15.123Z")
        val clock = object : Clock { override fun now() = now }
        val db = AppDatabase.open(folder.root.toPath().resolve("moved-completion.db"))
        try {
            val repository = TaskRepository(lazy { db }, clock, { zone })
            assertTrue(repository.save(Task(name = "Routine", dueDate = day(1),
                recurrence = TaskRecurrence(RecurrenceFrequency.Daily, occurrenceCount = 3))))
            val events = repository.observeCalendar(day(1), day(6)).first().sortedBy { it.dueDate }
            val movedDates = listOf(day(3), day(6), day(5))
            val expected = listOf(Instant.parse("2026-11-03T18:59:59.999Z"), now, now)
            for ((index, event) in events.withIndex()) {
                assertTrue(repository.moveOccurrence(event.id, event.occurrenceId, event.dueDate, movedDates[index], TaskChangeScope.ThisOccurrence))
                assertTrue(repository.setOccurrenceCompleted(event.id, event.occurrenceId, true))
                assertEquals(expected[index], repository.getOccurrence(event.id, requireNotNull(event.occurrenceId))?.completedAt)
            }
            now += 5.days
            for ((index, event) in events.withIndex()) {
                assertTrue(repository.setOccurrenceCompleted(event.id, event.occurrenceId, true))
                assertEquals(expected[index], repository.getOccurrence(event.id, requireNotNull(event.occurrenceId))?.completedAt)
            }
            val first = events.first()
            val key = requireNotNull(first.occurrenceId)
            assertTrue(repository.setOccurrenceCompleted(first.id, key, false))
            assertNull(repository.getOccurrence(first.id, key)?.completedAt)
            assertTrue(repository.moveOccurrence(first.id, key, day(3), day(4), TaskChangeScope.ThisOccurrence))
            assertTrue(repository.setOccurrenceCompleted(first.id, key, true))
            val completed = assertNotNull(repository.getOccurrence(first.id, key))
            assertEquals(day(4), completed.dueDate)
            assertEquals(Instant.parse("2026-11-04T18:59:59.999Z"), completed.completedAt)
            for (index in 1..2) assertEquals(expected[index], repository.getOccurrence(events[index].id, requireNotNull(events[index].occurrenceId))?.completedAt)
        } finally { db.close() }
    }

    @Test
    fun `finished series keeps the latest completion date regardless of the order in which events were checked off`() = runBlocking {
        val clock = object : Clock { override fun now() = Instant.parse("2026-11-05T08:30:00Z") }
        val db = AppDatabase.open(folder.root.toPath().resolve("series-completion.db"))
        try {
            val repository = TaskRepository(lazy { db }, clock, { zone })
            assertTrue(repository.save(Task(name = "Routine", dueDate = day(1),
                recurrence = TaskRecurrence(RecurrenceFrequency.Daily, occurrenceCount = 3))))
            val events = repository.observeCalendar(day(1), day(3)).first().sortedBy { it.dueDate }
            for (index in listOf(2, 0, 1)) {
                val event = events[index]
                assertTrue(repository.setOccurrenceCompleted(event.id, event.occurrenceId, true))
            }
            val expected = Instant.parse("2026-11-03T18:59:59.999Z")
            val id = events.first().id
            assertEquals(expected, repository.getById(id)?.completedAt)
            assertEquals(expected.toEpochMilliseconds(), db.taskDao().getById(id)?.completedAt)
            assertTrue(repository.observeTaskList(day(5)).first().isEmpty())
            assertEquals(3, repository.observeCalendar(day(1), day(3)).first().count { it.isCompleted })
            val middle = events[1]
            assertTrue(repository.setOccurrenceCompleted(id, middle.occurrenceId, false))
            assertNull(repository.getById(id)?.completedAt)
            assertTrue(repository.setOccurrenceCompleted(id, middle.occurrenceId, true))
            assertEquals(expected, repository.getById(id)?.completedAt)
        } finally { db.close() }
    }
}
