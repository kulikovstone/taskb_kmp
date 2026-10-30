package com.mkulikov.taskb.feature.group.screen.group_editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.component.GroupColorPicker
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.feature.group.component.GroupStatistics
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun GroupEditorForm(
    state: GroupEditorState,
    onIntent: (GroupEditorIntent) -> Unit,
    modifier: Modifier,
    autoFocusName: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(top = 24.dp),
) {
    when {
        state.isLoading -> TaskBLoading(modifier)
        state.loadError != null -> TaskBLoadError(state.loadError, { onIntent(GroupEditorIntent.RetryLoad) }, modifier)
        else -> Column(modifier.verticalScroll(rememberScrollState()).padding(contentPadding)) {
            val enabled = !state.isBusy && !state.isFinished && state.dialog == null
            val colors = TaskBTheme.colors
            if (!state.isNew) {
                GroupStatistics(requireNotNull(state.original).tasks)
                Spacer(Modifier.height(24.dp))
            }
            Text("Group name", modifier = Modifier.height(18.dp), style = TaskBTheme.typography.label, color = colors.text)
            Spacer(Modifier.height(8.dp))
            TaskBTextField(
                value = state.name, onValueChange = { onIntent(GroupEditorIntent.NameChanged(it)) }, description = "Group name",
                placeholder = "Enter group name", enabled = enabled, modifier = Modifier.fillMaxWidth(),
                autoFocus = autoFocusName,
                onDone = { onIntent(GroupEditorIntent.Save) },
            )
            Spacer(Modifier.height(24.dp))
            GroupColorPicker(state.color, { onIntent(GroupEditorIntent.ColorChanged(it)) }, enabled)
            if (state.isSaving || state.isDeleting) Text(if (state.isDeleting) "Deleting…" else "Saving…", style = TaskBTheme.typography.small, color = colors.muted)
        }
    }
}
