package com.mkulikov.taskb.feature.event.screen.event_editor

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.event.api.model.Event
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import com.mkulikov.taskb.feature.event.api.repository.IEventRepository
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.core.utils.time.currentDate
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
) : CoroutineStore<EventEditorIntent, EventEditorState, EventEditorMessage>(
    EventEditorState(isNew = eventId == null, isLoading = eventId != null, dueDate = initialDate ?: today(), groupId = initialGroup, today = today()), ::reduceEventEditor, dispatcher,
) {
    private var loadRequested = false
    private var groupObservation: Job? = null
    init {
        accept(EventEditorIntent.RetryGroups)
        if (eventId != null) accept(EventEditorIntent.RetryLoad)
        collectEffect(flow {
            while (true) {
                delay(60_000)
                emit(EventEditorMessage.Today(today()))
            }
        })
    }
    override fun handleIntent(intent: EventEditorIntent) {
        val current = state.value
        if (current.isFinished || current.isSaving) return
        when (intent) {
            EventEditorIntent.RetryGroups -> if (groupObservation?.isActive != true) {
                reduce(EventEditorMessage.GroupsLoading)
                groupObservation = collectEffect(groups.groups.map { EventEditorMessage.GroupsLoaded(it) as EventEditorMessage }.catch {
                    if (it is CancellationException) throw it
                    emit(EventEditorMessage.GroupsLoadFailed)
                })
            }
            EventEditorIntent.RetryLoad -> {
                if (eventId == null || current.isLoading && loadRequested || current.original != null) return
                loadRequested = true
                reduce(EventEditorMessage.Loading)
                launchEffect {
                    try { EventEditorMessage.Loaded(if (occurrenceId == null) repository.getById(eventId) else repository.getOccurrence(eventId, occurrenceId)) }
                    catch (exception: CancellationException) { throw exception }
                    catch (_: Exception) { EventEditorMessage.LoadFailed }
                }
            }
            EventEditorIntent.DismissDialog -> reduce(EventEditorMessage.Dialog(null))
            is EventEditorIntent.SaveOccurrence -> if (current.dialog == EventEditorDialog.MoveOccurrence && current.copy(dialog = null).canSave) save(current, intent.scope)
            is EventEditorIntent.DeleteOccurrence -> if (current.dialog == EventEditorDialog.DeleteOccurrence && eventId != null) {
                reduce(EventEditorMessage.Saving)
                launchEffect { recover("Couldn't delete this event. Please try again.") {
                    if (repository.deleteOccurrence(eventId, occurrenceId, intent.scope)) EventEditorMessage.Finished else EventEditorMessage.Failed("This event no longer exists.")
                } }
            }
            is EventEditorIntent.RepeatEndDateChanged -> if (current.dialog == EventEditorDialog.RepeatEndDatePicker) current.editedRecurrence?.let {
                reduce(EventEditorMessage.Recurrence(it.copy(endDate = intent.date, occurrenceCount = null)))
            }
            is EventEditorIntent.StartTimeChanged -> if (!current.isBusy && current.loadError == null &&
                (current.dialog == null || current.dialog == EventEditorDialog.StartTimePicker)) reduce(EventEditorMessage.StartTime(intent.time))
            is EventEditorIntent.EventEndDateChanged -> if (current.dialog == EventEditorDialog.EventEndDatePicker)
                reduce(EventEditorMessage.EndDate(intent.date))
            is EventEditorIntent.EndTimeChanged -> if (current.dialog == EventEditorDialog.EndTimePicker)
                reduce(EventEditorMessage.EndTime(intent.time))
            EventEditorIntent.ConfirmDelete -> if (current.dialog == EventEditorDialog.ConfirmDelete && eventId != null) {
                reduce(EventEditorMessage.Saving)
                launchEffect { recover("Couldn't delete this event. Please try again.") { repository.delete(eventId); EventEditorMessage.Finished } }
            }
            is EventEditorIntent.DateChanged -> if (!current.isLoading && current.loadError == null && (current.dialog == null || current.dialog == EventEditorDialog.DatePicker)) reduce(EventEditorMessage.Date(intent.date))
            else -> {
                if (current.isBusy || current.loadError != null || current.groupsLoadError != null || current.dialog != null) return
                when (intent) {
                    EventEditorIntent.ChooseStartTime -> reduce(EventEditorMessage.Dialog(EventEditorDialog.StartTimePicker))
                    is EventEditorIntent.DurationChanged -> if (current.startTime != null && current.endDate == current.dueDate && intent.minutes > 0) reduce(EventEditorMessage.Duration(intent.minutes))
                    EventEditorIntent.ChooseEventEndDate -> reduce(EventEditorMessage.Dialog(EventEditorDialog.EventEndDatePicker))
                    EventEditorIntent.ChooseEndTime -> if (current.startTime != null) reduce(EventEditorMessage.Dialog(EventEditorDialog.EndTimePicker))
                    is EventEditorIntent.GroupChanged -> if (intent.id == null || current.groups.any { it.id == intent.id }) {
                        reduce(EventEditorMessage.GroupSelected(intent.id))
                        onGroupSelected(intent.id)
                    }
                    is EventEditorIntent.RecurrenceChanged -> reduce(EventEditorMessage.Recurrence(intent.recurrence))
                    EventEditorIntent.ChooseRepeatEndDate -> if (current.recurrence != null) reduce(EventEditorMessage.Dialog(EventEditorDialog.RepeatEndDatePicker))
                    EventEditorIntent.ChooseDate -> reduce(EventEditorMessage.Dialog(EventEditorDialog.DatePicker))
                    EventEditorIntent.PrepareDelete -> if (current.original != null) reduce(EventEditorMessage.Dialog(if (occurrenceId != null) EventEditorDialog.DeleteOccurrence else EventEditorDialog.ConfirmDelete))
                    EventEditorIntent.Save -> if (current.canSave) {
                        val original = current.original
                        if (occurrenceId != null && original != null && original.recurrence == current.editedRecurrence &&
                            (original.dueDate != current.dueDate || original.startTime != current.startTime || original.durationMinutes != current.editedDurationMinutes || original.durationDays != current.savedDurationDays))
                            reduce(EventEditorMessage.Dialog(EventEditorDialog.MoveOccurrence))
                        else save(current, EventChangeScope.ThisAndFollowing)
                    }
                }
            }
        }
    }
    private fun save(current: EventEditorState, scope: EventChangeScope) {
        reduce(EventEditorMessage.Saving)
        val event = (current.original ?: Event(name = "")).copy(name = current.normalizedName,
            description = current.description.text.toString(), dueDate = current.dueDate, groupId = current.groupId,
            recurrence = current.editedRecurrence, startTime = current.startTime, durationMinutes = current.editedDurationMinutes, durationDays = requireNotNull(current.savedDurationDays))
        launchEffect { recover("Couldn't save this event. Please try again.") {
            if (repository.saveOccurrence(event, scope)) EventEditorMessage.Finished else EventEditorMessage.Failed("This event has changed or no longer exists. Reopen it and try again.")
        } }
    }
    private suspend fun recover(message: String, block: suspend () -> EventEditorMessage): EventEditorMessage = try { block() }
        catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { EventEditorMessage.Failed(message) }
}
