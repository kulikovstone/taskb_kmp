package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun GroupsCompactScreen(state: GroupsState, onIntent: (GroupsIntent) -> Unit, onBack: () -> Unit, onCreateGroup: () -> Unit, onEditGroup: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TaskBTopAppBar("Groups", onBack, titleSpacing = 4.dp) {
            IconButton(onClick = onCreateGroup) {
                Icon(TaskBIcons.Add, contentDescription = "Add group", tint = TaskBTheme.colors.accent)
            }
        }
        Column(Modifier.weight(1f).padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
            TaskBTextField(state.query, "Search groups", placeholder = "Search groups", search = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            GroupsFilters(state, onIntent)
            Spacer(Modifier.height(16.dp))
            GroupsDirectory(state, onIntent, compact = true, onOpenGroup = onEditGroup, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
