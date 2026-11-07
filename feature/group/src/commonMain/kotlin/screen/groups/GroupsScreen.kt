package com.mkulikov.taskb.feature.group.screen.groups

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
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
