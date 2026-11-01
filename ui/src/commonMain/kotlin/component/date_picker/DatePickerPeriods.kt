package com.mkulikov.taskb.ui.component.date_picker

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.format.MonthNames

@Composable
internal fun DatePickerMonths(state: DatePickerState) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val names = if (maxWidth < 280.dp) MonthNames.ENGLISH_ABBREVIATED else MonthNames.ENGLISH_FULL
        Column(Modifier.fillMaxWidth().heightIn(min = 276.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose a month in ${state.month.year}", color = TaskBTheme.colors.muted, style = TaskBTheme.typography.body)
            repeat(4) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { column ->
                        val index = row * 3 + column
                        TaskBSelectionButton(names.names[index], index == state.month.month.ordinal, { state.chooseMonth(index + 1) },
                            Modifier.weight(1f).semantics { contentDescription = "Choose ${MonthNames.ENGLISH_FULL.names[index]} ${state.month.year}" })
                    }
                }
            }
        }
    }
}

@Composable
internal fun DatePickerYears(state: DatePickerState) {
    Column(Modifier.fillMaxWidth().heightIn(min = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskBTextField(state.yearText, state::editYear, "Year", Modifier.weight(1f), showClear = false, keyboardType = KeyboardType.Number, onDone = state::applyYear)
            TaskBSelectionButton("Go", false, state::applyYear, Modifier.width(64.dp))
        }
        if (state.yearError) Text("Enter a year between 1 and 9999.", color = TaskBTheme.colors.danger, style = TaskBTheme.typography.small)
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            DatePickerArrow("Previous 20 years", TaskBIcons.ChevronLeft, { state.moveYearRange(false) }, state.canShowPreviousYears, height = 40.dp)
            Text("${state.firstVisibleYear}–${state.lastVisibleYear}", Modifier.weight(1f).wrapContentWidth(), style = TaskBTheme.typography.section, color = TaskBTheme.colors.text)
            DatePickerArrow("Next 20 years", TaskBIcons.ChevronRight, { state.moveYearRange(true) }, state.canShowNextYears, height = 40.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { column ->
                        val year = state.yearRangeStart + row * 4 + column
                        if (year in DatePickerState.FirstYear..DatePickerState.LastYear) {
                            TaskBSelectionButton(year.toString(), year == state.month.year, { state.chooseYear(year) },
                                Modifier.weight(1f).semantics { contentDescription = "Choose year $year" })
                        } else Spacer(Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
    }
}
