package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemOccurrenceId
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherCondition
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

internal fun calendarPreviewState(): CalendarState {
    val created = Instant.parse(input = "2026-10-01T12:00:00Z")
    val groups = listOf(
        Group(id = 1, name = "Work", color = GroupColor.Blue),
        Group(id = 2, name = "Personal", color = GroupColor.Green),
        Group(id = 3, name = "Learning", color = GroupColor.Purple),
        Group(id = 4, name = "Reading", color = GroupColor.Indigo),
        Group(id = 5, name = "Shopping", color = GroupColor.Orange),
        Group(id = 6, name = "Health", color = GroupColor.Teal),
    )
    val undated = listOf(
            "Organize project notes",
            "Try a new recipe",
            "Explore Kotlin Flow",
            "Choose the next book",
            "Make a shopping list",
            "Clean up downloads",
            "Call the repair shop",
        )
        .mapIndexed { index, name ->
            Task(
                id = index + 1,
                name = name,
                groupId = (index + 1).takeIf { occurrenceCount -> occurrenceCount <= 5 },
                createdAt = Instant.fromEpochMilliseconds(epochMilliseconds = created.toEpochMilliseconds() - index),
            )
        }
    val longUndated = Task(
        id = 8,
        name = "Prepare a detailed plan for the next release and review all remaining tasks",
        description = "Scope, milestones and owners for the next release.",
        groupId = 1,
        createdAt = Instant.fromEpochMilliseconds(epochMilliseconds = created.toEpochMilliseconds() + 1),
    )
    val scheduled = listOf(
            Triple(first = "2026-09-28", second = "Close September report", third = 1),
            Triple(first = "2026-09-30", second = "Prepare October plan", third = 1),
            Triple(first = "2026-10-01", second = "Review roadmap", third = 1),
            Triple(first = "2026-10-02", second = "Set monthly goals", third = 2),
            Triple(first = "2026-10-05", second = "Plan sprint", third = 1),
            Triple(first = "2026-10-06", second = "Dentist appointment", third = 6),
            Triple(first = "2026-10-07", second = "Read a chapter", third = 4),
            Triple(first = "2026-10-08", second = "Book train tickets", third = 2),
            Triple(first = "2026-10-09", second = "Send project update", third = 1),
            Triple(first = "2026-10-12", second = "Check backup", third = 1),
            Triple(first = "2026-10-13", second = "Grocery shopping", third = 5),
            Triple(first = "2026-10-14", second = "Team retrospective", third = 1),
            Triple(first = "2026-10-15", second = "Practice coroutines", third = 3),
            Triple(first = "2026-10-16", second = "Pay utility bills", third = 2),
            Triple(first = "2026-10-19", second = "Review pull requests", third = 1),
            Triple(first = "2026-10-20", second = "Evening walk", third = 6),
            Triple(first = "2026-10-21", second = "Kotlin study session", third = 3),
            Triple(first = "2026-10-22", second = "Pick up parcel", third = 5),
            Triple(first = "2026-10-23", second = "Update project notes", third = 1),
            Triple(first = "2026-10-26", second = "Plan next sprint", third = 1),
            Triple(first = "2026-10-27", second = "Book a dentist visit", third = 6),
            Triple(first = "2026-10-28", second = "Review calendar design", third = 1),
            Triple(first = "2026-10-28", second = "Buy groceries", third = 5),
            Triple(first = "2026-10-28", second = "Practice coroutines", third = 3),
            Triple(first = "2026-10-28", second = "Read a chapter", third = 4),
            Triple(first = "2026-10-28", second = "Plan weekend", third = 2),
            Triple(first = "2026-10-29", second = "Send project update", third = 1),
            Triple(first = "2026-10-29", second = "Evening walk", third = 6),
            Triple(first = "2026-10-30", second = "Monthly review", third = 1),
            Triple(first = "2026-10-31", second = "Plan November", third = 2),
            Triple(first = "2026-11-01", second = "Weekend walk", third = 6),
        )
        .mapIndexed { index, (date, name, group) ->
            Task(
                id = index + 100,
                name = name,
                dueDate = LocalDate.parse(input = date),
                groupId = group,
                description = when (index) {
                    26 -> "Summarize progress and the next milestones."
                    27 -> "Take the riverside route after work."
                    else -> "Review the details and prepare the next steps."
                },
                createdAt = Instant.fromEpochMilliseconds(epochMilliseconds = created.toEpochMilliseconds() - index),
                completedAt = created.takeIf { index in 2..4 },
            )
        }
    val ungrouped = listOf(
        Task(
            id = 200,
            name = "Renew passport",
            dueDate = LocalDate(year = 2026, month = 10, day = 3),
            createdAt = created,
        ),
        Task(
            id = 201,
            name = "Archive old files",
            dueDate = LocalDate(year = 2026, month = 10, day = 4),
            createdAt = created,
            completedAt = created,
        ),
    )
    val recentlyCompleted = Task(
        id = 9,
        name = "Send the release notes",
        groupId = 1,
        createdAt = created,
        completedAt = Instant.parse(input = "2026-10-29T11:00:00Z"),
    )
    return CalendarState(
        today = LocalDate(year = 2026, month = 10, day = 29),
        tasks = listOf(element = longUndated) + undated + scheduled + ungrouped + recentlyCompleted,
        groups = groups,
        isLoading = false,
        now = Instant.parse(input = "2026-10-29T12:00:00Z"),
    )
}

internal fun timedCalendarPreviewState(): CalendarState {
    val groups = listOf(
        Group(id = 1, name = "Work", color = GroupColor.Blue),
        Group(id = 2, name = "Personal", color = GroupColor.Green),
        Group(id = 3, name = "Travel", color = GroupColor.Purple),
    )
    val tasks = listOf(
        Task(
            id = 303,
            name = "Pay utility bills",
            dueDate = LocalDate(year = 2026, month = 11, day = 6),
            groupId = 2,
            description = "Electricity and internet for November.",
        ),
        Task(
            id = 307,
            name = "Plan next sprint",
            dueDate = LocalDate(year = 2026, month = 11, day = 9),
            groupId = 1,
        ),
        Task(
            id = 308,
            name = "Buy groceries",
            dueDate = LocalDate(year = 2026, month = 11, day = 2),
            groupId = 2,
        ),
        Task(
            id = 309,
            name = "Read one chapter",
            dueDate = LocalDate(year = 2026, month = 11, day = 4),
            groupId = 2,
        ),
        Task(
            id = 310,
            name = "Morning exercise",
            dueDate = LocalDate(year = 2026, month = 11, day = 6),
            groupId = 2,
            createdAt = Instant.parse(input = "2026-11-01T08:00:00Z"),
            recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily),
            occurrenceId = ItemOccurrenceId(
                scheduleId = 3100,
                scheduledDate = LocalDate(year = 2026, month = 11, day = 6),
            ),
            showInCalendar = false,
        ),
        Task(
            id = 311,
            name = "Read for 15 minutes",
            dueDate = LocalDate(year = 2026, month = 11, day = 6),
            createdAt = Instant.parse(input = "2026-11-01T07:00:00Z"),
            recurrence = ItemRecurrence(frequency = RecurrenceFrequency.Daily),
            occurrenceId = ItemOccurrenceId(
                scheduleId = 3110,
                scheduledDate = LocalDate(year = 2026, month = 11, day = 6),
            ),
            showInCalendar = false,
        ),
    )
    val events = listOf(
        Event(
            id = 300,
            name = "Team sync",
            dueDate = LocalDate(year = 2026, month = 11, day = 6),
            groupId = 1,
            startTime = LocalTime(hour = 12, minute = 0),
            durationMinutes = 60,
        ),
        Event(
            id = 301,
            name = "Coffee break",
            dueDate = LocalDate(year = 2026, month = 11, day = 6),
            startTime = LocalTime(hour = 15, minute = 0),
            durationMinutes = 30,
        ),
        Event(
            id = 302,
            name = "Business trip",
            dueDate = LocalDate(year = 2026, month = 11, day = 6),
            groupId = 3,
            startTime = LocalTime(hour = 18, minute = 0),
            durationMinutes = 3840,
        ),
        Event(
            id = 304,
            name = "Review mobile app design",
            dueDate = LocalDate(year = 2026, month = 11, day = 4),
            groupId = 1,
            startTime = LocalTime(hour = 11, minute = 0),
            durationMinutes = 60,
        ),
        Event(
            id = 305,
            name = "Design review",
            dueDate = LocalDate(year = 2026, month = 11, day = 2),
            groupId = 1,
            startTime = LocalTime(hour = 9, minute = 0),
            durationMinutes = 60,
        ),
        Event(
            id = 306,
            name = "Sprint review",
            dueDate = LocalDate(year = 2026, month = 10, day = 30),
            groupId = 1,
            startTime = LocalTime(hour = 16, minute = 0),
            durationMinutes = 60,
        ),
    )
    return CalendarState(
        today = LocalDate(year = 2026, month = 11, day = 2),
        selectedDate = LocalDate(year = 2026, month = 11, day = 6),
        tasks = tasks,
        events = events,
        groups = groups,
        isLoading = false,
        now = Instant.parse(input = "2026-11-02T12:00:00Z"),
    )
}

@Preview(name = "Calendar – completed day, weather and holiday", widthDp = 1440, heightDp = 900)
@Composable
private fun CalendarCompletedDayPreview() {
    val today = LocalDate(year = 2026, month = 1, day = 1)
    val now = Instant.parse(input = "2026-01-01T12:00:00Z")
    val location = AppLocation(
        name = "London, United Kingdom",
        latitude = 51.51,
        longitude = -0.13,
        timeZone = "Europe/London",
        countryCode = "GB",
    )
    val state = CalendarState(
        today = today,
        now = now,
        isLoading = false,
        tasks = listOf(
            Task(
                id = 1,
                name = "Plan the first week of January",
                dueDate = today,
                createdAt = now,
                completedAt = now,
            ),
        ),
        weather = WeatherForecast(
            location = location,
            days = listOf(
                DailyWeather(
                    date = today,
                    condition = WeatherCondition.Snow,
                    minimumCelsius = -2.0,
                    maximumCelsius = 3.0,
                ),
                DailyWeather(
                    date = LocalDate(year = 2026, month = 1, day = 2),
                    condition = WeatherCondition.PartlyCloudy,
                    minimumCelsius = 0.0,
                    maximumCelsius = 5.0,
                ),
            ),
        ),
        holidayCalendar = HolidayCalendar(
            location = location,
            holidays = listOf(
                PublicHoliday(id = "new-year", date = today, name = "New Year's Day", countryCode = "GB"),
            ),
        ),
    )
    TaskBTheme(darkTheme = false) {
        CalendarScreen(
            state = state,
            onIntent = {},
            onOpenGroups = {},
            onOpenItem = {},
            onCreateItem = { _, _ -> },
            onOpenSettings = {},
        )
    }
}
