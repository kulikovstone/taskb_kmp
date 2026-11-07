package com.mkulikov.taskb.ui.component.date_picker

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth

@Stable
internal class DatePickerState(initialMonth: YearMonth) {
    enum class View {
        Days,
        Months,
        Years,
    }

    var month by mutableStateOf(value = initialMonth)
        private set

    var view by mutableStateOf(value = View.Days)
        private set

    val yearText = TextFieldState(initialText = initialMonth.year.toString())
    var yearError by mutableStateOf(value = false)
        private set

    var yearRangeStart by mutableStateOf(value = initialMonth.year / 20 * 20)
        private set

    val canGoBack
        get() = month > YearMonth(year = FirstYear, month = 1)

    val canGoForward
        get() = month < YearMonth(year = LastYear, month = 12)

    val canShowPreviousYears
        get() = yearRangeStart > 0

    val canShowNextYears
        get() = yearRangeStart < LastYear / 20 * 20

    val firstVisibleYear
        get() = maxOf(a = FirstYear, b = yearRangeStart)

    val lastVisibleYear
        get() = minOf(a = LastYear, b = yearRangeStart + 19)

    fun toggleMonths() {
        view = if (view == View.Months) View.Days else View.Months
    }

    fun toggleYears() {
        if (view == View.Years) {
            view = View.Days
        } else {
            yearText.setTextAndPlaceCursorAtEnd(text = month.year.toString())
            yearRangeStart = month.year / 20 * 20
            yearError = false
            view = View.Years
        }
    }

    fun moveMonth(forward: Boolean) {
        if (forward && canGoForward) month = month.plusMonth()
        if (!forward && canGoBack) month = month.minusMonth()
        view = View.Days
    }

    fun chooseMonth(number: Int) {
        month = YearMonth(year = month.year, month = number)
        view = View.Days
    }

    fun chooseYear(year: Int) {
        if (year !in FirstYear..LastYear) return
        month = YearMonth(year = year, month = month.month)
        yearText.setTextAndPlaceCursorAtEnd(text = year.toString())
        yearError = false
        view = View.Days
    }

    fun editYear(text: String) {
        yearText.setTextAndPlaceCursorAtEnd(text = text)
        yearError = false
    }

    fun clearYearError() {
        yearError = false
    }

    fun applyYear() {
        val year = yearText.text.toString().trim().toIntOrNull()
        if (year == null || year !in FirstYear..LastYear) yearError = true else chooseYear(year = year)
    }

    fun moveYearRange(forward: Boolean) {
        yearRangeStart = (yearRangeStart + if (forward) 20 else -20).coerceIn(
            minimumValue = 0,
            maximumValue = LastYear / 20 * 20,
        )
    }

    fun showToday(today: YearMonth) {
        month = today
        view = View.Days
    }

    companion object {
        const val FirstYear = 1
        const val LastYear = 9999

        val Saver = listSaver<DatePickerState, Any>(
            save = { pickerState ->
                listOf(
                    pickerState.month.year,
                    pickerState.month.month.ordinal + 1,
                    pickerState.view.name,
                    pickerState.yearText.text.toString(),
                    pickerState.yearRangeStart,
                    pickerState.yearError,
                    pickerState.yearText.selection.start,
                    pickerState.yearText.selection.end,
                )
            },
            restore = { items ->
                DatePickerState(initialMonth = YearMonth(year = items[0] as Int, month = items[1] as Int)).apply {
                    view = View.valueOf(value = items[2] as String)
                    yearText.edit {
                        replace(start = 0, end = length, text = items[3] as String)
                        selection = TextRange(start = items[6] as Int, end = items[7] as Int)
                    }
                    yearRangeStart = items[4] as Int
                    yearError = items[5] as Boolean
                }
            },
        )
    }
}
