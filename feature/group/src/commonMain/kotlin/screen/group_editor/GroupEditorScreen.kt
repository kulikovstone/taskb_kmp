package com.mkulikov.taskb.feature.group.screen.group_editor

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import org.koin.compose.koinInject

@Composable
internal fun GroupEditorScreen(groupId: Int? = null, onBack: () -> Unit) {
    val repository = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource("group-editor") { GroupEditorStore(repository, groupId) }
    val state by store.state.collectAsState()
    LaunchedEffect(store, state.isFinished) { if (state.isFinished) onBack() }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 840.dp) GroupEditorWideScreen(state, store::accept, onBack)
            else GroupEditorCompactScreen(state, store::accept, onBack)
        }
    }
    GroupEditorDialogs(state, store::accept)
}
