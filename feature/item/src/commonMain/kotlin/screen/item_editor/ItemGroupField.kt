package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun ItemGroupField(
    group: Group?,
    enabled: Boolean,
    onChooseGroup: () -> Unit,
    onClearGroup: (() -> Unit)?,
    description: String,
    clearDescription: String,
) {
    val groupIcon = if (group == null) TaskBIcons.Folder else TaskBIcons.FolderFilled
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Group",
            style = TaskBTheme.typography.label,
            color = TaskBTheme.colors.text,
            modifier = Modifier.height(height = 18.dp),
        )
        Spacer(modifier = Modifier.height(height = 8.dp))
        ItemSelectionField(
            label = group?.name ?: "No group",
            icon = groupIcon,
            tint = group?.color?.color ?: TaskBTheme.colors.muted,
            placeholder = group == null,
            description = description,
            enabled = enabled,
            onClick = onChooseGroup,
            onClear = onClearGroup,
            clearDescription = clearDescription,
            height = 44.dp,
        )
    }
}
