package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence.Companion.MAX_INTERVAL
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence.Companion.MAX_OCCURRENCE_COUNT
import com.mkulikov.taskb.feature.item.api.model.ItemType
import com.mkulikov.taskb.feature.item.api.model.eventEndDateTime
import com.mkulikov.taskb.feature.item.api.recurrence.nextRecurrenceDates
import com.mkulikov.taskb.ui.input.withTextIfChanged
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

internal const val DEFAULT_REPEAT_COUNT = 10

internal sealed interface ScheduleDetails {
    val type: ItemType
    val dueDate: LocalDate?
}

internal data class TaskSchedule(
    override val dueDate: LocalDate? = null,
    val showInCalendar: Boolean = true,
    val quickDelete: Boolean = false,
) : ScheduleDetails {
    override val type: ItemType
        get() = ItemType.Task
}

internal data class EventSchedule(
    override val dueDate: LocalDate,
    val endDate: LocalDate = dueDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
) : ScheduleDetails {
    override val type: ItemType
        get() = ItemType.Event

    val editedDurationDays: Int?
        get() = (endDate.toEpochDays() - dueDate.toEpochDays() + 1).takeIf { days ->
            days in 1..Int.MAX_VALUE.toLong()
        }?.toInt()

    val editedDurationMinutes: Int?
        get() {
            val start = startTime ?: return null
            val end = endTime ?: return null
            val minutes = (endDate.toEpochDays() - dueDate.toEpochDays()) * 1440 + end.hour * 60 + end.minute -
                start.hour * 60 -
                start.minute
            return minutes.takeIf { minutes -> minutes in 1..Int.MAX_VALUE.toLong() }?.toInt()
        }

    val savedDurationDays
        get() = if (startTime == null) editedDurationDays else 1

    fun startingOn(date: LocalDate): EventSchedule {
        val days = (endDate.toEpochDays() - dueDate.toEpochDays()).coerceAtLeast(minimumValue = 0)
        return copy(dueDate = date, endDate = date.plus(value = days, unit = DateTimeUnit.DAY))
    }

    fun startingAt(time: LocalTime?): EventSchedule {
        if (time == null) return copy(startTime = null, endTime = null)
        val end = eventEndDateTime(
            date = dueDate,
            time = time,
            durationMinutes = editedDurationMinutes ?: Event.DEFAULT_DURATION_MINUTES,
        )
        return copy(
            startTime = time,
            endTime = end.time,
            endDate = if (startTime == null) maxOf(a = endDate, b = end.date) else end.date,
        )
    }

    val timingError: String?
        get() = when {
            endDate < dueDate -> "The end date must not be before the start date."
            startTime != null && editedDurationMinutes == null -> "The end date and time must be after the start."
            editedDurationDays == null -> "The date range is too long."
            else -> null
        }
}

internal data class ScheduleDraft(
    val details: ScheduleDetails,
    val recurrence: ItemRecurrence? = null,
    val repeatInterval: TextFieldState = TextFieldState(initialText = (recurrence?.interval ?: 1).toString()),
    val repeatDay: TextFieldState = TextFieldState(
        initialText = (recurrence?.dayOfMonth ?: details.dueDate?.day ?: 1).toString(),
    ),
    val repeatCount: TextFieldState = TextFieldState(
        initialText = (recurrence?.occurrenceCount ?: DEFAULT_REPEAT_COUNT).toString(),
    ),
) {
    val dueDate: LocalDate?
        get() = details.dueDate

    fun withRecurrence(rule: ItemRecurrence?): ScheduleDraft {
        val updated = if (details is TaskSchedule && rule == null && recurrence != null) {
            details.copy(showInCalendar = true, quickDelete = false)
        } else {
            details
        }
        return copy(
            details = updated,
            recurrence = rule,
            repeatInterval = repeatInterval.withTextIfChanged(text = (rule?.interval ?: 1).toString()),
            repeatDay = repeatDay.withTextIfChanged(text = (rule?.dayOfMonth ?: dueDate?.day ?: 1).toString()),
            repeatCount = repeatCount.withTextIfChanged(
                text = (rule?.occurrenceCount ?: DEFAULT_REPEAT_COUNT).toString(),
            ),
        )
    }

    val recurrenceError: String?
        get() = recurrence?.let { rule ->
            val start = dueDate
            val interval = repeatInterval.text.toString().toIntOrNull()
            val day = repeatDay.text.toString().toIntOrNull()
            val count = repeatCount.text.toString().toIntOrNull()
            when {
                interval == null || interval !in 1..MAX_INTERVAL -> "Enter an interval between 1 and $MAX_INTERVAL."
                day == null || day !in 1..31 -> "Enter a day between 1 and 31."
                rule.occurrenceCount != null && (count == null || count !in 1..MAX_OCCURRENCE_COUNT) ->
                    "Enter between 1 and $MAX_OCCURRENCE_COUNT occurrences."
                start == null -> "Choose a start date."
                rule.endDate?.let { end -> end < start } == true -> "The end date must not be before the start date."
                else -> null
            }
        }

    val editedRecurrence: ItemRecurrence?
        get() {
            val rule = recurrence ?: return null
            return rule.copy(
                interval = repeatInterval.text.toString().toIntOrNull()?.takeIf { interval ->
                    interval in 1..MAX_INTERVAL
                } ?: rule.interval,
                dayOfMonth = repeatDay.text.toString().toIntOrNull()?.takeIf { day ->
                    day in 1..31
                } ?: rule.dayOfMonth,
                occurrenceCount = if (rule.occurrenceCount == null) {
                    null
                } else {
                    repeatCount.text.toString().toIntOrNull()?.takeIf { count -> count in 1..MAX_OCCURRENCE_COUNT }
                        ?: rule.occurrenceCount
                },
            )
        }

    val upcomingDates: List<LocalDate>
        get() = editedRecurrence
            ?.let { rule -> dueDate?.let { date -> nextRecurrenceDates(rule = rule, start = date) } }
            .orEmpty()
}
