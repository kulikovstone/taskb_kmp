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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

internal val calendarTaskPadding = 2.dp
internal val calendarTaskCheckboxSize = 20.dp
internal val calendarTaskTitleGap = 6.dp
internal val calendarTaskTitleInset = calendarTaskPadding + calendarTaskCheckboxSize + calendarTaskTitleGap

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
) {
    val colors = TaskBTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    val titleStyle = (if (dense) TaskBTheme.typography.button.copy(lineHeight = 17.sp)
        else TaskBTheme.typography.body.copy(lineHeight = 18.sp)).copy(
        lineHeightStyle = lineHeightStyle,
    )
    Column(modifier.graphicsLayer { alpha = if (dragging) 0.25f else 1f }) {
        Row(
            Modifier.fillMaxWidth()
                .clip(if (dense) RoundedCornerShape(8.dp) else RectangleShape)
                .hoverable(interactionSource, enabled = enabled)
                .indication(interactionSource, LocalIndication.current)
                .then(
                    if (dense) Modifier.padding(calendarTaskPadding)
                    else Modifier.heightIn(min = 44.dp).padding(vertical = if (group != null) 2.dp else 0.dp),
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (dense) calendarTaskTitleGap else 8.dp),
        ) {
            Box(
                Modifier.size(if (dense) calendarTaskCheckboxSize else 44.dp)
                    .toggleable(task.isCompleted, interactionSource = interactionSource, indication = null,
                        enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(task.id) })
                    .semantics { contentDescription = if (task.isCompleted) "Mark ${task.name} incomplete" else "Complete ${task.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (task.isCompleted) TaskBIcons.Checked else TaskBIcons.Unchecked, null,
                    tint = if (dense) group?.color?.color ?: colors.muted else if (task.isCompleted) colors.accent else colors.muted,
                    modifier = Modifier.size(if (dense) calendarTaskCheckboxSize else 24.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                if (!dense && group != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(TaskBIcons.FolderFilled, null, tint = group.color.color, modifier = Modifier.size(14.dp))
                    Text(group.name, color = colors.muted, style = TaskBTheme.typography.small.copy(lineHeightStyle = lineHeightStyle), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!dense) Divider(color = colors.border)
    }
}
