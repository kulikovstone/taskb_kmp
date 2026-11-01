package com.mkulikov.taskb.feature.task.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.task.api.model.*
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek.*
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class TaskRecurrenceRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-04T10:00:00Z") }
    private fun date(day: Int) = LocalDate(2026, 11, day)
    private val weekly = TaskRecurrence(RecurrenceFrequency.Weekly, weekdays = setOf(TUESDAY, THURSDAY))
    private suspend fun TaskRepository.events() = observeCalendar(date(1), date(30)).first().sortedBy { it.dueDate }
    private fun event(events: List<Task>, day: Int) = events.single { it.dueDate == date(day) }
    private fun id(task: Task) = requireNotNull(task.occurrenceId)
    private fun withRepository(block: suspend (TaskRepository, AppDatabase) -> Unit) = runBlocking {
        val db = AppDatabase.open(folder.root.toPath().resolve("test.db"))
        try { block(TaskRepository(lazy { db }, clock), db) } finally { db.close() }
    }

    @Test fun `one task owns all events and renaming an event updates every occurrence`() = withRepository { repo, db ->
        assertTrue(repo.save(Task(name = "Workout", dueDate = date(3), recurrence = weekly)))
        val original = repo.events()
        assertEquals(listOf(3, 5, 10, 12, 17, 19, 24, 26), original.map { it.dueDate?.day })
        assertEquals(1, original.map { it.id }.distinct().size)
        assertEquals(8, original.map { it.key }.distinct().size)
        assertTrue(repo.save(event(original, 10).copy(name = "Strength training")))
        assertTrue(repo.events().all { it.name == "Strength training" })
        assertEquals(1, db.taskDao().observeTasks().first().size)
        assertEquals(1, db.taskDao().getRecord(original.first().id)?.schedules?.size)
    }

    @Test fun `description is shared by every occurrence and survives moves and schedule revisions`() = withRepository { repo, db ->
        val description = "Warm up for 10 minutes.\n\nBring a towel."
        repo.save(Task(name = "Workout", dueDate = date(3), recurrence = weekly, description = description))
        val initial = repo.events()
        assertTrue(initial.all { it.description == description })
        val first = event(initial, 3)
        repo.setOccurrenceCompleted(first.id, id(first), true)
        val selected = event(initial, 5)
        assertTrue(repo.saveOccurrence(selected.copy(description = "New plan\nThree sets"), TaskChangeScope.ThisOccurrence))
        assertTrue(repo.events().all { it.description == "New plan\nThree sets" })
        assertEquals(1, db.taskDao().getRecord(first.id)?.schedules?.size)

        assertTrue(repo.moveOccurrence(selected.id, id(selected), date(5), date(6), TaskChangeScope.ThisAndFollowing))
        assertTrue(repo.events().all { it.description == "New plan\nThree sets" })
        val moved = event(repo.events(), 6)
        val updatedDescription = "  Revised programme\nRest for 60 seconds.\n"
        assertTrue(repo.save(moved.copy(recurrence = TaskRecurrence(RecurrenceFrequency.Daily), description = updatedDescription)))
        assertTrue(repo.events().all { it.description == updatedDescription })
        assertEquals(updatedDescription, repo.getById(first.id)?.description)
        assertEquals(updatedDescription, repo.getOccurrence(first.id, id(first))?.description)
        assertTrue(repo.observeTaskList(date(4)).first().all { it.description == updatedDescription })
        assertTrue(event(repo.events(), 3).isCompleted)

        assertTrue(repo.save(event(repo.events(), 6).copy(dueDate = date(7), description = "")))
        assertTrue(repo.events().all { it.description.isEmpty() })
        assertEquals("", db.taskDao().getById(first.id)?.description)
    }

    @Test fun `single moves keep stable identity completion and all other dates and support stale-safe undo`() = withRepository { repo, _ ->
        repo.save(Task(name = "Workout", dueDate = date(3), recurrence = weekly))
        val initial = repo.events()
        val moved = event(initial, 5)
        val done = event(initial, 3)
        assertTrue(repo.setOccurrenceCompleted(done.id, id(done), true))
        assertTrue(repo.moveOccurrence(moved.id, id(moved), date(5), date(6), TaskChangeScope.ThisOccurrence))
        val events = repo.events()
        assertTrue(event(events, 3).isCompleted)
        assertEquals(moved.key, event(events, 6).key)
        assertEquals(initial.filter { it.dueDate != date(3) && it.dueDate != date(5) }, events.filter { it.dueDate != date(3) && it.dueDate != date(6) })
        assertFalse(repo.moveOccurrence(moved.id, id(moved), date(5), date(7), TaskChangeScope.ThisOccurrence))
        assertTrue(repo.moveOccurrence(moved.id, id(moved), date(6), date(5), TaskChangeScope.ThisOccurrence))
        assertEquals(moved, event(repo.events(), 5))
    }

    @Test fun `moved events enter another window and do not hide a nearer natural occurrence in the list`() = withRepository { repo, _ ->
        repo.save(Task(name = "Daily", dueDate = date(1), recurrence = TaskRecurrence(RecurrenceFrequency.Daily)))
        val moved = event(repo.events(), 5)
        assertTrue(repo.moveOccurrence(moved.id, id(moved), date(5), LocalDate(2026, 12, 1), TaskChangeScope.ThisOccurrence))
        val sameDay = repo.observeCalendar(LocalDate(2026, 12, 1), LocalDate(2026, 12, 1)).first()
        assertEquals(2, sameDay.size)
        assertEquals(2, sameDay.map { it.key }.distinct().size)
        assertTrue(sameDay.any { it.key == moved.key })
        assertEquals(listOf(3, 4), repo.observeTaskList(date(4)).first().filterNot { it.isCompleted }.sortedBy { it.dueDate }.map { it.dueDate?.day })
        assertEquals(listOf(4, 6), repo.observeTaskList(date(5)).first().filterNot { it.isCompleted }.sortedBy { it.dueDate }.map { it.dueDate?.day })
    }

    @Test fun `skip delete following and delete entire task have different scopes`() = withRepository { repo, db ->
        repo.save(Task(name = "Workout", dueDate = date(3), recurrence = weekly))
        val initial = repo.events()
        val skipped = event(initial, 5)
        assertTrue(repo.deleteOccurrence(skipped.id, id(skipped), TaskChangeScope.ThisOccurrence))
        assertEquals(listOf(3, 10, 12, 17, 19, 24, 26), repo.events().map { it.dueDate?.day })
        val boundary = event(repo.events(), 10)
        assertTrue(repo.deleteOccurrence(boundary.id, id(boundary), TaskChangeScope.ThisAndFollowing))
        assertEquals(listOf(3), repo.events().map { it.dueDate?.day })
        assertTrue(repo.observeCalendar(LocalDate(2027, 1, 1), LocalDate(2027, 12, 31)).first().isEmpty())
        assertEquals(1, db.taskDao().observeTasks().first().size)
        assertTrue(repo.deleteOccurrence(boundary.id, id(boundary), TaskChangeScope.EntireTask))
        assertTrue(db.taskDao().observeRecords().first().isEmpty())
    }

    @Test fun `moving the remaining schedule preserves earlier events and remaining count`() = withRepository { repo, _ ->
        repo.save(Task(name = "Workout", dueDate = date(3), recurrence = weekly.copy(occurrenceCount = 6)))
        val done = event(repo.events(), 3)
        repo.setOccurrenceCompleted(done.id, id(done), true)
        val selected = event(repo.events(), 10)
        assertTrue(repo.moveOccurrence(selected.id, id(selected), date(10), date(11), TaskChangeScope.ThisAndFollowing))
        val events = repo.events()
        assertEquals(listOf(3, 5, 11, 13, 18, 20), events.map { it.dueDate?.day })
        assertTrue(event(events, 3).isCompleted)
        assertEquals(1, events.map { it.id }.distinct().size)
        assertEquals(setOf(WEDNESDAY, FRIDAY), event(events, 11).recurrence?.weekdays)
    }

    @Test fun `new schedule revisions retain completed history and share the task title`() = withRepository { repo, db ->
        repo.save(Task(name = "Routine", dueDate = date(1), recurrence = TaskRecurrence(RecurrenceFrequency.Daily)))
        val first = event(repo.events(), 1)
        val second = event(repo.events(), 2)
        repo.setOccurrenceCompleted(first.id, id(first), true)
        repo.setOccurrenceCompleted(second.id, id(second), true)
        val third = event(repo.events(), 3)
        assertTrue(repo.save(third.copy(recurrence = weekly)))
        val events = repo.events()
        assertEquals(listOf(1, 2, 3, 5, 10, 12, 17, 19, 24, 26), events.map { it.dueDate?.day })
        assertTrue(event(events, 1).isCompleted)
        assertTrue(event(events, 2).isCompleted)
        assertEquals(first.key, event(events, 1).key)
        assertEquals(2, db.taskDao().getRecord(first.id)?.schedules?.size)
        assertTrue(repo.save(event(events, 1).copy(name = "Renamed history")))
        assertTrue(repo.events().all { it.name == "Renamed history" })
        assertEquals(weekly, repo.getById(first.id)?.recurrence)
    }

    @Test fun `moving a previously moved event and following events anchors to the original series day`() = withRepository { repo, _ ->
        repo.save(Task(name = "Workout", dueDate = date(3), recurrence = weekly))
        val selected = event(repo.events(), 5)
        repo.moveOccurrence(selected.id, id(selected), date(5), date(6), TaskChangeScope.ThisOccurrence)
        assertTrue(repo.moveOccurrence(selected.id, id(selected), date(6), date(7), TaskChangeScope.ThisAndFollowing))
        assertEquals(listOf(3, 7, 12, 14, 19, 21, 26, 28), repo.events().map { it.dueDate?.day })
    }

    @Test fun `finite series completion and reopening update group counts without completing other events`() = withRepository { repo, db ->
        val group = db.groupDao().insert(GroupEntity(name = "Health", color = 0)).toInt()
        repo.save(Task(name = "Routine", dueDate = date(1), groupId = group, recurrence = TaskRecurrence(RecurrenceFrequency.Daily, occurrenceCount = 3)))
        val events = repo.events()
        repo.setOccurrenceCompleted(events[0].id, id(events[0]), true)
        assertEquals(1, db.groupDao().getWithTaskCounts(group)?.planned)
        assertEquals(2, repo.events().count { !it.isCompleted })
        for (event in events.drop(1)) repo.setOccurrenceCompleted(event.id, id(event), true)
        assertEquals(0, db.groupDao().getWithTaskCounts(group)?.planned)
        assertEquals(1, db.groupDao().getWithTaskCounts(group)?.completed)
        repo.setOccurrenceCompleted(events[1].id, id(events[1]), false)
        assertEquals(1, db.groupDao().getWithTaskCounts(group)?.planned)
        assertEquals(1, repo.events().count { !it.isCompleted })
    }

    @Test fun `changing a completed event starts a future schedule without rewriting completed history`() = withRepository { repo, db ->
        repo.save(Task(name = "Workout", dueDate = date(3), recurrence = weekly.copy(occurrenceCount = 6)))
        val initial = repo.events()
        val first = event(initial, 3)
        val second = event(initial, 5)
        repo.setOccurrenceCompleted(first.id, id(first), true)
        repo.setOccurrenceCompleted(second.id, id(second), true)
        assertTrue(repo.moveOccurrence(first.id, id(first), date(3), date(11), TaskChangeScope.ThisAndFollowing))
        val events = repo.events()
        assertEquals(listOf(3, 5, 11, 13, 18, 20), events.map { it.dueDate?.day })
        assertEquals(first.key, event(events, 3).key)
        assertEquals(second.key, event(events, 5).key)
        assertEquals(2, events.count { it.isCompleted })
        assertEquals(2, db.taskDao().getRecord(first.id)?.schedules?.size)
    }

    @Test fun `moving after completed history preserves the remaining duration of an end-date rule`() = withRepository { repo, _ ->
        repo.save(Task(name = "Daily", dueDate = date(1), recurrence = TaskRecurrence(RecurrenceFrequency.Daily, endDate = date(10))))
        val initial = repo.events()
        for (event in initial.filter { requireNotNull(it.dueDate).day <= 5 }) repo.setOccurrenceCompleted(event.id, id(event), true)
        val first = event(initial, 1)
        assertTrue(repo.moveOccurrence(first.id, id(first), date(1), date(11), TaskChangeScope.ThisAndFollowing))
        assertEquals((1..5).toList() + (11..15).toList(), repo.events().map { it.dueDate?.day })
        assertEquals(5, repo.events().count { it.isCompleted })
    }

    @Test fun `list shows the latest pending overdue and nearest pending upcoming event`() = withRepository { repo, _ ->
        repo.save(Task(name = "Workout", dueDate = date(3), recurrence = weekly))
        assertEquals(listOf(3, 5), repo.observeTaskList(date(4)).first().map { it.dueDate?.day })
        val overdue = event(repo.events(), 3)
        repo.setOccurrenceCompleted(overdue.id, id(overdue), true)
        assertEquals(listOf(5), repo.observeTaskList(date(4)).first().filterNot { it.isCompleted }.map { it.dueDate?.day })
    }

    @Test fun `occurrence exceptions and revisions survive database reopening`() = runBlocking {
        val path = folder.root.toPath().resolve("reopen.db")
        var taskId = 0
        AppDatabase.open(path).let { db ->
            try {
                val repo = TaskRepository(lazy { db }, clock)
                repo.save(Task(name = "Workout", dueDate = date(3), recurrence = weekly))
                val done = event(repo.events(), 3); taskId = done.id
                repo.setOccurrenceCompleted(done.id, id(done), true)
                val moved = event(repo.events(), 5)
                repo.moveOccurrence(moved.id, id(moved), date(5), date(6), TaskChangeScope.ThisOccurrence)
            } finally { db.close() }
        }
        AppDatabase.open(path).let { db ->
            try {
                val repo = TaskRepository(lazy { db }, clock)
                assertEquals(weekly, repo.getById(taskId)?.recurrence)
                assertTrue(event(repo.events(), 3).isCompleted)
                assertNotNull(event(repo.events(), 6))
                assertEquals(1, db.taskDao().observeTasks().first().size)
            } finally { db.close() }
        }
    }
}
