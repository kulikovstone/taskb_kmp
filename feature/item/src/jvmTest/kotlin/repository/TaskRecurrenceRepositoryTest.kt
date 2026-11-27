package com.mkulikov.taskb.feature.item.repository

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.item.api.model.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek.*
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class TaskRecurrenceRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val clock =
        object : Clock {
            override fun now() = Instant.parse(input = "2026-11-04T10:00:00Z")
        }

    private fun date(day: Int) = LocalDate(year = 2026, month = 11, day = day)

    private val weekly = ItemRecurrence(frequency = RecurrenceFrequency.Weekly, weekdays = setOf(TUESDAY, THURSDAY))

    private suspend fun TaskRepository.events(): List<Task> {
        return observeCalendar(
            from = date(day = 1),
            through = date(day = 30),
        ).first().sortedBy(selector = Task::dueDate)
    }

    private fun event(events: List<Task>, day: Int) = events.single { task -> task.dueDate == date(day = day) }

    private fun id(task: Task) = requireNotNull(value = task.occurrenceId)

    private fun withRepository(block: suspend (TaskRepository, AppDatabase) -> Unit) {
        runBlocking {
            val db = AppDatabase.open(file = folder.root.toPath().resolve("test.db"))
            try {
                block(TaskRepository(database = lazy { db }, clock = clock), db)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `quick delete skips only the selected pending occurrence and protects completed occurrences`() {
        withRepository { repo, _ ->
            repo.save(task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly, quickDelete = true))
            val original = repo.events()
            val completed = event(events = original, day = 3)
            val pending = event(events = original, day = 5)
            repo.setOccurrenceCompleted(id = completed.id, occurrence = id(task = completed), completed = true)
            assertFalse(
                actual = repo.quickDeleteOccurrence(
                    id = completed.id,
                    occurrence = id(task = completed),
                    expectedDate = date(day = 3),
                )
            )
            assertTrue(
                actual = repo.quickDeleteOccurrence(
                    id = pending.id,
                    occurrence = id(task = pending),
                    expectedDate = date(day = 5),
                )
            )
            val remaining = repo.events()
            assertEquals(
                expected = original.filter { task -> task.key != pending.key }.map(transform = Task::key),
                actual = remaining.map(transform = Task::key),
            )
            assertTrue(actual = event(events = remaining, day = 3).isCompleted)
        }
    }

    @Test
    fun `one task owns all events and renaming an event updates every occurrence`() {
        withRepository { repo, db ->
            assertTrue(actual = repo.save(task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly)))
            val original = repo.events()
            assertEquals(
                expected = listOf(3, 5, 10, 12, 17, 19, 24, 26),
                actual = original.map { task -> task.dueDate?.day },
            )
            assertEquals(expected = 1, actual = original.map(transform = Task::id).distinct().size)
            assertEquals(expected = 8, actual = original.map(transform = Task::key).distinct().size)
            assertTrue(actual = repo.save(task = event(events = original, day = 10).copy(name = "Strength training")))
            assertTrue(actual = repo.events().all { task -> task.name == "Strength training" })
            assertEquals(expected = 1, actual = db.taskDao().observeTasks().first().size)
            assertEquals(expected = 1, actual = db.taskDao().getRecord(id = original.first().id)?.schedules?.size)
        }
    }

    @Test
    fun `description is shared by every occurrence and survives moves and schedule revisions`() {
        withRepository { repo, db ->
            val description = "Warm up for 10 minutes.\n\nBring a towel."
            repo.save(
                task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly, description = description)
            )
            val initial = repo.events()
            assertTrue(actual = initial.all { task -> task.description == description })
            val first = event(events = initial, day = 3)
            repo.setOccurrenceCompleted(id = first.id, occurrence = id(task = first), completed = true)
            val selected = event(events = initial, day = 5)
            assertTrue(
                actual = repo.saveOccurrence(
                    task = selected.copy(description = "New plan\nThree sets"),
                    scope = ItemChangeScope.ThisOccurrence,
                )
            )
            assertTrue(actual = repo.events().all { task -> task.description == "New plan\nThree sets" })
            assertEquals(expected = 1, actual = db.taskDao().getRecord(id = first.id)?.schedules?.size)

            assertTrue(
                actual = repo.moveOccurrence(
                    id = selected.id,
                    occurrence = id(task = selected),
                    expectedDate = date(day = 5),
                    date = date(day = 6),
                    scope = ItemChangeScope.ThisAndFollowing,
                )
            )
            assertTrue(actual = repo.events().all { task -> task.description == "New plan\nThree sets" })
            val moved = event(events = repo.events(), day = 6)
            val updatedDescription = "  Revised programme\nRest for 60 seconds.\n"
            assertTrue(
                actual = repo.save(
                    task = moved.copy(
                        recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily),
                        description = updatedDescription,
                    )
                )
            )
            assertTrue(actual = repo.events().all { task -> task.description == updatedDescription })
            assertEquals(expected = updatedDescription, actual = repo.getById(id = first.id)?.description)
            assertEquals(
                expected = updatedDescription,
                actual = repo.getOccurrence(id = first.id, occurrence = id(task = first))?.description,
            )
            assertTrue(
                actual = repo.observeTaskList(
                    today = date(day = 4),
                ).first().all { task -> task.description == updatedDescription }
            )
            assertTrue(actual = event(events = repo.events(), day = 3).isCompleted)

            assertTrue(
                actual = repo.save(
                    task = event(events = repo.events(), day = 6).copy(dueDate = date(day = 7), description = "")
                )
            )
            assertTrue(actual = repo.events().all { task -> task.description.isEmpty() })
            assertEquals(expected = "", actual = db.taskDao().getById(id = first.id)?.description)
        }
    }

    @Test
    fun `single moves keep stable identity completion and all other dates and support stale-safe undo`() {
        withRepository { repo, _ ->
            repo.save(task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly))
            val initial = repo.events()
            val moved = event(events = initial, day = 5)
            val done = event(events = initial, day = 3)
            assertTrue(
                actual = repo.setOccurrenceCompleted(id = done.id, occurrence = id(task = done), completed = true)
            )
            assertTrue(
                actual = repo.moveOccurrence(
                    id = moved.id,
                    occurrence = id(task = moved),
                    expectedDate = date(day = 5),
                    date = date(day = 6),
                    scope = ItemChangeScope.ThisOccurrence,
                )
            )
            val events = repo.events()
            assertTrue(actual = event(events = events, day = 3).isCompleted)
            assertEquals(expected = moved.key, actual = event(events = events, day = 6).key)
            assertEquals(
                expected = initial.filter { task -> task.dueDate != date(day = 3) && task.dueDate != date(day = 5) },
                actual = events.filter { task -> task.dueDate != date(day = 3) && task.dueDate != date(day = 6) },
            )
            assertFalse(
                actual = repo.moveOccurrence(
                    id = moved.id,
                    occurrence = id(task = moved),
                    expectedDate = date(day = 5),
                    date = date(day = 7),
                    scope = ItemChangeScope.ThisOccurrence,
                )
            )
            assertTrue(
                actual = repo.moveOccurrence(
                    id = moved.id,
                    occurrence = id(task = moved),
                    expectedDate = date(day = 6),
                    date = date(day = 5),
                    scope = ItemChangeScope.ThisOccurrence,
                )
            )
            assertEquals(expected = moved, actual = event(events = repo.events(), day = 5))
        }
    }

    @Test
    fun `moved events enter another window and do not hide a nearer natural occurrence in the list`() {
        withRepository { repo, _ ->
            repo.save(
                task = Task(
                    name = "Daily",
                    dueDate = date(day = 1),
                    recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily),
                )
            )
            val moved = event(events = repo.events(), day = 5)
            assertTrue(
                actual = repo.moveOccurrence(
                    id = moved.id,
                    occurrence = id(task = moved),
                    expectedDate = date(day = 5),
                    date = LocalDate(year = 2026, month = 12, day = 1),
                    scope = ItemChangeScope.ThisOccurrence,
                )
            )
            val sameDay = repo
                .observeCalendar(
                    from = LocalDate(year = 2026, month = 12, day = 1),
                    through = LocalDate(year = 2026, month = 12, day = 1),
                )
                .first()
            assertEquals(expected = 2, actual = sameDay.size)
            assertEquals(expected = 2, actual = sameDay.map(transform = Task::key).distinct().size)
            assertTrue(actual = sameDay.any { task -> task.key == moved.key })
            assertEquals(
                expected = listOf(3, 4),
                actual = repo
                    .observeTaskList(today = date(day = 4))
                    .first()
                    .filterNot(predicate = Task::isCompleted)
                    .sortedBy(selector = Task::dueDate)
                    .map { task -> task.dueDate?.day },
            )
            assertEquals(
                expected = listOf(4, 6),
                actual = repo
                    .observeTaskList(today = date(day = 5))
                    .first()
                    .filterNot(predicate = Task::isCompleted)
                    .sortedBy(selector = Task::dueDate)
                    .map { task -> task.dueDate?.day },
            )
        }
    }

    @Test
    fun `skip delete following and delete entire task have different scopes`() {
        withRepository { repo, db ->
            repo.save(task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly))
            val initial = repo.events()
            val skipped = event(events = initial, day = 5)
            assertTrue(
                actual = repo.deleteOccurrence(
                    id = skipped.id,
                    occurrence = id(task = skipped),
                    scope = ItemChangeScope.ThisOccurrence,
                )
            )
            assertEquals(
                expected = listOf(3, 10, 12, 17, 19, 24, 26),
                actual = repo.events().map { task -> task.dueDate?.day },
            )
            val boundary = event(events = repo.events(), day = 10)
            assertTrue(
                actual = repo.deleteOccurrence(
                    id = boundary.id,
                    occurrence = id(task = boundary),
                    scope = ItemChangeScope.ThisAndFollowing,
                )
            )
            assertEquals(expected = listOf(element = 3), actual = repo.events().map { task -> task.dueDate?.day })
            assertTrue(
                actual = repo
                    .observeCalendar(
                        from = LocalDate(year = 2027, month = 1, day = 1),
                        through = LocalDate(year = 2027, month = 12, day = 31),
                    )
                    .first()
                    .isEmpty()
            )
            assertEquals(expected = 1, actual = db.taskDao().observeTasks().first().size)
            assertTrue(
                actual = repo.deleteOccurrence(
                    id = boundary.id,
                    occurrence = id(task = boundary),
                    scope = ItemChangeScope.EntireItem,
                )
            )
            assertTrue(actual = db.taskDao().observeRecords().first().isEmpty())
        }
    }

    @Test
    fun `moving the remaining schedule preserves earlier events and remaining count`() {
        withRepository { repo, _ ->
            repo.save(
                task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly.copy(occurrenceCount = 6))
            )
            val done = event(events = repo.events(), day = 3)
            repo.setOccurrenceCompleted(id = done.id, occurrence = id(task = done), completed = true)
            val selected = event(events = repo.events(), day = 10)
            assertTrue(
                actual = repo.moveOccurrence(
                    id = selected.id,
                    occurrence = id(task = selected),
                    expectedDate = date(day = 10),
                    date = date(day = 11),
                    scope = ItemChangeScope.ThisAndFollowing,
                )
            )
            val events = repo.events()
            assertEquals(expected = listOf(3, 5, 11, 13, 18, 20), actual = events.map { task -> task.dueDate?.day })
            assertTrue(actual = event(events = events, day = 3).isCompleted)
            assertEquals(expected = 1, actual = events.map(transform = Task::id).distinct().size)
            assertEquals(
                expected = setOf(WEDNESDAY, FRIDAY),
                actual = event(events = events, day = 11).recurrence?.weekdays,
            )
        }
    }

    @Test
    fun `new schedule revisions retain completed history and share the task title`() {
        withRepository { repo, db ->
            repo.save(
                task = Task(
                    name = "Routine",
                    dueDate = date(day = 1),
                    recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily),
                )
            )
            val first = event(events = repo.events(), day = 1)
            val second = event(events = repo.events(), day = 2)
            repo.setOccurrenceCompleted(id = first.id, occurrence = id(task = first), completed = true)
            repo.setOccurrenceCompleted(id = second.id, occurrence = id(task = second), completed = true)
            val third = event(events = repo.events(), day = 3)
            assertTrue(actual = repo.save(task = third.copy(recurrence = weekly)))
            val events = repo.events()
            assertEquals(
                expected = listOf(1, 2, 3, 5, 10, 12, 17, 19, 24, 26),
                actual = events.map { task -> task.dueDate?.day },
            )
            assertTrue(actual = event(events = events, day = 1).isCompleted)
            assertTrue(actual = event(events = events, day = 2).isCompleted)
            assertEquals(expected = first.key, actual = event(events = events, day = 1).key)
            assertEquals(expected = 2, actual = db.taskDao().getRecord(id = first.id)?.schedules?.size)
            assertTrue(actual = repo.save(task = event(events = events, day = 1).copy(name = "Renamed history")))
            assertTrue(actual = repo.events().all { task -> task.name == "Renamed history" })
            assertEquals(expected = weekly, actual = repo.getById(id = first.id)?.recurrence)
        }
    }

    @Test
    fun `moving a previously moved event and following events anchors to the original series day`() {
        withRepository { repo, _ ->
            repo.save(task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly))
            val selected = event(events = repo.events(), day = 5)
            repo.moveOccurrence(
                id = selected.id,
                occurrence = id(task = selected),
                expectedDate = date(day = 5),
                date = date(day = 6),
                scope = ItemChangeScope.ThisOccurrence,
            )
            assertTrue(
                actual = repo.moveOccurrence(
                    id = selected.id,
                    occurrence = id(task = selected),
                    expectedDate = date(day = 6),
                    date = date(day = 7),
                    scope = ItemChangeScope.ThisAndFollowing,
                )
            )
            assertEquals(
                expected = listOf(3, 7, 12, 14, 19, 21, 26, 28),
                actual = repo.events().map { task -> task.dueDate?.day },
            )
        }
    }

    @Test
    fun `finite series completion and reopening update group counts without completing other events`() {
        withRepository { repo, db ->
            val group = db.groupDao().insert(group = GroupEntity(name = "Health", color = 0)).toInt()
            repo.save(
                task = Task(
                    name = "Routine",
                    dueDate = date(day = 1),
                    groupId = group,
                    recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily, occurrenceCount = 3),
                )
            )
            val events = repo.events()
            repo.setOccurrenceCompleted(id = events[0].id, occurrence = id(task = events[0]), completed = true)
            assertEquals(expected = 1, actual = db.groupDao().getWithTaskCounts(id = group)?.planned)
            assertEquals(expected = 2, actual = repo.events().count { task -> !task.isCompleted })
            for (event in events.drop(n = 1)) repo.setOccurrenceCompleted(
                id = event.id,
                occurrence = id(task = event),
                completed = true,
            )
            assertEquals(expected = 0, actual = db.groupDao().getWithTaskCounts(id = group)?.planned)
            assertEquals(expected = 1, actual = db.groupDao().getWithTaskCounts(id = group)?.completed)
            val usage = db.groupDao().observeGroupsWithUsage(createdSince = 0).first().single()
            assertEquals(expected = 1, actual = usage.recentTaskCount)
            repo.setOccurrenceCompleted(id = events[1].id, occurrence = id(task = events[1]), completed = false)
            assertEquals(expected = 1, actual = db.groupDao().getWithTaskCounts(id = group)?.planned)
            assertEquals(expected = 1, actual = repo.events().count { task -> !task.isCompleted })
        }
    }

    @Test
    fun `changing a completed event starts a future schedule without rewriting completed history`() {
        withRepository { repo, db ->
            repo.save(
                task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly.copy(occurrenceCount = 6))
            )
            val initial = repo.events()
            val first = event(events = initial, day = 3)
            val second = event(events = initial, day = 5)
            repo.setOccurrenceCompleted(id = first.id, occurrence = id(task = first), completed = true)
            repo.setOccurrenceCompleted(id = second.id, occurrence = id(task = second), completed = true)
            assertTrue(
                actual = repo.moveOccurrence(
                    id = first.id,
                    occurrence = id(task = first),
                    expectedDate = date(day = 3),
                    date = date(day = 11),
                    scope = ItemChangeScope.ThisAndFollowing,
                )
            )
            val events = repo.events()
            assertEquals(expected = listOf(3, 5, 11, 13, 18, 20), actual = events.map { task -> task.dueDate?.day })
            assertEquals(expected = first.key, actual = event(events = events, day = 3).key)
            assertEquals(expected = second.key, actual = event(events = events, day = 5).key)
            assertEquals(expected = 2, actual = events.count(predicate = Task::isCompleted))
            assertEquals(expected = 2, actual = db.taskDao().getRecord(id = first.id)?.schedules?.size)
        }
    }

    @Test
    fun `moving after completed history preserves the remaining duration of an end-date rule`() {
        withRepository { repo, _ ->
            repo.save(
                task = Task(
                    name = "Daily",
                    dueDate = date(day = 1),
                    recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily, endDate = date(day = 10)),
                )
            )
            val initial = repo.events()
            for (event in initial.filter { task -> requireNotNull(value = task.dueDate).day <= 5 }) repo
                .setOccurrenceCompleted(
                    id = event.id,
                    occurrence = id(task = event),
                    completed = true,
                )
            val first = event(events = initial, day = 1)
            assertTrue(
                actual = repo.moveOccurrence(
                    id = first.id,
                    occurrence = id(task = first),
                    expectedDate = date(day = 1),
                    date = date(day = 11),
                    scope = ItemChangeScope.ThisAndFollowing,
                )
            )
            assertEquals(
                expected = (1..5).toList() + (11..15).toList(),
                actual = repo.events().map { task -> task.dueDate?.day },
            )
            assertEquals(expected = 5, actual = repo.events().count(predicate = Task::isCompleted))
        }
    }

    @Test
    fun `list shows the latest pending overdue and nearest pending upcoming event`() {
        withRepository { repo, _ ->
            repo.save(task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly))
            assertEquals(
                expected = listOf(3, 5),
                actual = repo.observeTaskList(today = date(day = 4)).first().map { task -> task.dueDate?.day },
            )
            val overdue = event(events = repo.events(), day = 3)
            repo.setOccurrenceCompleted(id = overdue.id, occurrence = id(task = overdue), completed = true)
            assertEquals(
                expected = listOf(element = 5),
                actual = repo
                    .observeTaskList(today = date(day = 4))
                    .first()
                    .filterNot(predicate = Task::isCompleted)
                    .map { task -> task.dueDate?.day },
            )
        }
    }

    @Test
    fun `occurrence exceptions and revisions survive database reopening`() {
        runBlocking {
            val path = folder.root.toPath().resolve("reopen.db")
            var taskId = 0
            AppDatabase.open(file = path).let { db ->
                try {
                    val repo = TaskRepository(database = lazy { db }, clock = clock)
                    repo.save(task = Task(name = "Workout", dueDate = date(day = 3), recurrence = weekly))
                    val done = event(events = repo.events(), day = 3)
                    taskId = done.id
                    repo.setOccurrenceCompleted(id = done.id, occurrence = id(task = done), completed = true)
                    val moved = event(events = repo.events(), day = 5)
                    repo.moveOccurrence(
                        id = moved.id,
                        occurrence = id(task = moved),
                        expectedDate = date(day = 5),
                        date = date(day = 6),
                        scope = ItemChangeScope.ThisOccurrence,
                    )
                } finally {
                    db.close()
                }
            }
            AppDatabase.open(file = path).let { db ->
                try {
                    val repo = TaskRepository(database = lazy { db }, clock = clock)
                    assertEquals(expected = weekly, actual = repo.getById(id = taskId)?.recurrence)
                    assertTrue(actual = event(events = repo.events(), day = 3).isCompleted)
                    assertNotNull(actual = event(events = repo.events(), day = 6))
                    assertEquals(expected = 1, actual = db.taskDao().observeTasks().first().size)
                } finally {
                    db.close()
                }
            }
        }
    }
}
