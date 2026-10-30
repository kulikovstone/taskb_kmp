package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

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
    Column(modifier.graphicsLayer { alpha = if (dragging) 0.25f else 1f }) {
        Row(
            Modifier.fillMaxWidth().then(if (dense) Modifier.padding(2.dp) else Modifier.heightIn(min = 44.dp).padding(vertical = if (group != null) 4.dp else 0.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 8.dp),
        ) {
            Box(
                Modifier.size(if (dense) 14.dp else 44.dp)
                    .toggleable(task.isCompleted, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(task.id) })
                    .semantics { contentDescription = if (task.isCompleted) "Mark ${task.name} incomplete" else "Complete ${task.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (task.isCompleted) TaskBIcons.Checked else TaskBIcons.Unchecked, null,
                    tint = if (dense) group?.color?.color ?: colors.accent else if (task.isCompleted) colors.accent else colors.muted,
                    modifier = Modifier.size(if (dense) 14.dp else 24.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    task.name,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenTask(task.id) },
                    color = if (task.isCompleted) colors.muted else colors.text,
                    style = if (dense) TaskBTheme.typography.small.copy(fontSize = 11.sp, lineHeight = 14.sp) else TaskBTheme.typography.body,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = if (dense) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!dense && group != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(TaskBIcons.FolderFilled, null, tint = group.color.color, modifier = Modifier.size(14.dp))
                    Text(group.name, color = colors.muted, style = TaskBTheme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!dense) Divider(color = colors.border)
    }
}
