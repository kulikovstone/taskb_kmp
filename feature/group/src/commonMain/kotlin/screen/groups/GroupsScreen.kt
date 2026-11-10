package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import org.koin.compose.koinInject

@Composable
internal fun GroupsScreen(onBack: () -> Unit, onCreateGroup: () -> Unit, onEditGroup: (Int) -> Unit) {
    val repository = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource(key = "task-groups") { GroupsStore(repository = repository) }
    val state by store.state.collectAsState()
    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= 840.dp) {
                GroupsWideScreen(
                    state = state,
                    onIntent = store::accept,
                    onBack = onBack,
                    onCreateGroup = onCreateGroup,
                    onEditGroup = onEditGroup,
                )
            } else {
                GroupsCompactScreen(
                    state = state,
                    onIntent = store::accept,
                    onBack = onBack,
                    onCreateGroup = onCreateGroup,
                    onEditGroup = onEditGroup,
                )
            }
        }
    }
}

@Composable
internal fun GroupsSummary(state: GroupsState, wide: Boolean) {
    Text(
        text = state.summary,
        color = TaskBTheme.colors.muted,
        style = if (wide) TaskBTheme.typography.body else TaskBTheme.typography.small,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
