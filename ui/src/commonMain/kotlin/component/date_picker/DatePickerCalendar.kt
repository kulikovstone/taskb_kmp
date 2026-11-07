package com.mkulikov.taskb.ui.component.date_picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.onDay

private val dateDescription = LocalDate.Format {
    monthName(names = MonthNames.ENGLISH_FULL)
    chars(value = " ")
    day(padding = Padding.NONE)
    chars(value = ", ")
    year()
}

@Composable
internal fun DatePickerCalendar(
    month: YearMonth,
    date: LocalDate?,
    today: LocalDate,
    onChoose: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(height = 24.dp)) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { weekday ->
                Text(
                    text = weekday,
                    color = TaskBTheme.colors.muted,
                    style = TaskBTheme.typography.small,
                    modifier = Modifier.weight(weight = 1f).fillMaxHeight().wrapContentSize(),
                )
            }
        }
        val offset = month.firstDay.dayOfWeek.isoDayNumber - 1
        repeat(times = 6) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(times = 7) { day ->
                    val number = week * 7 + day - offset + 1
                    BoxWithConstraints(
                        modifier = Modifier.weight(weight = 1f).height(height = 42.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (number in 1..month.numberOfDays) {
                            val value = month.onDay(day = number)
                            val chosen = value == date
                            Box(
                                modifier = Modifier
                                    .size(size = minOf(a = 36.dp, b = maxWidth))
                                    .clip(shape = CircleShape)
                                    .then(
                                        other = if (chosen) {
                                            Modifier.background(color = TaskBTheme.colors.selected)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .then(
                                        other = if (value == today) {
                                            Modifier.border(
                                                width = 1.dp,
                                                color = TaskBTheme.colors.accent,
                                                shape = CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable(role = Role.Button) { onChoose(value) }
                                    .semantics {
                                        selected = chosen
                                        contentDescription = "Choose ${dateDescription.format(value = value)}"
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = number.toString(),
                                    color = if (chosen) TaskBTheme.colors.accent else TaskBTheme.colors.text,
                                    style = TaskBTheme.typography.body,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
