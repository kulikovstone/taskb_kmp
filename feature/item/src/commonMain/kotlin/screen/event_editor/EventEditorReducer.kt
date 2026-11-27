package com.mkulikov.taskb.feature.item.screen.event_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemRecurrence
import com.mkulikov.taskb.feature.item.api.model.endDateTime
import com.mkulikov.taskb.feature.item.api.model.eventEndDateTime
import com.mkulikov.taskb.ui.input.withTextIfChanged
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

internal sealed interface EventEditorMessage {
    data class Loaded(val event: Event?) : EventEditorMessage

    data class Date(val value: LocalDate) : EventEditorMessage

    data class StartTime(val value: LocalTime?) : EventEditorMessage

    data class Duration(val value: Int) : EventEditorMessage

    data class EndDate(val value: LocalDate) : EventEditorMessage

    data class EndTime(val value: LocalTime) : EventEditorMessage

    data class Recurrence(val value: ItemRecurrence?) : EventEditorMessage

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

internal fun reduceEventEditor(state: EventEditorState, message: EventEditorMessage): EventEditorState {
    return when (message) {
        is EventEditorMessage.Loaded -> {
            val event = message.event?.let { event ->
                if (event.startTime != null) {
                    event.copy(durationMinutes = event.durationMinutes?.takeIf { minutes -> minutes > 0 } ?: 60)
                } else {
                    event
                }
            }
            if (event == null) {
                state.copy(isLoading = false, loadError = "This event no longer exists.")
            } else {
                state.copy(
                    isLoading = false,
                    original = event,
                    name = TextFieldState(initialText = event.name),
                    description = TextFieldState(initialText = event.description),
                    dueDate = event.dueDate ?: state.today,
                    endDate = event.endDate ?: event.dueDate ?: state.today,
                    startTime = event.startTime,
                    endTime = event.endDateTime?.time,
                    recurrence = event.recurrence,
                    repeatInterval = TextFieldState(initialText = (event.recurrence?.interval ?: 1).toString()),
                    repeatDay = TextFieldState(
                        initialText = (event.recurrence?.dayOfMonth ?: event.dueDate?.day ?: 1).toString()
                    ),
                    repeatCount = TextFieldState(initialText = (event.recurrence?.occurrenceCount ?: 10).toString()),
                    groupId = event.groupId?.takeIf { id ->
                        state.isLoadingGroups ||
                            state.groupsLoadError != null ||
                            state.groups.any { group -> group.id == id }
                    },
                )
            }
        }
        is EventEditorMessage.Date -> state.copy(
            dueDate = message.value,
            endDate = message.value.plus(
                value = (state.endDate.toEpochDays() - state.dueDate.toEpochDays()).coerceAtLeast(
                    minimumValue = 0
                ),
                unit = DateTimeUnit.DAY,
            ),
            dialog = null,
        )
        is EventEditorMessage.StartTime -> if (message.value == null) {
            state.copy(startTime = null, endTime = null, dialog = null)
        } else {
            val ending = eventEndDateTime(
                date = state.dueDate,
                time = message.value,
                durationMinutes = state.editedDurationMinutes ?: 30,
            )
            state.copy(
                startTime = message.value,
                endTime = ending.time,
                endDate = if (state.startTime == null) maxOf(a = state.endDate, b = ending.date) else ending.date,
                dialog = null,
            )
        }
        is EventEditorMessage.Duration -> {
            val ending = eventEndDateTime(
                date = state.dueDate,
                time = requireNotNull(value = state.startTime),
                durationMinutes = message.value,
            )
            state.copy(endDate = ending.date, endTime = ending.time)
        }
        is EventEditorMessage.EndDate -> state.copy(endDate = message.value, dialog = null)
        is EventEditorMessage.EndTime -> state.copy(endTime = message.value, dialog = null)
        is EventEditorMessage.Recurrence -> state.copy(
            recurrence = message.value,
            repeatInterval = state.repeatInterval.withTextIfChanged(
                text = (message.value?.interval ?: 1).toString(),
            ),
            repeatDay = state.repeatDay.withTextIfChanged(
                text = (message.value?.dayOfMonth ?: state.dueDate.day).toString()
            ),
            repeatCount = state.repeatCount.withTextIfChanged(
                text = (message.value?.occurrenceCount ?: 10).toString(),
            ),
            dialog = null,
        )
        is EventEditorMessage.GroupSelected -> state.copy(groupId = message.value)
        is EventEditorMessage.GroupsLoaded -> state.copy(
            groups = message.groups,
            isLoadingGroups = false,
            groupsLoadError = null,
            groupId = state.groupId?.takeIf { id -> message.groups.any { group -> group.id == id } },
        )
        EventEditorMessage.GroupsLoading -> state.copy(isLoadingGroups = true, groupsLoadError = null)
        EventEditorMessage.GroupsLoadFailed -> state.copy(
            isLoadingGroups = false,
            groupsLoadError = "Couldn't load groups. Please try again.",
        )
        is EventEditorMessage.Today -> state.copy(today = message.value)
        is EventEditorMessage.Dialog -> state.copy(dialog = message.value)
        EventEditorMessage.Loading -> state.copy(isLoading = true, loadError = null)
        EventEditorMessage.LoadFailed -> state.copy(
            isLoading = false,
            loadError = "Couldn't load this event. Please try again.",
        )
        EventEditorMessage.Saving -> state.copy(isSaving = true, dialog = null)
        EventEditorMessage.Finished -> state.copy(isSaving = false, isFinished = true)
        is EventEditorMessage.Failed -> state.copy(
            isSaving = false,
            dialog = EventEditorDialog.Error(message = message.message),
        )
    }
}
