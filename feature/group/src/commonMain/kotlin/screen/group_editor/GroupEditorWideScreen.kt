package com.mkulikov.taskb.feature.group.screen.group_editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.component.TaskBEditorTopAppBar
import com.mkulikov.taskb.ui.icon.TaskBIcons

@Composable
internal fun GroupEditorWideScreen(
    state: GroupEditorState,
    onIntent: (GroupEditorIntent) -> Unit,
    onBack: () -> Unit,
) {
    val enabled = !state.isBusy && !state.isFinished && state.dialog == null
    Column(modifier = Modifier.fillMaxSize()) {
        TaskBEditorTopAppBar(
            title = if (state.isNew) "New group" else "Edit group",
            onBack = onBack,
            backEnabled = !state.isSaving && !state.isDeleting,
        ) {
            if (!state.isNew)
                TaskBEditorButton(
                    label = "Delete",
                    icon = TaskBIcons.Delete,
                    onClick = { onIntent(GroupEditorIntent.PrepareDelete) },
                    enabled = enabled && state.original != null,
                    destructive = true,
                    modifier = Modifier.semantics { contentDescription = "Delete group" },
                )
            TaskBEditorButton(
                label = if (state.isNew) "Create group" else "Save changes",
                icon = if (state.isNew) TaskBIcons.Add else TaskBIcons.Done,
                onClick = { onIntent(GroupEditorIntent.Save) },
                enabled = state.canSave && state.dialog == null,
                modifier = Modifier.semantics { contentDescription = "Save group" },
            )
        }
        Box(
            modifier = Modifier.weight(weight = 1f).fillMaxWidth().padding(horizontal = 32.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            GroupEditorForm(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                autoFocusName = true,
                contentPadding = PaddingValues(vertical = 32.dp),
            )
        }
    }
}
