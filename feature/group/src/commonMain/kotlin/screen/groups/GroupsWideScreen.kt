package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun GroupsWideScreen(
    state: GroupsState,
    onIntent: (GroupsIntent) -> Unit,
    onBack: () -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height = 96.dp).padding(start = 16.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = TaskBIcons.Back, contentDescription = "Back", tint = TaskBTheme.colors.heading)
            }
            Spacer(modifier = Modifier.width(width = 8.dp))
            Text(
                text = "Groups",
                modifier = Modifier.offset(y = (-2).dp),
                style = TaskBTheme.typography.display,
                color = TaskBTheme.colors.heading,
            )
            Spacer(modifier = Modifier.weight(weight = 1f))
            TaskBEditorButton(label = "New group", icon = TaskBIcons.Add, onClick = onCreateGroup)
        }
        Column(modifier = Modifier.weight(weight = 1f).padding(start = 32.dp, end = 32.dp, top = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(height = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 24.dp),
            ) {
                TaskBTextField(
                    state = state.query,
                    description = "Search groups",
                    placeholder = "Search groups",
                    search = true,
                    modifier = Modifier.width(width = 300.dp),
                )
                GroupsFilters(state = state, onIntent = onIntent)
            }
            Spacer(modifier = Modifier.height(height = 24.dp))
            GroupsDirectory(
                state = state,
                onIntent = onIntent,
                compact = false,
                onOpenGroup = onEditGroup,
                modifier = Modifier.weight(weight = 1f).fillMaxWidth(),
            )
        }
    }
}
