package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
fun TaskBEditorTopAppBar(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height = 96.dp).padding(start = 16.dp, end = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = backEnabled) {
            Icon(imageVector = TaskBIcons.Back, contentDescription = "Back", tint = TaskBTheme.colors.heading)
        }
        Spacer(modifier = Modifier.width(width = 8.dp))
        Text(
            text = title,
            modifier = Modifier.weight(weight = 1f).offset(y = (-2).dp),
            style = TaskBTheme.typography.display,
            color = TaskBTheme.colors.heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
