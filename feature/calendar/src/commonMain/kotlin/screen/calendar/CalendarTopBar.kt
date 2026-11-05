package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun CalendarTopBar(state: CalendarState, onIntent: (CalendarIntent) -> Unit, onBack: () -> Unit, onCreateTask: () -> Unit) {
    val colors = TaskBTheme.colors
    Row(Modifier.fillMaxWidth().height(96.dp).padding(start = 16.dp, end = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack, Modifier.size(48.dp)) { Icon(TaskBIcons.Back, "Back", tint = colors.heading) }
        Spacer(Modifier.width(8.dp))
        Text(calendarMonthLabel.format(state.month), modifier = Modifier.weight(1f).offset(y = (-2).dp), style = TaskBTheme.typography.display,
            color = colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Triple(TaskBIcons.ChevronLeft, "Previous month", CalendarIntent.PreviousMonth), Triple(TaskBIcons.ChevronRight, "Next month", CalendarIntent.NextMonth)).forEach { (icon, description, intent) ->
                OutlinedButton({ onIntent(intent) }, Modifier.size(40.dp), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.border), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.outlinedButtonColors(backgroundColor = colors.surface)) {
                    Icon(icon, description, tint = colors.secondary, modifier = Modifier.size(18.dp))
                }
            }
            OutlinedButton({ onIntent(CalendarIntent.Today) }, Modifier.width(72.dp).height(40.dp).testTag("calendar-today"), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.border), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.outlinedButtonColors(backgroundColor = colors.surface)) {
                Text("Today", style = TaskBTheme.typography.button, color = colors.accent)
            }
            OutlinedButton({}, Modifier.width(116.dp).height(40.dp).testTag("calendar-choose-date"), enabled = false,
                shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.border), contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = colors.surface, disabledContentColor = colors.muted)) {
                Text("Choose date", style = TaskBTheme.typography.button)
            }
        }
        Spacer(Modifier.width(16.dp))
        TaskBEditorButton("New task", TaskBIcons.Add, onCreateTask)
    }
}
