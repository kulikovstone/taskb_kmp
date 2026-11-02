package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.group.api.model.Group
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.mkulikov.taskb.feature.task.api.model.TaskRecurrence

internal sealed interface TaskEditorMessage {
    data class Loaded(val task: Task?) : TaskEditorMessage
    data class Date(val value: LocalDate?) : TaskEditorMessage
    data class StartTime(val value: LocalTime?) : TaskEditorMessage
    data class Duration(val value: Int) : TaskEditorMessage
    data object CustomDuration : TaskEditorMessage
    data class DurationMode(val value: TaskDurationMode) : TaskEditorMessage
    data class DurationInput(val days: String? = null, val hours: String? = null, val minutes: String? = null) : TaskEditorMessage
    data class EndDate(val value: LocalDate) : TaskEditorMessage
    data class EndTime(val value: LocalTime) : TaskEditorMessage
    data class Recurrence(val value: TaskRecurrence?) : TaskEditorMessage
    data class RepeatInput(val interval: String? = null, val day: String? = null, val count: String? = null) : TaskEditorMessage
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
    is TaskEditorMessage.Loaded -> {
        val task = message.task?.let {
            if (it.startTime != null && it.dueDate != null) it.copy(durationMinutes = it.durationMinutes?.takeIf { minutes -> minutes > 0 } ?: 60) else it
        }
        if (task == null) state.copy(isLoading = false, loadError = "This task no longer exists.")
        else state.copy(isLoading = false, original = task, name = TextFieldState(task.name),
            description = TextFieldState(task.description), dueDate = task.dueDate,
            startTime = task.startTime, durationMinutes = task.durationMinutes,
            recurrence = task.recurrence, repeatInterval = (task.recurrence?.interval ?: 1).toString(),
            repeatDay = (task.recurrence?.dayOfMonth ?: task.dueDate?.day ?: 1).toString(), repeatCount = (task.recurrence?.occurrenceCount ?: 10).toString(),
            groupId = task.groupId?.takeIf { id -> state.isLoadingGroups || state.groupsLoadError != null || state.groups.any { it.id == id } })
            .let { loaded -> if (loaded.durationMinutes != null && loaded.durationMinutes !in listOf(15, 30, 60)) reduceTaskEditor(loaded, TaskEditorMessage.CustomDuration) else loaded }
    }
    is TaskEditorMessage.Date -> state.copy(dueDate = message.value, recurrence = state.recurrence.takeIf { message.value != null },
        startTime = state.startTime.takeIf { message.value != null }, durationMinutes = state.durationMinutes.takeIf { message.value != null },
        customDuration = state.customDuration && message.value != null, dialog = null)
    is TaskEditorMessage.StartTime -> state.copy(startTime = message.value,
        durationMinutes = if (message.value == null) null else state.durationMinutes?.takeIf { it > 0 } ?: 60,
        customDuration = state.customDuration && message.value != null, dialog = null)
    is TaskEditorMessage.Duration -> state.copy(durationMinutes = message.value, customDuration = false)
    TaskEditorMessage.CustomDuration -> {
        val minutes = state.editedDurationMinutes ?: 60
        val ending = state.copy(durationMinutes = minutes, customDuration = false).ending
        state.copy(customDuration = true, durationDays = (minutes / 1440).toString(), durationHours = (minutes % 1440 / 60).toString(),
            durationMinutePart = (minutes % 60).toString(), endDate = ending?.date ?: state.dueDate, endTime = ending?.time ?: state.startTime)
    }
    is TaskEditorMessage.DurationMode -> {
        val prepared = reduceTaskEditor(state, TaskEditorMessage.CustomDuration)
        prepared.copy(durationMode = message.value)
    }
    is TaskEditorMessage.DurationInput -> state.copy(durationDays = message.days ?: state.durationDays,
        durationHours = message.hours ?: state.durationHours, durationMinutePart = message.minutes ?: state.durationMinutePart)
    is TaskEditorMessage.EndDate -> state.copy(endDate = message.value, dialog = null)
    is TaskEditorMessage.EndTime -> state.copy(endTime = message.value, dialog = null)
    is TaskEditorMessage.Recurrence -> state.copy(recurrence = message.value, repeatInterval = (message.value?.interval ?: 1).toString(),
        repeatDay = (message.value?.dayOfMonth ?: state.dueDate?.day ?: 1).toString(), repeatCount = (message.value?.occurrenceCount ?: 10).toString(), dialog = null)
    is TaskEditorMessage.RepeatInput -> state.copy(repeatInterval = message.interval ?: state.repeatInterval, repeatDay = message.day ?: state.repeatDay, repeatCount = message.count ?: state.repeatCount)
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
