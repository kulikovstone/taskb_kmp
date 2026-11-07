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
internal fun CalendarTopBar(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    onBack: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateEvent: () -> Unit,
) {
    val colors = TaskBTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(height = 96.dp).padding(start = 16.dp, end = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(size = 48.dp)) {
            Icon(imageVector = TaskBIcons.Back, contentDescription = "Back", tint = colors.heading)
        }
        Spacer(modifier = Modifier.width(width = 8.dp))
        Text(
            text = calendarMonthLabel.format(value = state.month),
            modifier = Modifier.weight(weight = 1f).offset(y = (-2).dp),
            style = TaskBTheme.typography.display,
            color = colors.heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(width = 24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                    Triple(
                        first = TaskBIcons.ChevronLeft,
                        second = "Previous month",
                        third = CalendarIntent.PreviousMonth,
                    ),
                    Triple(
                        first = TaskBIcons.ChevronRight,
                        second = "Next month",
                        third = CalendarIntent.NextMonth,
                    ),
                )
                .forEach { (icon, description, intent) ->
                    OutlinedButton(
                        onClick = { onIntent(intent) },
                        modifier = Modifier.size(size = 40.dp),
                        shape = RoundedCornerShape(size = 8.dp),
                        border = BorderStroke(width = 1.dp, color = colors.border),
                        contentPadding = PaddingValues(all = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(backgroundColor = colors.surface),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = description,
                            tint = colors.secondary,
                            modifier = Modifier.size(size = 18.dp),
                        )
                    }
                }
            OutlinedButton(
                onClick = { onIntent(CalendarIntent.Today) },
                modifier = Modifier.width(width = 72.dp).height(height = 40.dp).testTag(tag = "calendar-today"),
                shape = RoundedCornerShape(size = 8.dp),
                border = BorderStroke(width = 1.dp, color = colors.border),
                contentPadding = PaddingValues(all = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = colors.surface),
            ) {
                Text(text = "Today", style = TaskBTheme.typography.button, color = colors.accent)
            }
            OutlinedButton(
                onClick = { onIntent(CalendarIntent.ChooseDate) },
                modifier = Modifier
                    .width(width = 116.dp)
                    .height(height = 40.dp)
                    .testTag(tag = "calendar-choose-date"),
                shape = RoundedCornerShape(size = 8.dp),
                border = BorderStroke(width = 1.dp, color = colors.border),
                contentPadding = PaddingValues(all = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = colors.surface),
            ) {
                Text(text = "Choose date", style = TaskBTheme.typography.button)
            }
        }
        Spacer(modifier = Modifier.width(width = 16.dp))
        TaskBEditorButton(label = "Task", icon = TaskBIcons.Add, onClick = onCreateTask)
        Spacer(modifier = Modifier.width(width = 8.dp))
        TaskBEditorButton(label = "Event", icon = TaskBIcons.Add, onClick = onCreateEvent)
    }
}
