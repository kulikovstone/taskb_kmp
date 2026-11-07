package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence
import com.mkulikov.taskb.ui.input.withTextIfChanged
import kotlinx.datetime.LocalDate

internal sealed interface TaskEditorMessage {
    data class Loaded(val task: Task?) : TaskEditorMessage

    data class Date(val value: LocalDate?) : TaskEditorMessage

    data class Recurrence(val value: TaskRecurrence?) : TaskEditorMessage

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

internal fun reduceTaskEditor(state: TaskEditorState, message: TaskEditorMessage): TaskEditorState {
    return when (message) {
        is TaskEditorMessage.Loaded -> {
            val task = message.task
            if (task == null) {
                state.copy(isLoading = false, loadError = "This task no longer exists.")
            } else {
                state.copy(
                    isLoading = false,
                    original = task,
                    name = TextFieldState(initialText = task.name),
                    description = TextFieldState(initialText = task.description),
                    dueDate = task.dueDate,
                    recurrence = task.recurrence,
                    repeatInterval = TextFieldState(initialText = (task.recurrence?.interval ?: 1).toString()),
                    repeatDay = TextFieldState(
                        initialText = (task.recurrence?.dayOfMonth ?: task.dueDate?.day ?: 1).toString()
                    ),
                    repeatCount = TextFieldState(initialText = (task.recurrence?.occurrenceCount ?: 10).toString()),
                    groupId = task.groupId?.takeIf { id ->
                        state.isLoadingGroups ||
                            state.groupsLoadError != null ||
                            state.groups.any { group -> group.id == id }
                    },
                )
            }
        }
        is TaskEditorMessage.Date -> state.copy(
            dueDate = message.value,
            recurrence = state.recurrence.takeIf { message.value != null },
            dialog = null,
        )
        is TaskEditorMessage.Recurrence -> state.copy(
            recurrence = message.value,
            repeatInterval = state.repeatInterval.withTextIfChanged(text = (message.value?.interval ?: 1).toString()),
            repeatDay = state.repeatDay.withTextIfChanged(
                text = (message.value?.dayOfMonth ?: state.dueDate?.day ?: 1).toString()
            ),
            repeatCount = state.repeatCount.withTextIfChanged(text = (message.value?.occurrenceCount ?: 10).toString()),
            dialog = null,
        )
        is TaskEditorMessage.GroupSelected -> state.copy(groupId = message.value)
        is TaskEditorMessage.GroupsLoaded -> state.copy(
            groups = message.groups,
            isLoadingGroups = false,
            groupsLoadError = null,
            groupId = state.groupId?.takeIf { id -> message.groups.any { group -> group.id == id } },
        )
        TaskEditorMessage.GroupsLoading -> state.copy(isLoadingGroups = true, groupsLoadError = null)
        TaskEditorMessage.GroupsLoadFailed -> state.copy(
            isLoadingGroups = false,
            groupsLoadError = "Couldn't load groups. Please try again.",
        )
        is TaskEditorMessage.Today -> state.copy(today = message.value)
        is TaskEditorMessage.Dialog -> state.copy(dialog = message.value)
        TaskEditorMessage.Loading -> state.copy(isLoading = true, loadError = null)
        TaskEditorMessage.LoadFailed -> state.copy(
            isLoading = false,
            loadError = "Couldn't load this task. Please try again.",
        )
        TaskEditorMessage.Saving -> state.copy(isSaving = true, dialog = null)
        TaskEditorMessage.Finished -> state.copy(isSaving = false, isFinished = true)
        is TaskEditorMessage.Failed -> state.copy(
            isSaving = false,
            dialog = TaskEditorDialog.Error(message = message.message),
        )
    }
}
