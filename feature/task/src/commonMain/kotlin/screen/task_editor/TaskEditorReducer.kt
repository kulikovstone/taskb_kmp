package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.group.api.model.Group
import kotlinx.datetime.LocalDate

internal sealed interface TaskEditorMessage {
    data class Loaded(val task: Task?) : TaskEditorMessage
    data class Date(val value: LocalDate?) : TaskEditorMessage
    data class GroupSelected(val value: Int?) : TaskEditorMessage
    data class GroupsLoaded(val groups: List<Group>) : TaskEditorMessage
    data object GroupsLoading : TaskEditorMessage
    data object GroupsLoadFailed : TaskEditorMessage
    data class Today(val value: LocalDate) : TaskEditorMessage
    data class Dialog(val value: TaskEditorDialog?) : TaskEditorMessage
    data object Loading : TaskEditorMessage
    data object LoadFailed : TaskEditorMessage
    data object Saving : TaskEditorMessage
    data object Finished : TaskEditorMessage
    data class Failed(val message: String) : TaskEditorMessage
}
internal fun reduceTaskEditor(state: TaskEditorState, message: TaskEditorMessage): TaskEditorState = when (message) {
    is TaskEditorMessage.Loaded -> if (message.task == null) state.copy(isLoading = false, loadError = "This task no longer exists.")
        else state.copy(isLoading = false, original = message.task, name = TextFieldState(message.task.name), dueDate = message.task.dueDate,
            groupId = message.task.groupId?.takeIf { id -> state.isLoadingGroups || state.groupsLoadError != null || state.groups.any { it.id == id } })
    is TaskEditorMessage.Date -> state.copy(dueDate = message.value, dialog = null)
    is TaskEditorMessage.GroupSelected -> state.copy(groupId = message.value)
    is TaskEditorMessage.GroupsLoaded -> state.copy(groups = message.groups, isLoadingGroups = false, groupsLoadError = null,
        groupId = state.groupId?.takeIf { id -> message.groups.any { it.id == id } })
    TaskEditorMessage.GroupsLoading -> state.copy(isLoadingGroups = true, groupsLoadError = null)
    TaskEditorMessage.GroupsLoadFailed -> state.copy(isLoadingGroups = false, groupsLoadError = "Couldn't load groups. Please try again.")
    is TaskEditorMessage.Today -> state.copy(today = message.value)
    is TaskEditorMessage.Dialog -> state.copy(dialog = message.value)
    TaskEditorMessage.Loading -> state.copy(isLoading = true, loadError = null)
    TaskEditorMessage.LoadFailed -> state.copy(isLoading = false, loadError = "Couldn't load this task. Please try again.")
    TaskEditorMessage.Saving -> state.copy(isSaving = true, dialog = null)
    TaskEditorMessage.Finished -> state.copy(isSaving = false, isFinished = true)
    is TaskEditorMessage.Failed -> state.copy(isSaving = false, dialog = TaskEditorDialog.Error(message.message))
}
