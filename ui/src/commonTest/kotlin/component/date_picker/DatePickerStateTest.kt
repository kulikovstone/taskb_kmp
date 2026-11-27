package com.mkulikov.taskb.ui.component.date_picker

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.YearMonth

class DatePickerStateTest {
    @Test
    fun `restoring the year picker retains the draft and selection`() {
        val state = DatePickerState(initialMonth = YearMonth(year = 2026, month = 11))
        state.toggleYears()
        state.yearText.edit {
            replace(start = 0, end = length, text = "2036")
            selection = TextRange(start = 1, end = 3)
        }
        val saved = with(receiver = DatePickerState.Saver) { SaverScope { true }.save(value = state) }
        val restored = requireNotNull(value = DatePickerState.Saver.restore(value = requireNotNull(value = saved)))
        assertEquals(expected = DatePickerState.View.Years, actual = restored.view)
        assertEquals(expected = "2036", actual = restored.yearText.text.toString())
        assertEquals(expected = TextRange(start = 1, end = 3), actual = restored.yearText.selection)
        restored.applyYear()
        assertEquals(expected = YearMonth(year = 2036, month = 11), actual = restored.month)
    }

    @Test
    fun `year and month can be changed independently`() {
        val state = DatePickerState(initialMonth = YearMonth(year = 2026, month = 11))
        state.toggleYears()
        assertEquals(expected = 2020, actual = state.firstVisibleYear)
        assertEquals(expected = 2039, actual = state.lastVisibleYear)
        state.chooseYear(year = 2036)
        assertEquals(expected = YearMonth(year = 2036, month = 11), actual = state.month)
        assertEquals(expected = DatePickerState.View.Days, actual = state.view)
        state.toggleMonths()
        state.chooseMonth(number = 6)
        assertEquals(expected = YearMonth(year = 2036, month = 6), actual = state.month)
        state.showToday(today = YearMonth(year = 2026, month = 11))
        assertEquals(expected = YearMonth(year = 2026, month = 11), actual = state.month)
    }

    @Test
    fun `invalid input never changes the browsing month`() {
        val state = DatePickerState(initialMonth = YearMonth(year = 2026, month = 2))
        state.toggleYears()
        for (input in listOf("", "0", "-1", "10000", "abc", "999999999999")) {
            state.editYear(text = input)
            assertFalse(actual = state.yearError)
            state.applyYear()
            assertTrue(actual = state.yearError, message = input)
            assertEquals(expected = YearMonth(year = 2026, month = 2), actual = state.month)
            assertEquals(expected = DatePickerState.View.Years, actual = state.view)
        }
        state.editYear(text = "2036")
        state.applyYear()
        assertFalse(actual = state.yearError)
        assertEquals(expected = YearMonth(year = 2036, month = 2), actual = state.month)
    }

    @Test
    fun `year ranges move twenty years and stop at supported boundaries`() {
        val state = DatePickerState(initialMonth = YearMonth(year = 2026, month = 11))
        state.moveYearRange(forward = true)
        assertEquals(expected = 2040, actual = state.firstVisibleYear)
        assertEquals(expected = 2059, actual = state.lastVisibleYear)
        state.moveYearRange(forward = false)
        assertEquals(expected = 2020, actual = state.firstVisibleYear)
        state.chooseYear(year = 1)
        state.toggleYears()
        state.moveYearRange(forward = false)
        assertFalse(actual = state.canShowPreviousYears)
        assertEquals(expected = 1, actual = state.firstVisibleYear)
        state.chooseYear(year = 9999)
        state.toggleYears()
        state.moveYearRange(forward = true)
        assertFalse(actual = state.canShowNextYears)
        assertEquals(expected = 9980, actual = state.firstVisibleYear)
        assertEquals(expected = 9999, actual = state.lastVisibleYear)
    }

    @Test
    fun `month navigation crosses years without overflowing the supported range`() {
        val state = DatePickerState(initialMonth = YearMonth(year = 2026, month = 12))
        state.moveMonth(forward = true)
        assertEquals(expected = YearMonth(year = 2027, month = 1), actual = state.month)
        state.moveMonth(forward = false)
        assertEquals(expected = YearMonth(year = 2026, month = 12), actual = state.month)
        state.chooseYear(year = 1)
        state.chooseMonth(number = 1)
        assertFalse(actual = state.canGoBack)
        state.moveMonth(forward = false)
        assertEquals(expected = YearMonth(year = 1, month = 1), actual = state.month)
        state.chooseYear(year = 9999)
        state.chooseMonth(number = 12)
        assertFalse(actual = state.canGoForward)
        state.moveMonth(forward = true)
        assertEquals(expected = YearMonth(year = 9999, month = 12), actual = state.month)
    }
}
