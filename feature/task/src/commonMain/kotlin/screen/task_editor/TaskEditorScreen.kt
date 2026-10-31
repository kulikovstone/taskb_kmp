package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.core.navigation.rememberNavEntryResource
import com.mkulikov.taskb.ui.component.TaskBEditorTopAppBar
import com.mkulikov.taskb.ui.component.TaskBEditorButton
import com.mkulikov.taskb.ui.component.TaskBLoadError
import com.mkulikov.taskb.ui.component.TaskBLoading
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject

@Composable
internal fun TaskEditorScreen(
    taskId: Int? = null, initialDate: LocalDate? = null, initialGroupId: Int? = null,
    onBack: () -> Unit, groupResults: Flow<GroupSelectionResult>, onChooseGroup: (Int?) -> Unit,
) {
    val repository = koinInject<ITaskRepository>()
    val groups = koinInject<IGroupRepository>()
    val store = rememberNavEntryResource("task-editor") { TaskEditorStore(repository, groups, taskId, initialDate, initialGroupId) }
    val state by store.state.collectAsState()
    LaunchedEffect(store, state.isFinished) { if (state.isFinished) onBack() }
    LaunchedEffect(store, groupResults) {
        groupResults.collect { store.accept(TaskEditorIntent.GroupChanged(it.groupId)) }
    }

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            TaskEditorContent(state, store::accept, wide, onBack, onChooseGroup = { onChooseGroup(state.groupId) })
            TaskEditorDialogs(state, state.today, store::accept, maxHeight)
        }
    }
}

@Composable
internal fun TaskEditorContent(state: TaskEditorState, onIntent: (TaskEditorIntent) -> Unit, wide: Boolean, onBack: () -> Unit, onChooseGroup: () -> Unit) {
    val title = if (state.isNew) "New task" else "Edit task"
    val canDelete = !state.isBusy && state.dialog == null && !state.isFinished && state.original != null
    Column(Modifier.fillMaxSize()) {
        if (wide) TaskBEditorTopAppBar(title, onBack, !state.isSaving) {
            if (!state.isNew) TaskBEditorButton("Delete", TaskBIcons.Delete, { onIntent(TaskEditorIntent.PrepareDelete) },
                enabled = canDelete, destructive = true, modifier = Modifier.semantics { contentDescription = "Delete task" })
            TaskBEditorButton(if (state.isNew) "Create task" else "Save changes", if (state.isNew) TaskBIcons.Add else TaskBIcons.Done,
                { onIntent(TaskEditorIntent.Save) }, enabled = state.canSave, modifier = Modifier.semantics { contentDescription = "Save task" })
        } else TaskBTopAppBar(title, onBack, backEnabled = !state.isSaving, titleSpacing = 4.dp) {
            if (!state.isNew) IconButton({ onIntent(TaskEditorIntent.PrepareDelete) }, enabled = canDelete) {
                Icon(TaskBIcons.Delete, "Delete task", tint = TaskBTheme.colors.danger)
            }
            IconButton({ onIntent(TaskEditorIntent.Save) }, enabled = state.canSave) {
                Icon(TaskBIcons.Done, "Save task", tint = if (state.canSave) TaskBTheme.colors.accent else TaskBTheme.colors.border)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (wide) 32.dp else 0.dp), contentAlignment = Alignment.TopCenter) {
            val modifier = Modifier.widthIn(max = if (wide) 720.dp else androidx.compose.ui.unit.Dp.Infinity).fillMaxSize()
            when {
                state.isLoading || state.isLoadingGroups -> TaskBLoading(modifier)
                state.loadError != null -> TaskBLoadError(state.loadError, { onIntent(TaskEditorIntent.RetryLoad) }, modifier)
                state.groupsLoadError != null -> TaskBLoadError(state.groupsLoadError, { onIntent(TaskEditorIntent.RetryGroups) }, modifier)
                else -> TaskEditorForm(state, state.groups, state.today, onIntent,
                    onChooseGroup, wide, modifier)
            }
        }
    }
}
