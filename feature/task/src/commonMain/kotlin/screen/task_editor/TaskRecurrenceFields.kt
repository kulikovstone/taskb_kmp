package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
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
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.input.digitsOnlyInputTransformation
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

private val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val monthNames = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
)

internal fun TaskRecurrence.description(): String {
    val period = when (frequency) {
        RecurrenceFrequency.Daily -> "day"
        RecurrenceFrequency.Weekly -> "week"
        RecurrenceFrequency.Monthly -> "month"
        RecurrenceFrequency.Yearly -> "year"
    }
    val every = if (interval == 1) "Every $period" else "Every $interval ${period}s"
    return every +
        when (frequency) {
            RecurrenceFrequency.Daily -> ""
            RecurrenceFrequency.Weekly -> {
                val days = weekdays.sortedBy { weekday ->
                    weekday.ordinal
                }.map { weekday -> weekdayNames[weekday.ordinal] }
                " on " +
                    if (days.size <= 2) {
                        days.joinToString(separator = " and ")
                    } else {
                        days.dropLast(n = 1).joinToString(separator = ", ") + " and " + days.last()
                    }
            }
            RecurrenceFrequency.Monthly -> " on day $dayOfMonth"
            RecurrenceFrequency.Yearly -> " on ${monthNames[month - 1]} $dayOfMonth"
        }
}

@Composable
internal fun TaskRepeatPattern(
    state: TaskEditorState,
    onIntent: (TaskEditorIntent) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val date = state.dueDate ?: return
    val rule = state.editedRecurrence
    fun change(value: TaskRecurrence?) = onIntent(TaskEditorIntent.RecurrenceChanged(recurrence = value))
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Repeat",
            modifier = Modifier.height(height = 18.dp),
            style = TaskBTheme.typography.label,
            color = TaskBTheme.colors.text,
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        TaskSelectionField(
            label = rule?.description() ?: "Does not repeat",
            icon = TaskBIcons.Repeat,
            tint = TaskBTheme.colors.muted,
            placeholder = rule == null,
            description = "Choose repeat pattern",
            enabled = enabled,
            onClick = { if (rule == null) change(value = TaskRecurrence.monthly(date = date)) },
            onClear = rule?.let { { change(value = null) } },
            clearDescription = "Clear repeat pattern",
        )
        if (rule != null) {
            Spacer(modifier = Modifier.height(height = 12.dp))
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                ) {
                    RecurrenceFrequency.entries.forEach { frequency ->
                        TaskBSelectionButton(
                            label = frequency.name,
                            selected = rule.frequency == frequency,
                            onClick = {
                                change(
                                    value = rule.copy(
                                        frequency = frequency,
                                        weekdays = rule.weekdays.ifEmpty { setOf(element = date.dayOfWeek) },
                                        dayOfMonth = if (rule.frequency == frequency) rule.dayOfMonth else date.day,
                                        month = if (rule.frequency == frequency) rule.month else date.month.ordinal + 1,
                                    )
                                )
                            },
                            modifier = Modifier.weight(weight = 1f),
                            enabled = enabled,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
                ) {
                    Text(
                        text = "Every",
                        modifier = Modifier.width(width = 46.dp),
                        style = TaskBTheme.typography.body,
                        color = TaskBTheme.colors.text,
                    )
                    RepeatNumber(
                        state = state.repeatInterval,
                        description = "Repeat interval",
                        maxLength = 4,
                        enabled = enabled,
                    )
                    val unit = when (rule.frequency) {
                        RecurrenceFrequency.Daily -> "day"
                        RecurrenceFrequency.Weekly -> "week"
                        RecurrenceFrequency.Monthly -> "month"
                        RecurrenceFrequency.Yearly -> "year"
                    }
                    Text(
                        text = unit + if (rule.interval == 1) "" else "s",
                        style = TaskBTheme.typography.body,
                        color = TaskBTheme.colors.text,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaskRepeatOn(
    state: TaskEditorState,
    onIntent: (TaskEditorIntent) -> Unit,
    enabled: Boolean,
    wide: Boolean,
    modifier: Modifier = Modifier,
    showHeading: Boolean = false,
) {
    val rule = state.editedRecurrence ?: return
    fun change(value: TaskRecurrence) = onIntent(TaskEditorIntent.RecurrenceChanged(recurrence = value))
    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeading && (rule.frequency == RecurrenceFrequency.Monthly ||
                rule.frequency == RecurrenceFrequency.Yearly)
        ) {
            Text(
                text = "Repeat on",
                modifier = Modifier.height(height = 18.dp),
                style = TaskBTheme.typography.label,
                color = TaskBTheme.colors.text,
            )
            Spacer(modifier = Modifier.height(height = 8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(space = 16.dp)) {
            when (rule.frequency) {
                RecurrenceFrequency.Weekly -> Column(verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                    Text(
                        text = "Repeat on",
                        style = TaskBTheme.typography.label,
                        color = TaskBTheme.colors.text,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(space = if (wide) 8.dp else 4.dp),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            TaskBSelectionButton(
                                label = weekdayNames[day.ordinal],
                                selected = day in rule.weekdays,
                                onClick = {
                                    val next = if (day in rule.weekdays) {
                                        rule.weekdays - day
                                    } else {
                                        rule.weekdays + day
                                    }
                                    if (next.isNotEmpty()) change(value = rule.copy(weekdays = next))
                                },
                                modifier = Modifier.weight(weight = 1f),
                                enabled = enabled,
                            )
                        }
                    }
                }
                RecurrenceFrequency.Monthly,
                RecurrenceFrequency.Yearly -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
                    ) {
                        Text(
                            text = "On",
                            modifier = Modifier.width(width = 46.dp),
                            style = TaskBTheme.typography.body,
                            color = TaskBTheme.colors.text,
                        )
                        if (rule.frequency == RecurrenceFrequency.Yearly)
                            RepeatMonth(
                                month = rule.month,
                                enabled = enabled,
                                onChoose = { month -> change(value = rule.copy(month = month)) },
                                modifier =
                                    if (wide) Modifier.width(width = 180.dp) else Modifier.weight(weight = 1f),
                            )
                        RepeatNumber(
                            state = state.repeatDay,
                            description = "Repeat day",
                            maxLength = 2,
                            enabled = enabled,
                        )
                        if (rule.frequency == RecurrenceFrequency.Monthly)
                            Text(
                                text = "of the month",
                                style = TaskBTheme.typography.body,
                                color = TaskBTheme.colors.text,
                            )
                    }
                    if (rule.dayOfMonth > 28)
                        Text(
                            text = "If this date is missing, use the last day of the month.",
                            style = TaskBTheme.typography.small,
                            color = TaskBTheme.colors.muted,
                        )
                }
                RecurrenceFrequency.Daily -> Unit
            }
            val dates = state.upcomingDates
            Text(
                text = if (dates.isEmpty()) {
                    "No occurrences match this schedule."
                } else {
                    "Upcoming: " + upcomingLabel(dates = dates, frequency = rule.frequency)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = TaskBTheme.colors.selected, shape = RoundedCornerShape(size = 8.dp))
                    .padding(all = 12.dp),
                style = TaskBTheme.typography.small,
                color = TaskBTheme.colors.secondary,
            )
        }
        state.recurrenceError?.let { message ->
            Text(
                text = message,
                style = TaskBTheme.typography.small,
                color = TaskBTheme.colors.danger,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun TaskRepeatEnd(
    state: TaskEditorState,
    onIntent: (TaskEditorIntent) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val rule = state.editedRecurrence ?: return
    val date = state.dueDate ?: return
    fun change(value: TaskRecurrence) = onIntent(TaskEditorIntent.RecurrenceChanged(recurrence = value))
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Repeat ends",
            modifier = Modifier.height(height = 18.dp),
            style = TaskBTheme.typography.label,
            color = TaskBTheme.colors.text,
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        val end = when {
            rule.endDate != null -> "On date"
            rule.occurrenceCount != null -> "After"
            else -> "Never"
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            listOf("Never", "On date", "After").forEach { label ->
                TaskBSelectionButton(
                    label = label,
                    selected = end == label,
                    onClick = {
                        change(
                            value = rule.copy(
                                endDate = if (label == "On date") rule.endDate ?: date else null,
                                occurrenceCount = if (label == "After") rule.occurrenceCount ?: 10 else null,
                            )
                        )
                    },
                    modifier = Modifier.weight(weight = 1f),
                    enabled = enabled,
                )
            }
        }
        val endDate = rule.endDate
        if (endDate != null) {
            Spacer(modifier = Modifier.height(height = 8.dp))
            TaskSelectionField(
                label = shortDate(date = endDate),
                icon = TaskBIcons.Calendar,
                tint = TaskBTheme.colors.muted,
                placeholder = false,
                description = "Choose repeat end date",
                enabled = enabled,
                onClick = { onIntent(TaskEditorIntent.ChooseRepeatEndDate) },
                onClear = null,
                clearDescription = "",
            )
        } else if (rule.occurrenceCount != null) {
            Spacer(modifier = Modifier.height(height = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
            ) {
                RepeatNumber(
                    state = state.repeatCount,
                    description = "Repeat occurrence count",
                    maxLength = 7,
                    enabled = enabled,
                )
                Text(text = "occurrences", style = TaskBTheme.typography.body, color = TaskBTheme.colors.text)
            }
            Text(
                text = "Includes the first occurrence.",
                style = TaskBTheme.typography.small,
                color = TaskBTheme.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RepeatNumber(state: TextFieldState, description: String, maxLength: Int, enabled: Boolean) {
    val transformation = remember(key1 = maxLength) { digitsOnlyInputTransformation(maxLength = maxLength) }
    TaskBTextField(
        state = state,
        description = description,
        modifier = Modifier.width(width = 72.dp),
        enabled = enabled,
        showClear = false,
        keyboardType = KeyboardType.Number,
        inputTransformation = transformation,
    )
}

@Composable
private fun RepeatMonth(month: Int, enabled: Boolean, onChoose: (Int) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(value = false) }
    Box(modifier = modifier) {
        val shape = RoundedCornerShape(size = 8.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height = 44.dp)
                .clip(shape = shape)
                .border(width = 1.dp, color = TaskBTheme.colors.border, shape = shape)
                .clickable(enabled = enabled, role = Role.Button) { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = monthNames[month - 1],
                modifier = Modifier.weight(weight = 1f),
                style = TaskBTheme.typography.body,
                color = TaskBTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = TaskBIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(size = 18.dp),
                tint = TaskBTheme.colors.muted,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            monthNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    onClick = {
                        open = false
                        onChoose(index + 1)
                    }
                ) {
                    Text(text = name)
                }
            }
        }
    }
}

private fun shortDate(date: LocalDate) = "${monthNames[date.month.ordinal].take(n = 3)} ${date.day}, ${date.year}"

private fun upcomingLabel(dates: List<LocalDate>, frequency: RecurrenceFrequency): String {
    if (frequency == RecurrenceFrequency.Yearly || dates.map(transform = LocalDate::year).distinct().size > 1)
        return dates.joinToString(separator = " · ", transform = ::shortDate)
    return dates
        .mapIndexed { index, date ->
            if (index > 0 && dates[index - 1].month == date.month) {
                date.day.toString()
            } else {
                "${monthNames[date.month.ordinal].take(n = 3)} ${date.day}"
            }
        }
        .joinToString(separator = ", ")
}
