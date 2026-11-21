package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkulikov.taskb.feature.calendar.component.CalendarQuickDeleteButton
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal val calendarTaskPadding = 2.dp
internal val calendarTaskCheckboxSize = 20.dp
internal val calendarTaskTitleGap = 6.dp
internal val calendarTaskTitleInset = calendarTaskPadding + calendarTaskCheckboxSize + calendarTaskTitleGap

internal fun calendarSidebarTaskTitleGap(task: CalendarItem) = if (task.isRecurring) 4.dp else 6.dp

@Composable
internal fun calendarTaskTitleStyle(dense: Boolean): TextStyle {
    return (if (dense) {
        TaskBTheme.typography.button.copy(lineHeight = 17.sp)
    } else {
        TaskBTheme.typography.body.copy(lineHeight = 18.sp)
    })
        .copy(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            )
        )
}

@Composable
internal fun CalendarTaskRow(
    task: CalendarItem,
    group: Group?,
    onOpenTask: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    enabled: Boolean = true,
    dragging: Boolean = false,
    date: LocalDate? = task.dueDate,
    now: Instant = Clock.System.now(),
    today: LocalDate = now.toLocalDateTime(timeZone = TimeZone.currentSystemDefault()).date,
    contentPadding: PaddingValues = PaddingValues(all = 0.dp),
    compactDay: Boolean = false,
    onQuickDelete: (() -> Unit)? = null,
) {
    if (task is CalendarItem.HolidayItem) {
        CalendarHolidayRow(
            holiday = task.holiday,
            modifier = modifier,
            dense = dense,
            contentPadding = contentPadding,
            today = today,
        )
        return
    }
    if (task is CalendarItem.EventItem) {
        CalendarEventRow(
            event = task.event,
            group = group,
            onOpenEvent = onOpenTask,
            modifier = modifier,
            dense = dense,
            enabled = enabled,
            dragging = dragging,
            date = date,
            now = now,
            contentPadding = contentPadding,
            compactDay = compactDay,
        )
        return
    }
    val colors = TaskBTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
    val titleStyle = if (compactDay) {
        TaskBTheme.typography.body.copy(lineHeightStyle = lineHeightStyle)
    } else {
        calendarTaskTitleStyle(dense = dense)
    }
    val completedColor = if (compactDay) colors.calendarInactiveEntryText else colors.completedText
    val overdue = !task.isCompleted && task.dueDate?.let { date -> date < today } == true
    Column(modifier = modifier.graphicsLayer { alpha = if (dragging) 0.25f else 1f }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = if (dense) RoundedCornerShape(size = 8.dp) else RectangleShape)
                .then(
                    other = if (overdue) Modifier.background(color = colors.overdueTaskBackground) else Modifier
                )
                .hoverable(interactionSource = interactionSource, enabled = enabled)
                .indication(interactionSource = interactionSource, indication = LocalIndication.current)
                .padding(paddingValues = contentPadding)
                .then(
                    other = if (dense) {
                        Modifier.padding(all = calendarTaskPadding)
                    } else {
                        Modifier.padding(vertical = if (compactDay) 0.dp else 4.dp)
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = when {
                    dense -> calendarTaskTitleGap
                    compactDay -> 10.dp
                    else -> calendarSidebarTaskTitleGap(task = task)
                }
            ),
        ) {
            Box(
                modifier =
                    (if (dense) {
                        Modifier.size(size = calendarTaskCheckboxSize)
                    } else {
                        Modifier.width(width = if (compactDay) 22.dp else 24.dp)
                    })
                        .toggleable(
                            value = task.isCompleted,
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onToggle(task.id) },
                        )
                        .semantics {
                            contentDescription =
                                if (task.isCompleted) "Mark ${task.name} incomplete" else "Complete ${task.name}"
                        }
                        .then(other = if (dense || compactDay) Modifier else Modifier.padding(vertical = 6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (task.isCompleted) TaskBIcons.Checked else TaskBIcons.Unchecked,
                    contentDescription = null,
                    tint = if (dense && group != null) {
                        group.color.color
                    } else if (task.isCompleted) {
                        colors.accent
                    } else {
                        colors.muted
                    },
                    // Crop the icon's empty 3 dp margins without scaling its 18 dp glyph.
                    modifier = when {
                        dense -> Modifier.size(size = calendarTaskCheckboxSize)
                        compactDay -> Modifier.size(size = 22.dp)
                        else -> Modifier
                            .size(width = 24.dp, height = 18.dp)
                            .clipToBounds()
                            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                            .requiredSize(size = 24.dp)
                            .offset(x = (-3).dp, y = (-3).dp)
                    },
                )
            }
            Column(
                modifier = Modifier.weight(weight = 1f),
                verticalArrangement = Arrangement.spacedBy(
                    space = when {
                        dense -> 4.dp
                        compactDay -> 0.dp
                        else -> 2.dp
                    },
                ),
            ) {
                Text(
                    text = task.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                        ) {
                            onOpenTask(task.id)
                        },
                    color = if (task.isCompleted) completedColor else colors.text,
                    style = titleStyle,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!dense && task.description.isNotBlank())
                    Text(
                        text = task.description,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = enabled,
                                role = Role.Button,
                            ) {
                                onOpenTask(task.id)
                            },
                        color = colors.muted,
                        style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeightStyle),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                if (!dense && group != null)
                    Row(
                        modifier = if (compactDay) Modifier.height(height = 20.dp) else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
                    ) {
                        Icon(
                            imageVector = TaskBIcons.FolderFilled,
                            contentDescription = null,
                            tint = group.color.color,
                            modifier = Modifier.size(size = 14.dp),
                        )
                        Text(
                            text = group.name,
                            color = colors.muted,
                            style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeightStyle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
            if (onQuickDelete != null) {
                CalendarQuickDeleteButton(
                    taskName = task.name,
                    enabled = enabled,
                    onClick = onQuickDelete,
                    modifier = Modifier.align(alignment = Alignment.Top),
                )
            }
        }
    }
}
