package com.mkulikov.taskb.feature.group.screen.group_editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBEditorTopAppBar
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.icon.TaskBIcons

@Composable
internal fun GroupEditorWideScreen(state: GroupEditorState, onIntent: (GroupEditorIntent) -> Unit, onBack: () -> Unit) {
    val enabled = !state.isBusy && !state.isFinished && state.dialog == null
    Column(Modifier.fillMaxSize()) {
        TaskBEditorTopAppBar(if (state.isNew) "New group" else "Edit group", onBack, !state.isSaving && !state.isDeleting) {
            if (!state.isNew) TaskBEditorButton("Delete", TaskBIcons.Delete, { onIntent(GroupEditorIntent.PrepareDelete) },
                enabled = enabled && state.original != null, destructive = true,
                modifier = Modifier.semantics { contentDescription = "Delete group" })
            TaskBEditorButton(if (state.isNew) "Create group" else "Save changes", if (state.isNew) TaskBIcons.Add else TaskBIcons.Done,
                { onIntent(GroupEditorIntent.Save) }, enabled = state.canSave && state.dialog == null,
                modifier = Modifier.semantics { contentDescription = "Save group" })
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.TopCenter) {
            GroupEditorForm(state, onIntent, Modifier.widthIn(max = 720.dp).fillMaxSize(), autoFocusName = true,
                contentPadding = PaddingValues(vertical = 32.dp))
        }
    }
}
