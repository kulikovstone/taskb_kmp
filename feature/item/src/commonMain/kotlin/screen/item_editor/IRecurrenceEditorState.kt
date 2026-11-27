package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.recurrence.nextRecurrenceDates
import kotlinx.datetime.LocalDate

internal interface IRecurrenceEditorState {
    val dueDate: LocalDate?
    val recurrence: ItemRecurrence?
    val repeatInterval: TextFieldState
    val repeatDay: TextFieldState
    val repeatCount: TextFieldState

    val recurrenceError: String?
        get() = recurrence?.let { rule ->
            val start = dueDate
            when {
                repeatInterval.text.toString().toIntOrNull()?.let { value -> value in 1..9999 } != true ->
                    "Enter an interval between 1 and 9999."
                repeatDay.text.toString().toIntOrNull()?.let { value -> value in 1..31 } != true ->
                    "Enter a day between 1 and 31."
                rule.occurrenceCount != null &&
                    repeatCount.text.toString().toIntOrNull()?.let { value -> value in 1..1_000_000 } != true ->
                    "Enter between 1 and 1000000 occurrences."
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
                    interval in 1..9999
                } ?: rule.interval,
                dayOfMonth = repeatDay.text.toString().toIntOrNull()?.takeIf { day ->
                    day in 1..31
                } ?: rule.dayOfMonth,
                occurrenceCount = if (rule.occurrenceCount == null) {
                    null
                } else {
                    repeatCount.text.toString().toIntOrNull()?.takeIf { count -> count in 1..1_000_000 }
                        ?: rule.occurrenceCount
                },
            )
        }

    val upcomingDates: List<LocalDate>
        get() = editedRecurrence
            ?.let { rule -> dueDate?.let { date -> nextRecurrenceDates(rule = rule, start = date) } }
            .orEmpty()
}
