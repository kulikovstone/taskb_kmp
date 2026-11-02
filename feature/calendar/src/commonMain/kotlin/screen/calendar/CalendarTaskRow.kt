package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
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
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.startDateTime
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

internal val calendarTaskPadding = 2.dp
internal val calendarTaskCheckboxSize = 20.dp
internal val calendarTaskTitleGap = 6.dp
internal val calendarTaskTitleInset = calendarTaskPadding + calendarTaskCheckboxSize + calendarTaskTitleGap

internal fun calendarSidebarTaskTitleGap(task: Task) = if (task.isDailyRoutine) 4.dp else 6.dp

@Composable
internal fun calendarTaskTitleStyle(dense: Boolean): TextStyle =
    (if (dense) TaskBTheme.typography.button.copy(lineHeight = 17.sp)
    else TaskBTheme.typography.body.copy(lineHeight = 18.sp)).copy(
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

@Composable
internal fun CalendarTaskRow(
    task: Task,
    group: Group?,
    onOpenTask: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    enabled: Boolean = true,
    dragging: Boolean = false,
    date: LocalDate? = task.dueDate,
    now: Instant = Clock.System.now(),
) {
    if (task.startDateTime != null) {
        CalendarTimedTaskRow(task, group, onOpenTask, modifier, dense, enabled, dragging, date, now)
        return
    }
    val colors = TaskBTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    val titleStyle = calendarTaskTitleStyle(dense)
    Column(modifier.graphicsLayer { alpha = if (dragging) 0.25f else 1f }) {
        Row(
            Modifier.fillMaxWidth()
                .clip(if (dense) RoundedCornerShape(8.dp) else RectangleShape)
                .hoverable(interactionSource, enabled = enabled)
                .indication(interactionSource, LocalIndication.current)
                .then(
                    if (dense) Modifier.padding(calendarTaskPadding)
                    else Modifier.padding(vertical = 4.dp),
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (dense) calendarTaskTitleGap else calendarSidebarTaskTitleGap(task)),
        ) {
            Box(
                (if (dense) Modifier.size(calendarTaskCheckboxSize) else Modifier.width(24.dp))
                    .toggleable(task.isCompleted, interactionSource = interactionSource, indication = null,
                        enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(task.id) })
                    .semantics { contentDescription = if (task.isCompleted) "Mark ${task.name} incomplete" else "Complete ${task.name}" }
                    .then(if (dense) Modifier else Modifier.padding(vertical = 6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (task.isCompleted) TaskBIcons.Checked else TaskBIcons.Unchecked, null,
                    tint = if (dense && group != null) group.color.color else if (task.isCompleted) colors.accent else colors.muted,
                    // Crop the icon's empty 3 dp margins without scaling its 18 dp glyph.
                    modifier = if (dense) Modifier.size(calendarTaskCheckboxSize) else Modifier
                        .size(width = 24.dp, height = 18.dp).clipToBounds()
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(24.dp).offset(x = (-3).dp, y = (-3).dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 2.dp)) {
                Text(
                    task.name,
                    modifier = Modifier.fillMaxWidth().clickable(interactionSource = interactionSource, indication = null,
                        enabled = enabled, role = Role.Button) { onOpenTask(task.id) },
                    color = if (task.isCompleted) colors.completedText else colors.text,
                    style = titleStyle,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!dense && task.description.isNotBlank()) Text(
                    task.description,
                    modifier = Modifier.fillMaxWidth().clickable(interactionSource = interactionSource, indication = null,
                        enabled = enabled, role = Role.Button) { onOpenTask(task.id) },
                    color = colors.muted,
                    style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeightStyle),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!dense && group != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(TaskBIcons.FolderFilled, null, tint = group.color.color, modifier = Modifier.size(14.dp))
                    Text(group.name, color = colors.muted, style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeightStyle), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!dense) Divider(color = colors.border)
    }
}
