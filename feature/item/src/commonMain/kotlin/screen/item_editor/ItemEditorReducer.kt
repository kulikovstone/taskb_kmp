package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.Item
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.api.model.endDateTime
import kotlinx.datetime.LocalDate

internal sealed interface ItemEditorMessage {
    data class Loaded(val item: Item?) : ItemEditorMessage

    data class Schedule(val value: ScheduleDraft) : ItemEditorMessage

    data class SwitchSchedule(val value: ScheduleDraft) : ItemEditorMessage

    data class GroupSelected(val value: Int?) : ItemEditorMessage

    data class GroupsLoaded(val groups: List<Group>) : ItemEditorMessage

    data object GroupsLoading : ItemEditorMessage

    data object GroupsLoadFailed : ItemEditorMessage

    data class Today(val value: LocalDate) : ItemEditorMessage

    data class Dialog(val value: ItemEditorDialog?) : ItemEditorMessage

    data object Loading : ItemEditorMessage

    data object LoadFailed : ItemEditorMessage

    data object Saving : ItemEditorMessage

    data object Finished : ItemEditorMessage

    data class Failed(val message: String) : ItemEditorMessage
}

internal fun reduceItemEditor(state: ItemEditorState, message: ItemEditorMessage): ItemEditorState {
    return when (message) {
        is ItemEditorMessage.Loaded -> state.loaded(item = message.item)
        is ItemEditorMessage.Schedule -> state.copy(schedule = message.value, dialog = null)
        is ItemEditorMessage.SwitchSchedule -> state.copy(
            schedule = message.value,
            inactiveSchedules = state.inactiveSchedules - message.value.details.type + (state.type to state.schedule),
        )
        is ItemEditorMessage.GroupSelected -> state.copy(groupId = message.value)
        is ItemEditorMessage.GroupsLoaded -> state.copy(
            groups = message.groups,
            isLoadingGroups = false,
            groupsLoadError = null,
            groupId = state.groupId?.takeIf { id -> message.groups.any { group -> group.id == id } },
        )
        ItemEditorMessage.GroupsLoading -> state.copy(isLoadingGroups = true, groupsLoadError = null)
        ItemEditorMessage.GroupsLoadFailed -> state.copy(
            isLoadingGroups = false,
            groupsLoadError = "Couldn't load groups. Please try again.",
        )
        is ItemEditorMessage.Today -> state.copy(today = message.value)
        is ItemEditorMessage.Dialog -> state.copy(dialog = message.value)
        ItemEditorMessage.Loading -> state.copy(isLoading = true, loadError = null)
        ItemEditorMessage.LoadFailed -> state.copy(
            isLoading = false,
            loadError = "Couldn't load this ${state.type.name.lowercase()}. Please try again.",
        )
        ItemEditorMessage.Saving -> state.copy(isSaving = true, dialog = null)
        ItemEditorMessage.Finished -> state.copy(isSaving = false, isFinished = true)
        is ItemEditorMessage.Failed -> state.copy(
            isSaving = false,
            dialog = ItemEditorDialog.Error(message = message.message),
        )
    }
}

private fun ItemEditorState.loaded(item: Item?): ItemEditorState {
    if (item == null) return copy(isLoading = false, loadError = "This ${type.name.lowercase()} no longer exists.")
    val original = if (item is Event && item.startTime != null && (item.durationMinutes ?: 0) <= 0) {
        item.copy(durationMinutes = 60)
    } else {
        item
    }
    val details = when (original) {
        is Task -> TaskSchedule(
            dueDate = original.dueDate,
            showInCalendar = original.showInCalendar,
            quickDelete = original.quickDelete,
        )
        is Event -> EventSchedule(
            dueDate = original.dueDate ?: today,
            endDate = original.endDate ?: original.dueDate ?: today,
            startTime = original.startTime,
            endTime = original.endDateTime?.time,
        )
    }
    return copy(
        isLoading = false,
        original = original,
        name = TextFieldState(initialText = original.name),
        description = TextFieldState(initialText = original.description),
        schedule = ScheduleDraft(details = details, recurrence = original.recurrence),
        groupId = original.groupId?.takeIf { id ->
            isLoadingGroups || groupsLoadError != null || groups.any { group -> group.id == id }
        },
    )
}
