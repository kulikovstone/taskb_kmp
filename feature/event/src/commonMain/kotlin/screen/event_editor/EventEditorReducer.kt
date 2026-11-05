package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.group.api.model.Group
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.mkulikov.taskb.feature.event.api.model.EventRecurrence

internal sealed interface EventEditorMessage {
    data class Loaded(val event: Event?) : EventEditorMessage
    data class Date(val value: LocalDate?) : EventEditorMessage
    data class StartTime(val value: LocalTime?) : EventEditorMessage
    data class Duration(val value: Int) : EventEditorMessage
    data object CustomDuration : EventEditorMessage
    data class DurationMode(val value: EventDurationMode) : EventEditorMessage
    data class DurationInput(val days: String? = null, val hours: String? = null, val minutes: String? = null) : EventEditorMessage
    data class EndDate(val value: LocalDate) : EventEditorMessage
    data class EndTime(val value: LocalTime) : EventEditorMessage
    data class Recurrence(val value: EventRecurrence?) : EventEditorMessage
    data class RepeatInput(val interval: String? = null, val day: String? = null, val count: String? = null) : EventEditorMessage
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
            if (it.startTime != null && it.dueDate != null) it.copy(durationMinutes = it.durationMinutes?.takeIf { minutes -> minutes > 0 } ?: 60) else it
        }
        if (event == null) state.copy(isLoading = false, loadError = "This event no longer exists.")
        else state.copy(isLoading = false, original = event, name = TextFieldState(event.name),
            description = TextFieldState(event.description), dueDate = event.dueDate,
            startTime = event.startTime, durationMinutes = event.durationMinutes,
            recurrence = event.recurrence, repeatInterval = (event.recurrence?.interval ?: 1).toString(),
            repeatDay = (event.recurrence?.dayOfMonth ?: event.dueDate?.day ?: 1).toString(), repeatCount = (event.recurrence?.occurrenceCount ?: 10).toString(),
            groupId = event.groupId?.takeIf { id -> state.isLoadingGroups || state.groupsLoadError != null || state.groups.any { it.id == id } })
            .let { loaded -> if (loaded.durationMinutes != null && loaded.durationMinutes !in listOf(15, 30, 60)) reduceEventEditor(loaded, EventEditorMessage.CustomDuration) else loaded }
    }
    is EventEditorMessage.Date -> state.copy(dueDate = message.value, recurrence = state.recurrence.takeIf { message.value != null },
        startTime = state.startTime.takeIf { message.value != null }, durationMinutes = state.durationMinutes.takeIf { message.value != null },
        customDuration = state.customDuration && message.value != null, dialog = null)
    is EventEditorMessage.StartTime -> state.copy(startTime = message.value,
        durationMinutes = if (message.value == null) null else state.durationMinutes?.takeIf { it > 0 } ?: 60,
        customDuration = state.customDuration && message.value != null, dialog = null)
    is EventEditorMessage.Duration -> state.copy(durationMinutes = message.value, customDuration = false)
    EventEditorMessage.CustomDuration -> {
        val minutes = state.editedDurationMinutes ?: 60
        val ending = state.copy(durationMinutes = minutes, customDuration = false).ending
        state.copy(customDuration = true, durationDays = (minutes / 1440).toString(), durationHours = (minutes % 1440 / 60).toString(),
            durationMinutePart = (minutes % 60).toString(), endDate = ending?.date ?: state.dueDate, endTime = ending?.time ?: state.startTime)
    }
    is EventEditorMessage.DurationMode -> {
        val prepared = reduceEventEditor(state, EventEditorMessage.CustomDuration)
        prepared.copy(durationMode = message.value)
    }
    is EventEditorMessage.DurationInput -> state.copy(durationDays = message.days ?: state.durationDays,
        durationHours = message.hours ?: state.durationHours, durationMinutePart = message.minutes ?: state.durationMinutePart)
    is EventEditorMessage.EndDate -> state.copy(endDate = message.value, dialog = null)
    is EventEditorMessage.EndTime -> state.copy(endTime = message.value, dialog = null)
    is EventEditorMessage.Recurrence -> state.copy(recurrence = message.value, repeatInterval = (message.value?.interval ?: 1).toString(),
        repeatDay = (message.value?.dayOfMonth ?: state.dueDate?.day ?: 1).toString(), repeatCount = (message.value?.occurrenceCount ?: 10).toString(), dialog = null)
    is EventEditorMessage.RepeatInput -> state.copy(repeatInterval = message.interval ?: state.repeatInterval, repeatDay = message.day ?: state.repeatDay, repeatCount = message.count ?: state.repeatCount)
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
