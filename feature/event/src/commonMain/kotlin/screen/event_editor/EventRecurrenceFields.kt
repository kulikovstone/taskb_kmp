package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.task.api.model.RecurrenceFrequency
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

private val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

internal fun EventRecurrence.description(): String {
    val period = when (frequency) {
        RecurrenceFrequency.Daily -> "day"
        RecurrenceFrequency.Weekly -> "week"
        RecurrenceFrequency.Monthly -> "month"
        RecurrenceFrequency.Yearly -> "year"
    }
    val every = if (interval == 1) "Every $period" else "Every $interval ${period}s"
    return every + when (frequency) {
        RecurrenceFrequency.Daily -> ""
        RecurrenceFrequency.Weekly -> {
            val days = weekdays.sortedBy { it.ordinal }.map { weekdayNames[it.ordinal] }
            " on " + if (days.size <= 2) days.joinToString(" and ") else days.dropLast(1).joinToString(", ") + " and " + days.last()
        }
        RecurrenceFrequency.Monthly -> " on day $dayOfMonth"
        RecurrenceFrequency.Yearly -> " on ${monthNames[month - 1]} $dayOfMonth"
    }
}

@Composable
internal fun EventRecurrenceFields(state: EventEditorState, onIntent: (EventEditorIntent) -> Unit, enabled: Boolean, wide: Boolean) {
    val date = state.dueDate ?: return
    val rule = state.editedRecurrence
    fun change(value: EventRecurrence?) = onIntent(EventEditorIntent.RecurrenceChanged(value))
    Column(Modifier.fillMaxWidth()) {
        Text("Repeat", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text)
        Spacer(Modifier.height(8.dp))
        EventSelectionField(rule?.description() ?: "Does not repeat", TaskBIcons.Repeat, TaskBTheme.colors.muted, rule == null,
            "Choose repeat pattern", enabled, { if (rule == null) change(EventRecurrence.monthly(date)) },
            rule?.let { { change(null) } }, "Clear repeat pattern")
        if (rule != null) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurrenceFrequency.entries.forEach { frequency ->
                        TaskBSelectionButton(frequency.name, rule.frequency == frequency, {
                            change(rule.copy(frequency = frequency, weekdays = rule.weekdays.ifEmpty { setOf(date.dayOfWeek) },
                                dayOfMonth = if (rule.frequency == frequency) rule.dayOfMonth else date.day,
                                month = if (rule.frequency == frequency) rule.month else date.month.ordinal + 1))
                        }, Modifier.weight(1f), enabled)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Every", Modifier.width(46.dp), style = TaskBTheme.typography.body, color = TaskBTheme.colors.text)
                    RepeatNumber(state.repeatInterval, "Repeat interval", { onIntent(EventEditorIntent.RepeatIntervalChanged(it)) }, enabled)
                    val unit = when (rule.frequency) { RecurrenceFrequency.Daily -> "day"; RecurrenceFrequency.Weekly -> "week"; RecurrenceFrequency.Monthly -> "month"; RecurrenceFrequency.Yearly -> "year" }
                    Text(unit + if (rule.interval == 1) "" else "s", style = TaskBTheme.typography.body, color = TaskBTheme.colors.text)
                }
                when (rule.frequency) {
                    RecurrenceFrequency.Weekly -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Repeat on", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 4.dp)) {
                            DayOfWeek.entries.forEach { day ->
                                TaskBSelectionButton(weekdayNames[day.ordinal], day in rule.weekdays, {
                                    val next = if (day in rule.weekdays) rule.weekdays - day else rule.weekdays + day
                                    if (next.isNotEmpty()) change(rule.copy(weekdays = next))
                                }, Modifier.weight(1f), enabled)
                            }
                        }
                    }
                    RecurrenceFrequency.Monthly, RecurrenceFrequency.Yearly -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("On", Modifier.width(46.dp), style = TaskBTheme.typography.body, color = TaskBTheme.colors.text)
                            if (rule.frequency == RecurrenceFrequency.Yearly) RepeatMonth(rule.month, enabled, { change(rule.copy(month = it)) }, if (wide) Modifier.width(180.dp) else Modifier.weight(1f))
                            RepeatNumber(state.repeatDay, "Repeat day", { onIntent(EventEditorIntent.RepeatDayChanged(it)) }, enabled)
                            if (rule.frequency == RecurrenceFrequency.Monthly) Text("of the month", style = TaskBTheme.typography.body, color = TaskBTheme.colors.text)
                        }
                        if (rule.dayOfMonth > 28) Text("If this date is missing, use the last day of the month.", style = TaskBTheme.typography.small, color = TaskBTheme.colors.muted)
                    }
                    RecurrenceFrequency.Daily -> Unit
                }
                val dates = state.upcomingDates
                Text(if (dates.isEmpty()) "No occurrences match this schedule." else "Upcoming: " + upcomingLabel(dates, rule.frequency),
                    Modifier.fillMaxWidth().background(TaskBTheme.colors.selected, RoundedCornerShape(8.dp)).padding(12.dp),
                    style = TaskBTheme.typography.small, color = TaskBTheme.colors.secondary)
            }
            state.recurrenceError?.let { Text(it, style = TaskBTheme.typography.small, color = TaskBTheme.colors.danger, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Repeat ends", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text)
            Spacer(Modifier.height(8.dp))
            val end = when { rule.endDate != null -> "On date"; rule.occurrenceCount != null -> "After"; else -> "Never" }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Never", "On date", "After").forEach { label ->
                    TaskBSelectionButton(label, end == label, {
                        change(rule.copy(endDate = if (label == "On date") rule.endDate ?: date else null,
                            occurrenceCount = if (label == "After") rule.occurrenceCount ?: 10 else null))
                    }, Modifier.weight(1f), enabled)
                }
            }
            val endDate = rule.endDate
            if (endDate != null) {
                Spacer(Modifier.height(8.dp))
                EventSelectionField(shortDate(endDate), TaskBIcons.Calendar, TaskBTheme.colors.muted, false, "Choose repeat end date", enabled,
                    { onIntent(EventEditorIntent.ChooseRepeatEndDate) }, null, "")
            } else if (rule.occurrenceCount != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RepeatNumber(state.repeatCount, "Repeat occurrence count", { onIntent(EventEditorIntent.RepeatCountChanged(it)) }, enabled)
                    Text("occurrences", style = TaskBTheme.typography.body, color = TaskBTheme.colors.text)
                }
                Text("Includes the first occurrence.", style = TaskBTheme.typography.small, color = TaskBTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun RepeatNumber(value: String, description: String, onChange: (String) -> Unit, enabled: Boolean) {
    TaskBTextField(value, onChange, description, Modifier.width(72.dp), enabled = enabled, showClear = false, keyboardType = KeyboardType.Number)
}

@Composable
private fun RepeatMonth(month: Int, enabled: Boolean, onChoose: (Int) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val shape = RoundedCornerShape(8.dp)
        Row(Modifier.fillMaxWidth().height(44.dp).clip(shape).border(1.dp, TaskBTheme.colors.border, shape)
            .clickable(enabled, role = Role.Button) { open = true }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monthNames[month - 1], Modifier.weight(1f), style = TaskBTheme.typography.body, color = TaskBTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(TaskBIcons.ChevronRight, null, Modifier.size(18.dp), tint = TaskBTheme.colors.muted)
        }
        DropdownMenu(open, { open = false }) { monthNames.forEachIndexed { index, name -> DropdownMenuItem({ open = false; onChoose(index + 1) }) { Text(name) } } }
    }
}

private fun shortDate(date: LocalDate) = "${monthNames[date.month.ordinal].take(3)} ${date.day}, ${date.year}"

private fun upcomingLabel(dates: List<LocalDate>, frequency: RecurrenceFrequency): String {
    if (frequency == RecurrenceFrequency.Yearly || dates.map { it.year }.distinct().size > 1) return dates.joinToString(" · ", transform = ::shortDate)
    return dates.mapIndexed { index, date -> if (index > 0 && dates[index - 1].month == date.month) date.day.toString() else "${monthNames[date.month.ordinal].take(3)} ${date.day}" }.joinToString(", ")
}
