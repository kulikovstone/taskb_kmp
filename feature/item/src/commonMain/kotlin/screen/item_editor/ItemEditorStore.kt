package com.mkulikov.taskb.feature.item.screen.item_editor

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.item.api.ItemEditorRoute
import com.mkulikov.taskb.feature.item.api.model.Event
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.feature.item.api.model.ItemType
import com.mkulikov.taskb.feature.item.api.model.Task
import com.mkulikov.taskb.feature.item.api.model.eventEndDateTime
import com.mkulikov.taskb.feature.item.api.repository.IEventRepository
import com.mkulikov.taskb.feature.item.api.repository.ITaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

internal class ItemEditorStore(
    private val tasks: ITaskRepository,
    private val events: IEventRepository,
    private val groups: IGroupRepository,
    private val route: ItemEditorRoute,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    today: () -> LocalDate = ::currentDate,
) : CoroutineStore<ItemEditorIntent, ItemEditorState, ItemEditorMessage>(
    initialState = initialEditorState(route = route, today = today()),
    reducer = ::reduceItemEditor,
    context = dispatcher,
) {
    private var groupObservation: Job? = null
    private var loadRequested = false
    private val existingItems = when (route.type) {
        ItemType.Task -> tasks
        ItemType.Event -> events
    }

    init {
        accept(intent = ItemEditorIntent.RetryGroups)
        if (route.itemId != null) accept(intent = ItemEditorIntent.RetryLoad)
        collectEffect(
            messages = flow {
                while (true) {
                    delay(timeMillis = 60_000)
                    emit(value = ItemEditorMessage.Today(value = today()))
                }
            }
        )
    }

    override fun handleIntent(intent: ItemEditorIntent) {
        val current = state.value
        if (current.isFinished || current.isSaving) return
        val schedule = current.schedule
        val details = schedule.details
        val dialog = current.dialog
        when (intent) {
            ItemEditorIntent.RetryGroups -> observeGroups()
            ItemEditorIntent.RetryLoad -> loadItem()
            ItemEditorIntent.DismissDialog -> show(value = null)
            ItemEditorIntent.ConfirmDelete -> if (dialog == ItemEditorDialog.ConfirmDelete) delete(scope = null)
            is ItemEditorIntent.DeleteOccurrence -> if (dialog == ItemEditorDialog.DeleteOccurrence) {
                delete(scope = intent.scope)
            }
            is ItemEditorIntent.SaveOccurrence -> if (
                dialog == ItemEditorDialog.MoveOccurrence && current.copy(dialog = null).canSave
            ) {
                save(current = current, scope = intent.scope)
            }
            is ItemEditorIntent.DateChanged -> if (
                !current.isLoading && current.loadError == null &&
                (dialog == null || dialog == ItemEditorDialog.DatePicker)
            ) {
                val date = intent.date
                when (details) {
                    is TaskSchedule -> {
                        val dated = schedule.copy(details = details.copy(dueDate = date))
                        change(value = if (date == null) dated.withRecurrence(rule = null) else dated)
                    }
                    is EventSchedule -> if (date != null) update(value = details.startingOn(date = date))
                }
            }
            is ItemEditorIntent.RepeatEndDateChanged -> if (dialog == ItemEditorDialog.RepeatEndDatePicker) {
                val rule = schedule.editedRecurrence ?: return
                change(value = schedule.withRecurrence(rule = rule.copy(endDate = intent.date, occurrenceCount = null)))
            }
            is ItemEditorIntent.StartTimeChanged -> if (
                details is EventSchedule && !current.isBusy && current.loadError == null &&
                (dialog == null || dialog == ItemEditorDialog.StartTimePicker)
            ) {
                update(value = details.startingAt(time = intent.time))
            }
            is ItemEditorIntent.EventEndDateChanged -> if (
                details is EventSchedule && dialog == ItemEditorDialog.EventEndDatePicker
            ) {
                update(value = details.copy(endDate = intent.date))
            }
            is ItemEditorIntent.EndTimeChanged -> if (
                details is EventSchedule && dialog == ItemEditorDialog.EndTimePicker
            ) {
                update(value = details.copy(endTime = intent.time))
            }
            is ItemEditorIntent.FormAction -> if (current.enabled) handleFormAction(intent = intent, current = current)
        }
    }

    private fun handleFormAction(intent: ItemEditorIntent.FormAction, current: ItemEditorState) {
        val schedule = current.schedule
        val details = schedule.details
        when (intent) {
            is ItemEditorIntent.SelectType -> if (current.isNew && current.type != intent.type) {
                val date = if (intent.type == ItemType.Task) route.initialDate else schedule.dueDate
                val next = current.inactiveSchedules[intent.type]
                    ?: newSchedule(type = intent.type, date = date, today = current.today)
                reduce(message = ItemEditorMessage.SwitchSchedule(value = next))
            }
            is ItemEditorIntent.CalendarVisibilityChanged -> if (
                details is TaskSchedule && schedule.recurrence != null
            ) {
                update(value = details.copy(showInCalendar = intent.showInCalendar))
            }
            is ItemEditorIntent.QuickDeleteChanged -> if (details is TaskSchedule && schedule.recurrence != null) {
                update(value = details.copy(quickDelete = intent.enabled))
            }
            ItemEditorIntent.ChooseStartTime -> if (details is EventSchedule) {
                show(value = ItemEditorDialog.StartTimePicker)
            }
            ItemEditorIntent.ChooseEventEndDate -> if (details is EventSchedule) {
                show(value = ItemEditorDialog.EventEndDatePicker)
            }
            ItemEditorIntent.ChooseEndTime -> if (details is EventSchedule && details.startTime != null) {
                show(value = ItemEditorDialog.EndTimePicker)
            }
            is ItemEditorIntent.DurationChanged -> if (
                details is EventSchedule && details.startTime != null &&
                    details.endDate == details.dueDate && intent.minutes > 0
            ) {
                val end = eventEndDateTime(
                    date = details.dueDate,
                    time = details.startTime,
                    durationMinutes = intent.minutes,
                )
                update(value = details.copy(endDate = end.date, endTime = end.time))
            }
            is ItemEditorIntent.GroupChanged -> if (
                intent.id == null || current.groups.any { group -> group.id == intent.id }
            ) {
                reduce(message = ItemEditorMessage.GroupSelected(value = intent.id))
            }
            is ItemEditorIntent.RecurrenceChanged -> if (schedule.dueDate != null || intent.recurrence == null) {
                change(value = schedule.withRecurrence(rule = intent.recurrence))
            }
            ItemEditorIntent.ChooseRepeatEndDate -> if (schedule.recurrence != null) {
                show(value = ItemEditorDialog.RepeatEndDatePicker)
            }
            ItemEditorIntent.ChooseDate -> show(value = ItemEditorDialog.DatePicker)
            ItemEditorIntent.PrepareDelete -> if (current.original != null) {
                val choice = if (route.occurrenceId == null) {
                    ItemEditorDialog.ConfirmDelete
                } else {
                    ItemEditorDialog.DeleteOccurrence
                }
                show(value = choice)
            }
            ItemEditorIntent.Save -> if (current.canSave) {
                if (current.needsMoveScope) {
                    show(value = ItemEditorDialog.MoveOccurrence)
                } else {
                    save(current = current, scope = ItemChangeScope.ThisAndFollowing)
                }
            }
        }
    }

    private fun change(value: ScheduleDraft) = reduce(message = ItemEditorMessage.Schedule(value = value))

    private fun update(value: ScheduleDetails) = change(value = state.value.schedule.copy(details = value))

    private fun show(value: ItemEditorDialog?) = reduce(message = ItemEditorMessage.Dialog(value = value))

    private fun observeGroups() {
        if (groupObservation?.isActive == true) return
        reduce(message = ItemEditorMessage.GroupsLoading)
        groupObservation = collectEffect(
            messages = groups.groups
                .map<List<Group>, ItemEditorMessage>(transform = ItemEditorMessage::GroupsLoaded)
                .catch { exception ->
                    if (exception is CancellationException) throw exception
                    emit(value = ItemEditorMessage.GroupsLoadFailed)
                }
        )
    }

    private fun loadItem() {
        val id = route.itemId ?: return
        if (state.value.original != null || state.value.isLoading && loadRequested) return
        loadRequested = true
        reduce(message = ItemEditorMessage.Loading)
        launchEffect {
            try {
                val occurrence = route.occurrenceId
                val item = if (occurrence == null) {
                    existingItems.getById(id = id)
                } else {
                    existingItems.getOccurrence(id = id, occurrence = occurrence)
                }
                ItemEditorMessage.Loaded(item = item)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                ItemEditorMessage.LoadFailed
            }
        }
    }

    private fun delete(scope: ItemChangeScope?) {
        val id = route.itemId ?: return
        val label = route.type.name.lowercase()
        write(error = "Couldn't delete this $label. Please try again.") {
            if (scope == null) {
                existingItems.delete(id = id)
                ItemEditorMessage.Finished
            } else if (existingItems.deleteOccurrence(id = id, occurrence = route.occurrenceId, scope = scope)) {
                ItemEditorMessage.Finished
            } else {
                ItemEditorMessage.Failed(message = "This $label no longer exists.")
            }
        }
    }

    private fun save(current: ItemEditorState, scope: ItemChangeScope) {
        val item = current.editedItem()
        val label = item.type.name.lowercase()
        write(error = "Couldn't save this $label. Please try again.") {
            val saved = when (item) {
                is Task -> tasks.saveOccurrence(task = item, scope = scope)
                is Event -> events.saveOccurrence(event = item, scope = scope)
            }
            if (saved) {
                ItemEditorMessage.Finished
            } else {
                ItemEditorMessage.Failed(
                    message = "This $label has changed or no longer exists. Reopen it and try again."
                )
            }
        }
    }

    private fun write(error: String, block: suspend () -> ItemEditorMessage) {
        reduce(message = ItemEditorMessage.Saving)
        launchEffect {
            try {
                block()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                ItemEditorMessage.Failed(message = error)
            }
        }
    }
}

private fun initialEditorState(route: ItemEditorRoute, today: LocalDate): ItemEditorState = ItemEditorState(
    isNew = route.itemId == null,
    isLoading = route.itemId != null,
    schedule = newSchedule(type = route.type, date = route.initialDate, today = today),
    groupId = route.initialGroupId,
    today = today,
)

private fun newSchedule(type: ItemType, date: LocalDate?, today: LocalDate): ScheduleDraft = when (type) {
    ItemType.Task -> ScheduleDraft(details = TaskSchedule(dueDate = date))
    ItemType.Event -> ScheduleDraft(details = EventSchedule(dueDate = date ?: today))
}
