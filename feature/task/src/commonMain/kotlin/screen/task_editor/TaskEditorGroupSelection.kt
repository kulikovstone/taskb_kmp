package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.feature.task.component.GroupSelectionContent
import com.mkulikov.taskb.feature.task.component.SelectionDialog
import com.mkulikov.taskb.feature.task.component.SelectionScreen

@Composable
internal fun TaskEditorGroupSelection(state: TaskEditorState, onIntent: (TaskEditorIntent) -> Unit, wide: Boolean, availableHeight: Dp) {
    val onClose = { onIntent(TaskEditorIntent.CloseGroupSelection) }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        when {
            state.isLoadingGroups -> TaskBLoading(modifier)
            state.groupsLoadError != null -> TaskBLoadError(state.groupsLoadError, { onIntent(TaskEditorIntent.RetryGroups) }, modifier)
            else -> GroupSelectionContent(state.groups, state.groupId, state.groupId == null, state.groupQuery,
                { onIntent(TaskEditorIntent.SearchGroups(it)) }, { onIntent(TaskEditorIntent.GroupChanged(it)) }, modifier, scrollbar = wide)
        }
    }
    if (wide) SelectionDialog("Choose group", onClose, availableHeight, 624.dp, content)
    else SelectionScreen("Choose group", onClose, content)
}
