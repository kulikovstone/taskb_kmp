package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.foundation.text.input.TextFieldState
import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

internal class EventEditorStore(
    private val repository: IEventRepository,
    private val groups: IGroupRepository,
    private val eventId: Int? = null,
    initialDate: LocalDate? = null,
    initialGroup: Int? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    today: () -> LocalDate = ::currentDate,
    private val occurrenceId: EventOccurrenceId? = null,
    private val onGroupSelected: (Int?) -> Unit = {},
    name: TextFieldState = TextFieldState(),
    description: TextFieldState = TextFieldState(),
) : CoroutineStore<EventEditorIntent, EventEditorState, EventEditorMessage>(
    initialState = EventEditorState(
        isNew = eventId == null,
        isLoading = eventId != null,
        dueDate = initialDate ?: today(),
        groupId = initialGroup,
        today = today(),
        name = name,
        description = description,
    ),
    reducer = ::reduceEventEditor,
    context = dispatcher,
) {
    private var loadRequested = false
    private var groupObservation: Job? = null

    init {
        accept(intent = EventEditorIntent.RetryGroups)
        if (eventId != null) accept(intent = EventEditorIntent.RetryLoad)
        collectEffect(
            messages = flow {
                while (true) {
                    delay(timeMillis = 60_000)
                    emit(value = EventEditorMessage.Today(value = today()))
                }
            }
        )
    }

    override fun handleIntent(intent: EventEditorIntent) {
        val current = state.value
        if (current.isFinished || current.isSaving) return
        when (intent) {
            EventEditorIntent.RetryGroups -> if (groupObservation?.isActive != true) {
                reduce(message = EventEditorMessage.GroupsLoading)
                groupObservation = collectEffect(
                    messages = groups.groups
                        .map { groups -> EventEditorMessage.GroupsLoaded(groups = groups) as EventEditorMessage }
                        .catch { exception ->
                            if (exception is CancellationException) throw exception
                            emit(value = EventEditorMessage.GroupsLoadFailed)
                        }
                )
            }
            EventEditorIntent.RetryLoad -> {
                if (eventId == null || current.isLoading && loadRequested || current.original != null) return
                loadRequested = true
                reduce(message = EventEditorMessage.Loading)
                launchEffect {
                    try {
                        EventEditorMessage.Loaded(
                            event = if (occurrenceId == null) {
                                repository.getById(id = eventId)
                            } else {
                                repository.getOccurrence(id = eventId, occurrence = occurrenceId)
                            }
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        EventEditorMessage.LoadFailed
                    }
                }
            }
            EventEditorIntent.DismissDialog -> reduce(message = EventEditorMessage.Dialog(value = null))
            is EventEditorIntent.SaveOccurrence ->
                if (current.dialog == EventEditorDialog.MoveOccurrence && current.copy(dialog = null).canSave)
                    save(current = current, scope = intent.scope)
            is EventEditorIntent.DeleteOccurrence ->
                if (current.dialog == EventEditorDialog.DeleteOccurrence && eventId != null) {
                    reduce(message = EventEditorMessage.Saving)
                    launchEffect {
                        recover(message = "Couldn't delete this event. Please try again.") {
                            if (
                                repository.deleteOccurrence(
                                    id = eventId,
                                    occurrence = occurrenceId,
                                    scope = intent.scope,
                                )
                            ) {
                                EventEditorMessage.Finished
                            } else {
                                EventEditorMessage.Failed(message = "This event no longer exists.")
                            }
                        }
                    }
                }
            is EventEditorIntent.RepeatEndDateChanged -> if (current.dialog == EventEditorDialog.RepeatEndDatePicker)
                current.editedRecurrence?.let { recurrence ->
                    reduce(
                        message = EventEditorMessage.Recurrence(
                            value = recurrence.copy(endDate = intent.date, occurrenceCount = null)
                        )
                    )
                }
            is EventEditorIntent.StartTimeChanged -> if (
                !current.isBusy &&
                    current.loadError == null &&
                    (current.dialog == null || current.dialog == EventEditorDialog.StartTimePicker)
            )
                reduce(message = EventEditorMessage.StartTime(value = intent.time))
            is EventEditorIntent.EventEndDateChanged -> if (current.dialog == EventEditorDialog.EventEndDatePicker)
                reduce(message = EventEditorMessage.EndDate(value = intent.date))
            is EventEditorIntent.EndTimeChanged -> if (current.dialog == EventEditorDialog.EndTimePicker)
                reduce(message = EventEditorMessage.EndTime(value = intent.time))
            EventEditorIntent.ConfirmDelete ->
                if (current.dialog == EventEditorDialog.ConfirmDelete && eventId != null) {
                    reduce(message = EventEditorMessage.Saving)
                    launchEffect {
                        recover(message = "Couldn't delete this event. Please try again.") {
                            repository.delete(id = eventId)
                            EventEditorMessage.Finished
                        }
                    }
                }
            is EventEditorIntent.DateChanged -> if (
                !current.isLoading &&
                    current.loadError == null &&
                    (current.dialog == null || current.dialog == EventEditorDialog.DatePicker)
            )
                reduce(message = EventEditorMessage.Date(value = intent.date))
            else -> {
                if (
                    current.isBusy ||
                        current.loadError != null ||
                        current.groupsLoadError != null ||
                        current.dialog != null
                )
                    return
                when (intent) {
                    EventEditorIntent.ChooseStartTime -> reduce(
                        message = EventEditorMessage.Dialog(value = EventEditorDialog.StartTimePicker),
                    )
                    is EventEditorIntent.DurationChanged ->
                        if (current.startTime != null && current.endDate == current.dueDate && intent.minutes > 0)
                            reduce(message = EventEditorMessage.Duration(value = intent.minutes))
                    EventEditorIntent.ChooseEventEndDate -> reduce(
                        message = EventEditorMessage.Dialog(value = EventEditorDialog.EventEndDatePicker),
                    )
                    EventEditorIntent.ChooseEndTime -> if (current.startTime != null)
                        reduce(message = EventEditorMessage.Dialog(value = EventEditorDialog.EndTimePicker))
                    is EventEditorIntent.GroupChanged ->
                        if (intent.id == null || current.groups.any { group -> group.id == intent.id }) {
                            reduce(message = EventEditorMessage.GroupSelected(value = intent.id))
                            onGroupSelected(intent.id)
                        }
                    is EventEditorIntent.RecurrenceChanged -> reduce(
                        message = EventEditorMessage.Recurrence(value = intent.recurrence),
                    )
                    EventEditorIntent.ChooseRepeatEndDate -> if (current.recurrence != null)
                        reduce(
                            message = EventEditorMessage.Dialog(value = EventEditorDialog.RepeatEndDatePicker)
                        )
                    EventEditorIntent.ChooseDate -> reduce(
                        message = EventEditorMessage.Dialog(value = EventEditorDialog.DatePicker),
                    )
                    EventEditorIntent.PrepareDelete -> if (current.original != null)
                        reduce(
                            message = EventEditorMessage.Dialog(
                                value = if (occurrenceId != null) {
                                    EventEditorDialog.DeleteOccurrence
                                } else {
                                    EventEditorDialog.ConfirmDelete
                                }
                            )
                        )
                    is EventEditorIntent.SaveNew -> if (current.isNew && current.canSave) {
                        val groupId = intent.groupId?.takeIf { id -> current.groups.any { group -> group.id == id } }
                        save(current = current.copy(groupId = groupId), scope = EventChangeScope.ThisAndFollowing)
                    }
                    EventEditorIntent.Save -> if (current.canSave) {
                        val original = current.original
                        if (
                            occurrenceId != null &&
                                original != null &&
                                original.recurrence == current.editedRecurrence &&
                                (original.dueDate != current.dueDate ||
                                    original.startTime != current.startTime ||
                                    original.durationMinutes != current.editedDurationMinutes ||
                                    original.durationDays != current.savedDurationDays)
                        ) {
                            reduce(
                                message = EventEditorMessage.Dialog(value = EventEditorDialog.MoveOccurrence)
                            )
                        } else {
                            save(current = current, scope = EventChangeScope.ThisAndFollowing)
                        }
                    }
                }
            }
        }
    }

    private fun save(current: EventEditorState, scope: EventChangeScope) {
        reduce(message = EventEditorMessage.Saving)
        val event = (current.original ?: Event(name = "")).copy(
            name = current.normalizedName,
            description = current.description.text.toString(),
            dueDate = current.dueDate,
            groupId = current.groupId,
            recurrence = current.editedRecurrence,
            startTime = current.startTime,
            durationMinutes = current.editedDurationMinutes,
            durationDays = requireNotNull(value = current.savedDurationDays),
        )
        launchEffect {
            recover(message = "Couldn't save this event. Please try again.") {
                if (repository.saveOccurrence(event = event, scope = scope)) {
                    EventEditorMessage.Finished
                } else {
                    EventEditorMessage.Failed(
                        message = "This event has changed or no longer exists. Reopen it and try again."
                    )
                }
            }
        }
    }

    private suspend fun recover(message: String, block: suspend () -> EventEditorMessage): EventEditorMessage {
        return try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            EventEditorMessage.Failed(message = message)
        }
    }
}
