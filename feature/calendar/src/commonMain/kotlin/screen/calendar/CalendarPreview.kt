package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

internal fun calendarPreviewState(): CalendarState {
    val created = Instant.parse("2026-10-01T12:00:00Z")
    val groups = listOf(Group(1, "Work", GroupColor.Blue), Group(2, "Personal", GroupColor.Green), Group(3, "Learning", GroupColor.Purple),
        Group(4, "Reading", GroupColor.Indigo), Group(5, "Shopping", GroupColor.Orange), Group(6, "Health", GroupColor.Teal))
    val undated = listOf("Organize project notes", "Try a new recipe", "Explore Kotlin Flow", "Choose the next book", "Make a shopping list", "Clean up downloads", "Call the repair shop")
        .mapIndexed { index, name -> Task(id = index + 1, name = name, groupId = (index + 1).takeIf { it <= 5 }, createdAt = Instant.fromEpochMilliseconds(created.toEpochMilliseconds() - index)) }
    val longUndated = Task(
        id = 8,
        name = "Prepare a detailed plan for the next release and review all remaining tasks",
        description = "Scope, milestones and owners for the next release.",
        groupId = 1,
        createdAt = Instant.fromEpochMilliseconds(created.toEpochMilliseconds() + 1),
    )
    val scheduled = listOf(
        Triple("2026-09-28", "Close September report", 1), Triple("2026-09-30", "Prepare October plan", 1),
        Triple("2026-10-01", "Review roadmap", 1), Triple("2026-10-02", "Set monthly goals", 2),
        Triple("2026-10-05", "Plan sprint", 1), Triple("2026-10-06", "Dentist appointment", 6),
        Triple("2026-10-07", "Read a chapter", 4), Triple("2026-10-08", "Book train tickets", 2),
        Triple("2026-10-09", "Send project update", 1), Triple("2026-10-12", "Check backup", 1),
        Triple("2026-10-13", "Grocery shopping", 5), Triple("2026-10-14", "Team retrospective", 1),
        Triple("2026-10-15", "Practice coroutines", 3), Triple("2026-10-16", "Pay utility bills", 2),
        Triple("2026-10-19", "Review pull requests", 1), Triple("2026-10-20", "Evening walk", 6),
        Triple("2026-10-21", "Kotlin study session", 3), Triple("2026-10-22", "Pick up parcel", 5),
        Triple("2026-10-23", "Update project notes", 1), Triple("2026-10-26", "Plan next sprint", 1),
        Triple("2026-10-27", "Book a dentist visit", 6), Triple("2026-10-28", "Review calendar design", 1),
        Triple("2026-10-28", "Buy groceries", 5), Triple("2026-10-28", "Practice coroutines", 3),
        Triple("2026-10-28", "Read a chapter", 4), Triple("2026-10-28", "Plan weekend", 2),
        Triple("2026-10-29", "Send project update", 1), Triple("2026-10-29", "Evening walk", 6),
        Triple("2026-10-30", "Monthly review", 1), Triple("2026-10-31", "Plan November", 2),
        Triple("2026-11-01", "Weekend walk", 6),
    ).mapIndexed { index, (date, name, group) -> Task(index + 100, name, LocalDate.parse(date), group,
        description = when (index) {
            26 -> "Summarize progress and the next milestones."
            27 -> "Take the riverside route after work."
            else -> "Review the details and prepare the next steps."
        },
        createdAt = Instant.fromEpochMilliseconds(created.toEpochMilliseconds() - index), completedAt = created.takeIf { index in 2..4 }) }
    val ungrouped = listOf(
        Task(id = 200, name = "Renew passport", dueDate = LocalDate(2026, 10, 3), createdAt = created),
        Task(id = 201, name = "Archive old files", dueDate = LocalDate(2026, 10, 4), createdAt = created, completedAt = created),
    )
    val recentlyCompleted = Task(id = 9, name = "Send the release notes", groupId = 1, createdAt = created,
        completedAt = Instant.parse("2026-10-29T11:00:00Z"))
    return CalendarState(today = LocalDate(2026, 10, 29), tasks = listOf(longUndated) + undated + scheduled + ungrouped + recentlyCompleted, groups = groups, isLoading = false, now = Instant.parse("2026-10-29T12:00:00Z"))
}

internal fun timedCalendarPreviewState(): CalendarState {
    val groups = listOf(Group(1, "Work", GroupColor.Blue), Group(2, "Personal", GroupColor.Green), Group(3, "Travel", GroupColor.Purple))
    val tasks = listOf(
        Task(300, "Team sync", LocalDate(2026, 11, 6), 1, startTime = LocalTime(12, 0), durationMinutes = 60),
        Task(301, "Coffee break", LocalDate(2026, 11, 6), startTime = LocalTime(15, 0), durationMinutes = 30),
        Task(302, "Business trip", LocalDate(2026, 11, 6), 3, startTime = LocalTime(18, 0), durationMinutes = 3840),
        Task(303, "Pay utility bills", LocalDate(2026, 11, 6), 2, description = "Electricity and internet for November."),
        Task(304, "Review mobile app design", LocalDate(2026, 11, 4), 1, startTime = LocalTime(11, 0), durationMinutes = 60),
        Task(305, "Design review", LocalDate(2026, 11, 2), 1, startTime = LocalTime(9, 0), durationMinutes = 60),
        Task(306, "Sprint review", LocalDate(2026, 10, 30), 1, startTime = LocalTime(16, 0), durationMinutes = 60),
        Task(307, "Plan next sprint", LocalDate(2026, 11, 9), 1),
        Task(308, "Buy groceries", LocalDate(2026, 11, 2), 2),
        Task(309, "Read one chapter", LocalDate(2026, 11, 4), 2),
        Task(310, "Morning exercise", LocalDate(2026, 11, 6), 2,
            createdAt = Instant.parse("2026-11-01T08:00:00Z"),
            recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily), occurrenceId = TaskOccurrenceId(3100, LocalDate(2026, 11, 6))),
        Task(311, "Read for 15 minutes", LocalDate(2026, 11, 6),
            createdAt = Instant.parse("2026-11-01T07:00:00Z"),
            recurrence = TaskRecurrence(frequency = RecurrenceFrequency.Daily), occurrenceId = TaskOccurrenceId(3110, LocalDate(2026, 11, 6))),
    )
    return CalendarState(today = LocalDate(2026, 11, 2), selectedDate = LocalDate(2026, 11, 6), tasks = tasks,
        groups = groups, isLoading = false, now = Instant.parse("2026-11-02T12:00:00Z"))
}
