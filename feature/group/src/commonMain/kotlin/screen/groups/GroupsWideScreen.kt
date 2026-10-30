package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(96.dp).padding(start = 16.dp, end = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(TaskBIcons.Back, contentDescription = "Back", tint = TaskBTheme.colors.heading) }
            Spacer(Modifier.width(8.dp))
            Text("Groups", style = TaskBTheme.typography.display, color = TaskBTheme.colors.heading)
            Spacer(Modifier.weight(1f))
            TaskBEditorButton("New group", TaskBIcons.Add, onCreateGroup)
        }
        Column(Modifier.weight(1f).padding(start = 32.dp, end = 32.dp, top = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TaskBTextField(state.query, { onIntent(GroupsIntent.Search(it)) }, "Search groups", placeholder = "Search groups", search = true, modifier = Modifier.width(300.dp))
                GroupsFilters(state, onIntent)
            }
            Spacer(Modifier.height(24.dp))
            GroupsDirectory(state, onIntent, compact = false, onOpenGroup = onEditGroup,
                modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
