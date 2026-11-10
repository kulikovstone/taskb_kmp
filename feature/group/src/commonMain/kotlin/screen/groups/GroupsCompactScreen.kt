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
internal fun GroupsCompactScreen(
    state: GroupsState,
    onIntent: (GroupsIntent) -> Unit,
    onBack: () -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TaskBTopAppBar(
            title = "Groups",
            onBack = onBack,
            titleSpacing = 4.dp,
            subtitle = { GroupsSummary(state = state, wide = false) },
        ) {
            IconButton(onClick = onCreateGroup) {
                Icon(
                    imageVector = TaskBIcons.Add,
                    contentDescription = "Add group",
                    tint = TaskBTheme.colors.accent,
                )
            }
        }
        Column(modifier = Modifier.weight(weight = 1f).padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                TaskBTextField(
                    state = state.query,
                    description = "Search groups",
                    placeholder = "Search groups",
                    search = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(height = 8.dp))
                GroupsFilters(state = state, onIntent = onIntent)
            }
            Spacer(modifier = Modifier.height(height = 8.dp))
            GroupsDirectory(
                state = state,
                onIntent = onIntent,
                onOpenGroup = onEditGroup,
                modifier = Modifier.weight(weight = 1f).fillMaxWidth(),
            )
        }
    }
}
