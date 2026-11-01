package com.mkulikov.taskb.ui.component.date_picker

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth

@Stable
internal class DatePickerState(initialMonth: YearMonth) {
    enum class View { Days, Months, Years }

    var month by mutableStateOf(initialMonth)
        private set
    var view by mutableStateOf(View.Days)
        private set
    var yearText by mutableStateOf(initialMonth.year.toString())
        private set
    var yearError by mutableStateOf(false)
        private set
    var yearRangeStart by mutableStateOf(initialMonth.year / 20 * 20)
        private set

    val canGoBack get() = month > YearMonth(FirstYear, 1)
    val canGoForward get() = month < YearMonth(LastYear, 12)
    val canShowPreviousYears get() = yearRangeStart > 0
    val canShowNextYears get() = yearRangeStart < LastYear / 20 * 20
    val firstVisibleYear get() = maxOf(FirstYear, yearRangeStart)
    val lastVisibleYear get() = minOf(LastYear, yearRangeStart + 19)

    fun toggleMonths() {
        view = if (view == View.Months) View.Days else View.Months
    }

    fun toggleYears() {
        if (view == View.Years) {
            view = View.Days
        } else {
            yearText = month.year.toString()
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
        month = YearMonth(month.year, number)
        view = View.Days
    }

    fun chooseYear(year: Int) {
        if (year !in FirstYear..LastYear) return
        month = YearMonth(year, month.month)
        yearText = year.toString()
        yearError = false
        view = View.Days
    }

    fun editYear(text: String) {
        yearText = text
        yearError = false
    }

    fun applyYear() {
        val year = yearText.trim().toIntOrNull()
        if (year == null || year !in FirstYear..LastYear) yearError = true else chooseYear(year)
    }

    fun moveYearRange(forward: Boolean) {
        yearRangeStart = (yearRangeStart + if (forward) 20 else -20).coerceIn(0, LastYear / 20 * 20)
    }

    fun showToday(today: YearMonth) {
        month = today
        view = View.Days
    }

    companion object {
        const val FirstYear = 1
        const val LastYear = 9999

        val Saver = listSaver<DatePickerState, Any>(
            save = { listOf(it.month.year, it.month.month.ordinal + 1, it.view.name, it.yearText, it.yearRangeStart, it.yearError) },
            restore = {
                DatePickerState(YearMonth(it[0] as Int, it[1] as Int)).apply {
                    view = View.valueOf(it[2] as String)
                    yearText = it[3] as String
                    yearRangeStart = it[4] as Int
                    yearError = it[5] as Boolean
                }
            },
        )
    }
}
