package com.mkulikov.taskb.feature.group.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun GroupColorPicker(selected: GroupColor, onSelect: (GroupColor) -> Unit, enabled: Boolean) {
    val colors = TaskBTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(height = 18.dp)) {
            Text(text = "Color", style = TaskBTheme.typography.label, color = colors.text)
            Spacer(modifier = Modifier.weight(weight = 1f))
            Text(text = selected.label, style = TaskBTheme.typography.small, color = colors.muted)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            GroupColor.entries.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(size = 24.dp)
                        .clip(shape = CircleShape)
                        .background(color = color.color, shape = CircleShape)
                        .border(width = 1.dp, color = colors.border, shape = CircleShape)
                        .selectable(
                            selected = selected == color,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(color) },
                        )
                        .semantics { contentDescription = color.label },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected == color)
                        Icon(
                            imageVector = TaskBIcons.Done,
                            contentDescription = null,
                            tint = Palette.Black,
                            modifier = Modifier.size(size = 16.dp),
                        )
                }
            }
        }
    }
}

@Composable
internal fun GroupStatistics(group: Group) {
    val colors = TaskBTheme.colors
    val statistics = listOf(
        "Events" to group.eventCount,
        "Planned" to group.tasks.planned,
        "Completed" to group.tasks.completed,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        statistics.forEach { (label, value) ->
            Column(
                modifier = Modifier
                    .weight(weight = 1f)
                    .widthIn(min = 88.dp)
                    .height(height = 78.dp)
                    .background(color = colors.subtle, shape = RoundedCornerShape(size = 10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(space = 4.dp),
            ) {
                Text(text = label, style = TaskBTheme.typography.small, color = colors.secondary, maxLines = 1)
                Text(text = value.toString(), style = TaskBTheme.typography.number, color = colors.text)
            }
        }
    }
}
