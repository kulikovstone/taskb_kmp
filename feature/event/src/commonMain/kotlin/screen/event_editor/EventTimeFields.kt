package com.mkulikov.taskb.feature.event.screen.event_editor

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
import androidx.compose.material3.isInputValid
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.button.TaskBSelectionButton
import com.mkulikov.taskb.ui.dialog.TaskBDialog
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalTime

@Composable
internal fun EventStartTimeField(
    state: EventEditorState,
    onIntent: (EventEditorIntent) -> Unit,
    enabled: Boolean,
) {
    Text(text = "Start time", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text)
    Spacer(modifier = Modifier.height(height = 8.dp))
    EventSelectionField(
        label = state.startTime?.let(block = ::eventTimeLabel) ?: "Choose time",
        icon = TaskBIcons.Schedule,
        tint = TaskBTheme.colors.muted,
        placeholder = state.startTime == null,
        description = "Choose start time",
        enabled = enabled,
        onClick = { onIntent(EventEditorIntent.ChooseStartTime) },
        onClear = state.startTime?.let { { onIntent(EventEditorIntent.StartTimeChanged(time = null)) } },
        clearDescription = "Clear start time",
    )
    if (state.startTime != null && state.endDate == state.dueDate) {
        Spacer(modifier = Modifier.height(height = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            for ((minutes, label) in listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour")) {
                TaskBSelectionButton(
                    label = label,
                    selected = state.editedDurationMinutes == minutes,
                    onClick = { onIntent(EventEditorIntent.DurationChanged(minutes = minutes)) },
                    modifier = Modifier.weight(weight = 1f),
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
internal fun EventEndTimeField(state: EventEditorState, onIntent: (EventEditorIntent) -> Unit, enabled: Boolean) {
    Text(text = "End time", style = TaskBTheme.typography.label, color = TaskBTheme.colors.text)
    Spacer(modifier = Modifier.height(height = 8.dp))
    EventSelectionField(
        label = state.endTime?.let(block = ::eventTimeLabel) ?: "Choose end time",
        icon = TaskBIcons.Schedule,
        tint = TaskBTheme.colors.muted,
        placeholder = state.endTime == null,
        description = "Choose end time",
        enabled = enabled,
        onClick = { onIntent(EventEditorIntent.ChooseEndTime) },
        onClear = null,
        clearDescription = "",
    )
}

@Composable
internal fun EventTimePickerDialog(
    title: String,
    initialTime: LocalTime?,
    availableHeight: Dp,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = TaskBTheme.colors
    val type = TaskBTheme.typography
    val picker = rememberTimePickerState(
        initialHour = initialTime?.hour ?: 9,
        initialMinute = initialTime?.minute ?: 0,
        is24Hour = true,
    )
    var keyboard by remember { mutableStateOf(value = availableHeight < 600.dp) }
    val typography = Typography().let { base ->
        val family = type.body.fontFamily
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = family),
            displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.copy(fontFamily = family),
            headlineMedium = base.headlineMedium.copy(fontFamily = family),
            headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family),
            titleMedium = base.titleMedium.copy(fontFamily = family),
            titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family),
            bodyMedium = base.bodyMedium.copy(fontFamily = family),
            bodySmall = base.bodySmall.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family),
            labelMedium = base.labelMedium.copy(fontFamily = family),
            labelSmall = base.labelSmall.copy(fontFamily = family),
        )
    }
    TaskBDialog(onDismissRequest = onDismiss) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAction,
                surface = colors.surface,
                onSurface = colors.text,
                onSurfaceVariant = colors.muted,
                surfaceVariant = colors.subtle,
                secondaryContainer = colors.selected,
                onSecondaryContainer = colors.text,
                outline = colors.border,
                error = colors.danger,
            ),
            typography = typography,
        ) {
            Surface(shape = RoundedCornerShape(size = 16.dp), color = colors.surface) {
                Column(
                    modifier = Modifier
                        .width(width = 360.dp)
                        .heightIn(max = (availableHeight - 48.dp).coerceAtLeast(minimumValue = 240.dp))
                        .verticalScroll(state = rememberScrollState())
                        .padding(all = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(space = 16.dp),
                ) {
                    Text(text = title, style = type.section, color = colors.text)
                    val pickerColors = TimePickerDefaults.colors(
                        clockDialColor = colors.subtle,
                        clockDialSelectedContentColor = colors.onAction,
                        clockDialUnselectedContentColor = colors.text,
                        selectorColor = colors.actionFill,
                        containerColor = colors.surface,
                        periodSelectorBorderColor = colors.border,
                        periodSelectorSelectedContainerColor = colors.selected,
                        periodSelectorUnselectedContainerColor = colors.surface,
                        periodSelectorSelectedContentColor = colors.text,
                        periodSelectorUnselectedContentColor = colors.muted,
                        timeSelectorSelectedContainerColor = colors.selected,
                        timeSelectorUnselectedContainerColor = colors.subtle,
                        timeSelectorSelectedContentColor = colors.text,
                        timeSelectorUnselectedContentColor = colors.text,
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (keyboard) {
                            TimeInput(state = picker, colors = pickerColors)
                        } else {
                            TimePicker(
                                state = picker,
                                // Isolate the dial's Clear/Xor blending from the dialog's render target.
                                modifier = Modifier
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                                colors = pickerColors,
                                layoutType = TimePickerLayoutType.Vertical,
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { keyboard = !keyboard }) {
                            Icon(
                                imageVector = if (keyboard) TaskBIcons.Schedule else TaskBIcons.Keyboard,
                                contentDescription =
                                    if (keyboard) "Switch to clock input" else "Switch to keyboard input",
                                tint = colors.muted,
                            )
                        }
                        Spacer(modifier = Modifier.weight(weight = 1f))
                        TextButton(onClick = onDismiss) {
                            Text(text = "Cancel", color = colors.accent, style = type.button)
                        }
                        TextButton(
                            onClick = { onConfirm(LocalTime(hour = picker.hour, minute = picker.minute)) },
                            enabled = !keyboard || picker.isInputValid,
                            modifier = Modifier.semantics { contentDescription = "Apply time" },
                        ) {
                            Text(
                                text = "OK",
                                color = if (!keyboard || picker.isInputValid) colors.accent else colors.muted,
                                style = type.button,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun eventTimeLabel(time: LocalTime): String {
    return ("${time.hour.toString().padStart(length = 2, padChar = '0')}:" +
        time.minute.toString().padStart(length = 2, padChar = '0'))
}
