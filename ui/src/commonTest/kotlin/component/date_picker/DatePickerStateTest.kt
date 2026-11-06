package com.mkulikov.taskb.ui.component.date_picker

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatePickerStateTest {
    @Test
    fun `restoring the year picker retains the draft and selection`() {
        val state = DatePickerState(YearMonth(2026, 11))
        state.toggleYears()
        state.yearText.edit {
            replace(0, length, "2036")
            selection = TextRange(1, 3)
        }
        val saved = with(DatePickerState.Saver) { SaverScope { true }.save(state) }
        val restored = requireNotNull(DatePickerState.Saver.restore(requireNotNull(saved)))
        assertEquals(DatePickerState.View.Years, restored.view)
        assertEquals("2036", restored.yearText.text.toString())
        assertEquals(TextRange(1, 3), restored.yearText.selection)
        restored.applyYear()
        assertEquals(YearMonth(2036, 11), restored.month)
    }

    @Test
    fun `year and month can be changed independently`() {
        val state = DatePickerState(YearMonth(2026, 11))
        state.toggleYears()
        assertEquals(2020, state.firstVisibleYear)
        assertEquals(2039, state.lastVisibleYear)
        state.chooseYear(2036)
        assertEquals(YearMonth(2036, 11), state.month)
        assertEquals(DatePickerState.View.Days, state.view)
        state.toggleMonths()
        state.chooseMonth(6)
        assertEquals(YearMonth(2036, 6), state.month)
        state.showToday(YearMonth(2026, 11))
        assertEquals(YearMonth(2026, 11), state.month)
    }

    @Test
    fun `invalid input never changes the browsing month`() {
        val state = DatePickerState(YearMonth(2026, 2))
        state.toggleYears()
        for (input in listOf("", "0", "-1", "10000", "abc", "999999999999")) {
            state.editYear(input)
            assertFalse(state.yearError)
            state.applyYear()
            assertTrue(state.yearError, input)
            assertEquals(YearMonth(2026, 2), state.month)
            assertEquals(DatePickerState.View.Years, state.view)
        }
        state.editYear("2036")
        state.applyYear()
        assertFalse(state.yearError)
        assertEquals(YearMonth(2036, 2), state.month)
        assertEquals(29, state.month.numberOfDays)
        state.chooseYear(2037)
        assertEquals(28, state.month.numberOfDays)
    }

    @Test
    fun `year ranges move twenty years and stop at supported boundaries`() {
        val state = DatePickerState(YearMonth(2026, 11))
        state.moveYearRange(true)
        assertEquals(2040, state.firstVisibleYear)
        assertEquals(2059, state.lastVisibleYear)
        state.moveYearRange(false)
        assertEquals(2020, state.firstVisibleYear)
        state.chooseYear(1)
        state.toggleYears()
        state.moveYearRange(false)
        assertFalse(state.canShowPreviousYears)
        assertEquals(1, state.firstVisibleYear)
        state.chooseYear(9999)
        state.toggleYears()
        state.moveYearRange(true)
        assertFalse(state.canShowNextYears)
        assertEquals(9980, state.firstVisibleYear)
        assertEquals(9999, state.lastVisibleYear)
    }

    @Test
    fun `month navigation crosses years without overflowing the supported range`() {
        val state = DatePickerState(YearMonth(2026, 12))
        state.moveMonth(true)
        assertEquals(YearMonth(2027, 1), state.month)
        state.moveMonth(false)
        assertEquals(YearMonth(2026, 12), state.month)
        state.chooseYear(1)
        state.chooseMonth(1)
        assertFalse(state.canGoBack)
        state.moveMonth(false)
        assertEquals(YearMonth(1, 1), state.month)
        state.chooseYear(9999)
        state.chooseMonth(12)
        assertFalse(state.canGoForward)
        state.moveMonth(true)
        assertEquals(YearMonth(9999, 12), state.month)
    }
}
