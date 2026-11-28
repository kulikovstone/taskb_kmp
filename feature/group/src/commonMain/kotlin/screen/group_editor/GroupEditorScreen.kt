package com.mkulikov.taskb.feature.group.screen.group_editor

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.ui.layout.TaskBLayout
import org.koin.compose.koinInject

@Composable
internal fun GroupEditorScreen(groupId: Int? = null, onBack: () -> Unit) {
    val repository = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource(key = "group-editor") {
        GroupEditorStore(repository = repository, groupId = groupId)
    }
    val state by store.state.collectAsState()
    LaunchedEffect(key1 = store, key2 = state.isFinished) { if (state.isFinished) onBack() }

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= TaskBLayout.wideMinWidth) {
                GroupEditorWideScreen(state = state, onIntent = store::accept, onBack = onBack)
            } else {
                GroupEditorCompactScreen(state = state, onIntent = store::accept, onBack = onBack)
            }
        }
    }
    GroupEditorDialogs(state = state, onIntent = store::accept)
}
