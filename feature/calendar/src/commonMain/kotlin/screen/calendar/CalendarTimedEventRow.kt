package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Composable
internal fun CalendarTimedEventRow(
    task: Event,
    group: Group?,
    onOpenTask: (Int) -> Unit,
    modifier: Modifier,
    dense: Boolean,
    enabled: Boolean,
    dragging: Boolean,
    date: LocalDate?,
    now: Instant,
    contentPadding: PaddingValues,
) {
    val colors = TaskBTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val subdued = task.isPastCalendarEvent(now)
    val titleColor = if (subdued) colors.completedText else colors.text
    val timeColor = if (subdued) colors.completedText else colors.taskTime
    val secondaryColor = if (subdued) colors.completedText else colors.muted
    val lineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    Row(
        modifier.graphicsLayer { alpha = if (dragging) 0.25f else 1f }
            .fillMaxWidth().height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(4.dp))
            .hoverable(interaction, enabled = enabled)
            .indication(interaction, LocalIndication.current)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) { onOpenTask(task.id) }
            .padding(contentPadding)
            .padding(top = 4.dp, bottom = 4.dp, end = if (dense) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 10.dp),
    ) {
        if (group != null) Box(
            Modifier.width(2.dp).fillMaxHeight()
                .then(if (dense) Modifier else Modifier.padding(vertical = 3.dp))
                .background(group.color.color).testTag("calendar-event-marker-${task.key}"),
        )
        if (dense) {
            val label = calendarMonthEventText(task, date)
            val text = buildAnnotatedString {
                append(label)
                val clockStart = when {
                    !task.isMultiDayCalendarEvent || date == null || date == task.dueDate -> 0
                    date == task.calendarLastDate -> label.length - 5
                    else -> null
                }
                if (clockStart != null) addStyle(SpanStyle(color = timeColor), clockStart, clockStart + 5)
            }
            Text(text, modifier = Modifier.weight(1f), color = titleColor,
                style = calendarTaskTitleStyle(dense = true), textDecoration = TextDecoration.None,
                maxLines = if (task.isMultiDayCalendarEvent) 1 else 2, overflow = TextOverflow.Ellipsis)
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val range = calendarEventTimeRange(task)
                val text = buildAnnotatedString {
                    append(range)
                    for (clock in Regex("\\b\\d{1,2}:\\d{2}\\b").findAll(range)) {
                        addStyle(SpanStyle(color = timeColor), clock.range.first, clock.range.last + 1)
                    }
                }
                Text(text, modifier = Modifier.fillMaxWidth().testTag("calendar-event-time-${task.key}"),
                    color = secondaryColor, style = TaskBTheme.typography.label.copy(lineHeightStyle = lineHeight),
                    textDecoration = TextDecoration.None)
                Text(task.name, color = titleColor, style = calendarTaskTitleStyle(dense = false),
                    textDecoration = TextDecoration.None, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (group != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(TaskBIcons.FolderFilled, null, tint = group.color.color, modifier = Modifier.size(14.dp))
                    Text(group.name, color = colors.muted, style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeight),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
