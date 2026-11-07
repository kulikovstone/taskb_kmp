package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
fun TaskBSelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    groupColor: androidx.compose.ui.graphics.Color? = null,
    reserveGroupIconSpace: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height = 63.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            if (groupColor != null || reserveGroupIconSpace)
                Box(modifier = Modifier.size(size = 28.dp), contentAlignment = Alignment.Center) {
                    if (groupColor != null)
                        Icon(
                            imageVector = TaskBIcons.FolderFilled,
                            contentDescription = null,
                            tint = groupColor,
                            modifier = Modifier.size(size = 24.dp),
                        )
                }
            Text(
                text = label,
                style = TaskBTheme.typography.section,
                color = TaskBTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(weight = 1f),
            )
            if (selected)
                Icon(
                    imageVector = TaskBIcons.Done,
                    contentDescription = null,
                    tint = TaskBTheme.colors.accent,
                    modifier = Modifier.size(size = 24.dp),
                )
        }
        Divider(color = TaskBTheme.colors.border)
    }
}
