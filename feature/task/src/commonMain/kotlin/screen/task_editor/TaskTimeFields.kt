package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.isInputValid
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.dialog.TaskBDialog
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Composable
internal fun TaskTimeFields(state: TaskEditorState, onIntent: (TaskEditorIntent) -> Unit, enabled: Boolean, wide: Boolean) {
    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) { TaskStartTimeField(state, onIntent, enabled) }
        Column(Modifier.weight(1f)) { if (state.startTime != null) TaskDurationFields(state, onIntent, enabled) }
    } else {
        TaskStartTimeField(state, onIntent, enabled)
        if (state.startTime != null) {
            Spacer(Modifier.height(24.dp))
            TaskDurationFields(state, onIntent, enabled)
        }
    }
}

@Composable
private fun TaskStartTimeField(state: TaskEditorState, onIntent: (TaskEditorIntent) -> Unit, enabled: Boolean) {
    Text("Start time", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text)
    Spacer(Modifier.height(8.dp))
    TaskSelectionField(state.startTime?.let(::taskTimeLabel) ?: "Choose time", TaskBIcons.Schedule, TaskBTheme.colors.muted,
        state.startTime == null, "Choose start time", enabled,
        { onIntent(TaskEditorIntent.ChooseStartTime) }, state.startTime?.let { { onIntent(TaskEditorIntent.StartTimeChanged(null)) } }, "Clear start time")
    Spacer(Modifier.height(8.dp))
    Text("24-hour time", style = TaskBTheme.typography.small, color = TaskBTheme.colors.muted)
}

@Composable
private fun TaskDurationFields(state: TaskEditorState, onIntent: (TaskEditorIntent) -> Unit, enabled: Boolean) {
    val colors = TaskBTheme.colors
    Text("Duration", Modifier.fillMaxWidth().height(18.dp), style = TaskBTheme.typography.label, color = colors.text)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskBSelectionButton("15 min", !state.customDuration && state.durationMinutes == 15,
            { onIntent(TaskEditorIntent.DurationChanged(15)) }, Modifier.weight(1f), enabled)
        TaskBSelectionButton("30 min", !state.customDuration && state.durationMinutes == 30,
            { onIntent(TaskEditorIntent.DurationChanged(30)) }, Modifier.weight(1f), enabled)
        TaskBSelectionButton("1 hour", !state.customDuration && state.durationMinutes == 60,
            { onIntent(TaskEditorIntent.DurationChanged(60)) }, Modifier.weight(1f), enabled)
        TaskBSelectionButton("Custom", state.customDuration,
            { onIntent(TaskEditorIntent.ChooseCustomDuration) }, Modifier.weight(1f), enabled)
    }
    if (state.customDuration) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskBSelectionButton("Duration", state.durationMode == TaskDurationMode.Duration,
                { onIntent(TaskEditorIntent.DurationModeChanged(TaskDurationMode.Duration)) }, Modifier.weight(1f), enabled)
            TaskBSelectionButton("End date & time", state.durationMode == TaskDurationMode.EndDateTime,
                { onIntent(TaskEditorIntent.DurationModeChanged(TaskDurationMode.EndDateTime)) }, Modifier.weight(1f), enabled)
        }
        Spacer(Modifier.height(16.dp))
        if (state.durationMode == TaskDurationMode.Duration) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DurationNumber("Days", state.durationDays, "Duration days", { onIntent(TaskEditorIntent.DurationInputChanged(days = it)) }, enabled, Modifier.weight(1f))
            DurationNumber("Hours", state.durationHours, "Duration hours", { onIntent(TaskEditorIntent.DurationInputChanged(hours = it)) }, enabled, Modifier.weight(1f))
            DurationNumber("Minutes", state.durationMinutePart, "Duration minutes", { onIntent(TaskEditorIntent.DurationInputChanged(minutes = it)) }, enabled, Modifier.weight(1f))
        } else {
            Text("End date", style = TaskBTheme.typography.label, color = colors.text)
            Spacer(Modifier.height(8.dp))
            TaskSelectionField(state.endDate?.let(::taskTimingDateLabel) ?: "Choose end date", TaskBIcons.Calendar, colors.muted,
                state.endDate == null, "Choose task end date", enabled, { onIntent(TaskEditorIntent.ChooseTaskEndDate) }, null, "")
            Spacer(Modifier.height(16.dp))
            Text("End time", style = TaskBTheme.typography.label, color = colors.text)
            Spacer(Modifier.height(8.dp))
            TaskSelectionField(state.endTime?.let(::taskTimeLabel) ?: "Choose end time", TaskBIcons.Schedule, colors.muted,
                state.endTime == null, "Choose end time", enabled, { onIntent(TaskEditorIntent.ChooseEndTime) }, null, "")
        }
    }
    Spacer(Modifier.height(8.dp))
    val error = state.timingError
    val end = state.ending
    when {
        error != null -> Text(error, style = TaskBTheme.typography.small, color = colors.danger)
        end != null && state.customDuration -> Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(colors.selected, RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(taskDurationLabel(requireNotNull(state.editedDurationMinutes)), style = TaskBTheme.typography.button, color = colors.text)
            Text("${taskTimingDateLabel(requireNotNull(state.dueDate))} · ${taskTimeLabel(requireNotNull(state.startTime))}–${taskTimingDateLabel(end.date)} · ${taskTimeLabel(end.time)}",
                style = TaskBTheme.typography.small, color = colors.secondary)
        }
        end != null -> Text(if (end.date == state.dueDate) "Ends at ${taskTimeLabel(end.time)} · same day" else "Ends ${taskTimingDateLabel(end.date)} · ${taskTimeLabel(end.time)}",
            style = TaskBTheme.typography.small, color = colors.muted)
    }
}

@Composable
private fun DurationNumber(label: String, value: String, description: String, onChange: (String) -> Unit, enabled: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = TaskBTheme.typography.label, color = TaskBTheme.colors.muted)
        Spacer(Modifier.height(8.dp))
        TaskBTextField(value, onChange, description, Modifier.fillMaxWidth(), enabled = enabled, showClear = false, keyboardType = KeyboardType.Number)
    }
}

@Composable
internal fun TaskTimePickerDialog(title: String, initialTime: LocalTime?, availableHeight: Dp, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val colors = TaskBTheme.colors
    val type = TaskBTheme.typography
    val picker = rememberTimePickerState(initialHour = initialTime?.hour ?: 9, initialMinute = initialTime?.minute ?: 0, is24Hour = true)
    var keyboard by remember { mutableStateOf(availableHeight < 600.dp) }
    val typography = Typography().let { base ->
        val family = type.body.fontFamily
        base.copy(displayLarge = base.displayLarge.copy(fontFamily = family), displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family), headlineLarge = base.headlineLarge.copy(fontFamily = family),
            headlineMedium = base.headlineMedium.copy(fontFamily = family), headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family), titleMedium = base.titleMedium.copy(fontFamily = family), titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family), bodyMedium = base.bodyMedium.copy(fontFamily = family), bodySmall = base.bodySmall.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family), labelMedium = base.labelMedium.copy(fontFamily = family), labelSmall = base.labelSmall.copy(fontFamily = family))
    }
    TaskBDialog(onDismiss) {
        MaterialTheme(colorScheme = lightColorScheme(primary = colors.accent, onPrimary = colors.onAction, surface = colors.surface,
            onSurface = colors.text, onSurfaceVariant = colors.muted, surfaceVariant = colors.subtle, secondaryContainer = colors.selected,
            onSecondaryContainer = colors.text, outline = colors.border, error = colors.danger), typography = typography) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surface) {
                Column(Modifier.width(360.dp).heightIn(max = (availableHeight - 48.dp).coerceAtLeast(240.dp))
                    .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = type.section, color = colors.text)
                    val pickerColors = TimePickerDefaults.colors(clockDialColor = colors.subtle, clockDialSelectedContentColor = colors.onAction,
                        clockDialUnselectedContentColor = colors.text, selectorColor = colors.actionFill, containerColor = colors.surface,
                        periodSelectorBorderColor = colors.border, periodSelectorSelectedContainerColor = colors.selected,
                        periodSelectorUnselectedContainerColor = colors.surface, periodSelectorSelectedContentColor = colors.text,
                        periodSelectorUnselectedContentColor = colors.muted, timeSelectorSelectedContainerColor = colors.selected,
                        timeSelectorUnselectedContainerColor = colors.subtle, timeSelectorSelectedContentColor = colors.text,
                        timeSelectorUnselectedContentColor = colors.text)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (keyboard) TimeInput(picker, colors = pickerColors)
                        else TimePicker(picker,
                            // Isolate the dial's Clear/Xor blending from the dialog's render target.
                            modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                            colors = pickerColors, layoutType = TimePickerLayoutType.Vertical)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ keyboard = !keyboard }) {
                            Icon(if (keyboard) TaskBIcons.Schedule else TaskBIcons.Keyboard,
                                if (keyboard) "Switch to clock input" else "Switch to keyboard input", tint = colors.muted)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onDismiss) { Text("Cancel", color = colors.accent, style = type.button) }
                        TextButton({ onConfirm(LocalTime(picker.hour, picker.minute)) }, enabled = !keyboard || picker.isInputValid,
                            modifier = Modifier.semantics { contentDescription = "Apply time" }) {
                            Text("OK", color = if (!keyboard || picker.isInputValid) colors.accent else colors.muted, style = type.button)
                        }
                    }
                }
            }
        }
    }
}

internal fun taskTimeLabel(time: LocalTime): String = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

private val timingMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
internal fun taskTimingDateLabel(date: LocalDate): String = "${date.day} ${timingMonths[date.month.ordinal]} ${date.year}"

internal fun taskDurationLabel(minutes: Int): String = buildList {
    val days = minutes / 1440
    val hours = minutes % 1440 / 60
    val rest = minutes % 60
    if (days > 0) add("$days ${if (days == 1) "day" else "days"}")
    if (hours > 0) add("$hours hr")
    if (rest > 0) add("$rest min")
}.joinToString(" ")
