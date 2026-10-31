package com.mkulikov.taskb.feature.calendar.screen.calendar

import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.task.api.model.Task
import kotlinx.datetime.LocalDate
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
        createdAt = Instant.fromEpochMilliseconds(created.toEpochMilliseconds() - index), completedAt = created.takeIf { index in 2..4 }) }
    val ungrouped = listOf(
        Task(id = 200, name = "Renew passport", dueDate = LocalDate(2026, 10, 3), createdAt = created),
        Task(id = 201, name = "Archive old files", dueDate = LocalDate(2026, 10, 4), createdAt = created, completedAt = created),
    )
    val recentlyCompleted = Task(id = 9, name = "Send the release notes", groupId = 1, createdAt = created,
        completedAt = Instant.parse("2026-10-29T11:00:00Z"))
    return CalendarState(today = LocalDate(2026, 10, 29), tasks = listOf(longUndated) + undated + scheduled + ungrouped + recentlyCompleted, groups = groups, isLoading = false, now = Instant.parse("2026-10-29T12:00:00Z"))
}
