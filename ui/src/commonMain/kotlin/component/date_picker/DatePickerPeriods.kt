package com.mkulikov.taskb.ui.component.date_picker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val names = if (maxWidth < 280.dp) MonthNames.ENGLISH_ABBREVIATED else MonthNames.ENGLISH_FULL
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 276.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            Text(
                text = "Choose a month in ${state.month.year}",
                color = TaskBTheme.colors.muted,
                style = TaskBTheme.typography.body,
            )
            repeat(times = 4) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                ) {
                    repeat(times = 3) { column ->
                        val index = row * 3 + column
                        TaskBSelectionButton(
                            label = names.names[index],
                            selected = index == state.month.month.ordinal,
                            onClick = { state.chooseMonth(number = index + 1) },
                            modifier = Modifier
                                .weight(weight = 1f)
                                .semantics {
                                    contentDescription =
                                        "Choose ${MonthNames.ENGLISH_FULL.names[index]} ${state.month.year}"
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DatePickerYears(state: DatePickerState) {
    val clearError = remember(key1 = state) { InputTransformation { state.clearYearError() } }
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            TaskBTextField(
                state = state.yearText,
                description = "Year",
                modifier = Modifier.weight(weight = 1f),
                showClear = false,
                keyboardType = KeyboardType.Number,
                onDone = state::applyYear,
                inputTransformation = clearError,
            )
            TaskBSelectionButton(
                label = "Go",
                selected = false,
                onClick = state::applyYear,
                modifier = Modifier.width(width = 64.dp),
            )
        }
        if (state.yearError)
            Text(
                text = "Enter a year between 1 and 9999.",
                color = TaskBTheme.colors.danger,
                style = TaskBTheme.typography.small,
            )
        Row(
            modifier = Modifier.fillMaxWidth().height(height = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DatePickerArrow(
                description = "Previous 20 years",
                icon = TaskBIcons.ChevronLeft,
                onClick = { state.moveYearRange(forward = false) },
                enabled = state.canShowPreviousYears,
                height = 40.dp,
            )
            Text(
                text = "${state.firstVisibleYear}–${state.lastVisibleYear}",
                modifier = Modifier.weight(weight = 1f).wrapContentWidth(),
                style = TaskBTheme.typography.section,
                color = TaskBTheme.colors.text,
            )
            DatePickerArrow(
                description = "Next 20 years",
                icon = TaskBIcons.ChevronRight,
                onClick = { state.moveYearRange(forward = true) },
                enabled = state.canShowNextYears,
                height = 40.dp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
            repeat(times = 5) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                ) {
                    repeat(times = 4) { column ->
                        val year = state.yearRangeStart + row * 4 + column
                        if (year in DatePickerState.FirstYear..DatePickerState.LastYear) {
                            TaskBSelectionButton(
                                label = year.toString(),
                                selected = year == state.month.year,
                                onClick = { state.chooseYear(year = year) },
                                modifier = Modifier
                                    .weight(weight = 1f)
                                    .semantics {
                                        contentDescription = "Choose year $year"
                                    },
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(weight = 1f).height(height = 44.dp))
                        }
                    }
                }
            }
        }
    }
}
