package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.calendar.component.CalendarDayContext
import com.mkulikov.taskb.feature.calendar.component.CalendarDayList
import com.mkulikov.taskb.feature.calendar.component.calendarSidebarTaskPadding
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun CalendarSidebar(
    state: CalendarState,
    drag: CalendarDragState,
    onIntent: (CalendarIntent) -> Unit,
    onOpenTask: (CalendarItem) -> Unit,
) {
    val date = state.selectedDate
    val colors = TaskBTheme.colors
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
    Column(modifier = Modifier.width(width = 320.dp).fillMaxHeight().testTag(tag = "calendar-sidebar")) {
        Row(
            modifier = Modifier
                .padding(paddingValues = calendarSidebarTaskPadding)
                .fillMaxWidth()
                .height(height = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = date?.let(block = calendarSidebarDateLabel::format) ?: "No date",
                modifier = Modifier.weight(weight = 1f).testTag(tag = "calendar-sidebar-title"),
                style = TaskBTheme.typography.section.copy(lineHeightStyle = lineHeightStyle),
                color = colors.heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = { onIntent(if (date == null) CalendarIntent.Today else CalendarIntent.ShowUnscheduled) },
                modifier = Modifier
                    .width(width = if (date == null) 76.dp else 110.dp)
                    .height(height = 36.dp)
                    .testTag(tag = "calendar-sidebar-toggle"),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = if (date == null) "Today" else "No date",
                    color = colors.accent,
                    style = TaskBTheme.typography.label,
                )
            }
        }
        Spacer(modifier = Modifier.height(height = 4.dp))
        CalendarDayContext(state = state, modifier = Modifier.padding(paddingValues = calendarSidebarTaskPadding))
        Spacer(modifier = Modifier.height(height = 16.dp))
        CalendarDayList(
            state = state,
            onIntent = onIntent,
            onOpenTask = onOpenTask,
            drag = drag,
            modifier = Modifier.weight(weight = 1f).fillMaxWidth(),
        )
    }
}
