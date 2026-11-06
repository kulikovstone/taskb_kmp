package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.ui.input.withTextIfChanged
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.group.api.model.Group
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import com.mkulikov.taskb.feature.event.api.model.endDateTime
import com.mkulikov.taskb.feature.event.api.model.eventEndDateTime
import kotlinx.datetime.LocalTime
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence

internal sealed interface EventEditorMessage {
    data class Loaded(val event: Event?) : EventEditorMessage
    data class Date(val value: LocalDate) : EventEditorMessage
    data class StartTime(val value: LocalTime?) : EventEditorMessage
    data class Duration(val value: Int) : EventEditorMessage
    data class EndDate(val value: LocalDate) : EventEditorMessage
    data class EndTime(val value: LocalTime) : EventEditorMessage
    data class Recurrence(val value: EventRecurrence?) : EventEditorMessage
    data class GroupSelected(val value: Int?) : EventEditorMessage
    data class GroupsLoaded(val groups: List<Group>) : EventEditorMessage
    data object GroupsLoading : EventEditorMessage
    data object GroupsLoadFailed : EventEditorMessage
    data class Today(val value: LocalDate) : EventEditorMessage
    data class Dialog(val value: EventEditorDialog?) : EventEditorMessage
    data object Loading : EventEditorMessage
    data object LoadFailed : EventEditorMessage
    data object Saving : EventEditorMessage
    data object Finished : EventEditorMessage
    data class Failed(val message: String) : EventEditorMessage
}
internal fun reduceEventEditor(state: EventEditorState, message: EventEditorMessage): EventEditorState = when (message) {
    is EventEditorMessage.Loaded -> {
        val event = message.event?.let {
            if (it.startTime != null) it.copy(durationMinutes = it.durationMinutes?.takeIf { minutes -> minutes > 0 } ?: 60) else it
        }
        if (event == null) state.copy(isLoading = false, loadError = "This event no longer exists.")
        else state.copy(isLoading = false, original = event, name = TextFieldState(event.name),
            description = TextFieldState(event.description), dueDate = event.dueDate ?: state.today,
            endDate = event.endDate ?: event.dueDate ?: state.today,
            startTime = event.startTime, endTime = event.endDateTime?.time,
            recurrence = event.recurrence, repeatInterval = TextFieldState((event.recurrence?.interval ?: 1).toString()),
            repeatDay = TextFieldState((event.recurrence?.dayOfMonth ?: event.dueDate?.day ?: 1).toString()),
            repeatCount = TextFieldState((event.recurrence?.occurrenceCount ?: 10).toString()),
            groupId = event.groupId?.takeIf { id -> state.isLoadingGroups || state.groupsLoadError != null || state.groups.any { it.id == id } })
    }
    is EventEditorMessage.Date -> state.copy(dueDate = message.value,
        endDate = message.value.plus((state.endDate.toEpochDays() - state.dueDate.toEpochDays()).coerceAtLeast(0), DateTimeUnit.DAY), dialog = null)
    is EventEditorMessage.StartTime -> if (message.value == null) state.copy(startTime = null, endTime = null, dialog = null) else {
        val ending = eventEndDateTime(state.dueDate, message.value, state.editedDurationMinutes ?: 30)
        state.copy(startTime = message.value, endTime = ending.time,
            endDate = if (state.startTime == null) maxOf(state.endDate, ending.date) else ending.date, dialog = null)
    }
    is EventEditorMessage.Duration -> {
        val ending = eventEndDateTime(state.dueDate, requireNotNull(state.startTime), message.value)
        state.copy(endDate = ending.date, endTime = ending.time)
    }
    is EventEditorMessage.EndDate -> state.copy(endDate = message.value, dialog = null)
    is EventEditorMessage.EndTime -> state.copy(endTime = message.value, dialog = null)
    is EventEditorMessage.Recurrence -> state.copy(recurrence = message.value, repeatInterval = state.repeatInterval.withTextIfChanged((message.value?.interval ?: 1).toString()),
        repeatDay = state.repeatDay.withTextIfChanged((message.value?.dayOfMonth ?: state.dueDate.day).toString()), repeatCount = state.repeatCount.withTextIfChanged((message.value?.occurrenceCount ?: 10).toString()), dialog = null)
    is EventEditorMessage.GroupSelected -> state.copy(groupId = message.value)
    is EventEditorMessage.GroupsLoaded -> state.copy(groups = message.groups, isLoadingGroups = false, groupsLoadError = null,
        groupId = state.groupId?.takeIf { id -> message.groups.any { it.id == id } })
    EventEditorMessage.GroupsLoading -> state.copy(isLoadingGroups = true, groupsLoadError = null)
    EventEditorMessage.GroupsLoadFailed -> state.copy(isLoadingGroups = false, groupsLoadError = "Couldn't load groups. Please try again.")
    is EventEditorMessage.Today -> state.copy(today = message.value)
    is EventEditorMessage.Dialog -> state.copy(dialog = message.value)
    EventEditorMessage.Loading -> state.copy(isLoading = true, loadError = null)
    EventEditorMessage.LoadFailed -> state.copy(isLoading = false, loadError = "Couldn't load this event. Please try again.")
    EventEditorMessage.Saving -> state.copy(isSaving = true, dialog = null)
    EventEditorMessage.Finished -> state.copy(isSaving = false, isFinished = true)
    is EventEditorMessage.Failed -> state.copy(isSaving = false, dialog = EventEditorDialog.Error(message.message))
}
