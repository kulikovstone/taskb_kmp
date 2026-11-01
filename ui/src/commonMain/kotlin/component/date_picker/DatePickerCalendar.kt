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
    monthName(MonthNames.ENGLISH_FULL)
    chars(" ")
    day(Padding.NONE)
    chars(", ")
    year()
}

@Composable
internal fun DatePickerCalendar(month: YearMonth, date: LocalDate?, today: LocalDate, onChoose: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(24.dp)) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach {
                Text(it, color = TaskBTheme.colors.muted, style = TaskBTheme.typography.small,
                    modifier = Modifier.weight(1f).fillMaxHeight().wrapContentSize())
            }
        }
        val offset = month.firstDay.dayOfWeek.isoDayNumber - 1
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { day ->
                    val number = week * 7 + day - offset + 1
                    BoxWithConstraints(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                        if (number in 1..month.numberOfDays) {
                            val value = month.onDay(number)
                            val chosen = value == date
                            Box(Modifier.size(minOf(36.dp, maxWidth)).clip(CircleShape)
                                .then(if (chosen) Modifier.background(TaskBTheme.colors.selected) else Modifier)
                                .then(if (value == today) Modifier.border(1.dp, TaskBTheme.colors.accent, CircleShape) else Modifier)
                                .clickable(role = Role.Button) { onChoose(value) }
                                .semantics { selected = chosen; contentDescription = "Choose ${dateDescription.format(value)}" }, contentAlignment = Alignment.Center) {
                                Text(number.toString(), color = if (chosen) TaskBTheme.colors.accent else TaskBTheme.colors.text, style = TaskBTheme.typography.body)
                            }
                        }
                    }
                }
            }
        }
    }
}
