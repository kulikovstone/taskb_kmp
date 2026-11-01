package com.mkulikov.taskb.feature.task.screen.task_editor

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal class TaskEditorStore(
    private val repository: ITaskRepository,
    private val groups: IGroupRepository,
    private val taskId: Int? = null,
    initialDate: LocalDate? = null,
    initialGroup: Int? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    today: () -> LocalDate = ::currentDate,
    private val occurrenceId: TaskOccurrenceId? = null,
) : CoroutineStore<TaskEditorIntent, TaskEditorState, TaskEditorMessage>(
    TaskEditorState(isNew = taskId == null, isLoading = taskId != null, dueDate = initialDate, groupId = initialGroup, today = today()), ::reduceTaskEditor, dispatcher,
) {
    private var loadRequested = false
    private var groupObservation: Job? = null
    init {
        accept(TaskEditorIntent.RetryGroups)
        if (taskId != null) accept(TaskEditorIntent.RetryLoad)
        collectEffect(flow {
            while (true) {
                delay(60_000)
                emit(TaskEditorMessage.Today(today()))
            }
        })
    }
    override fun handleIntent(intent: TaskEditorIntent) {
        val current = state.value
        if (current.isFinished || current.isSaving) return
        when (intent) {
            TaskEditorIntent.RetryGroups -> if (groupObservation?.isActive != true) {
                reduce(TaskEditorMessage.GroupsLoading)
                groupObservation = collectEffect(groups.groups.map { TaskEditorMessage.GroupsLoaded(it) as TaskEditorMessage }.catch {
                    if (it is CancellationException) throw it
                    emit(TaskEditorMessage.GroupsLoadFailed)
                })
            }
            TaskEditorIntent.RetryLoad -> {
                if (taskId == null || current.isLoading && loadRequested || current.original != null) return
                loadRequested = true
                reduce(TaskEditorMessage.Loading)
                launchEffect {
                    try { TaskEditorMessage.Loaded(if (occurrenceId == null) repository.getById(taskId) else repository.getOccurrence(taskId, occurrenceId)) }
                    catch (exception: CancellationException) { throw exception }
                    catch (_: Exception) { TaskEditorMessage.LoadFailed }
                }
            }
            TaskEditorIntent.DismissDialog -> reduce(TaskEditorMessage.Dialog(null))
            is TaskEditorIntent.SaveOccurrence -> if (current.dialog == TaskEditorDialog.MoveOccurrence && current.copy(dialog = null).canSave) save(current, intent.scope)
            is TaskEditorIntent.DeleteOccurrence -> if (current.dialog == TaskEditorDialog.DeleteOccurrence && taskId != null) {
                reduce(TaskEditorMessage.Saving)
                launchEffect { recover("Couldn't delete this event. Please try again.") {
                    if (repository.deleteOccurrence(taskId, occurrenceId, intent.scope)) TaskEditorMessage.Finished else TaskEditorMessage.Failed("This event no longer exists.")
                } }
            }
            is TaskEditorIntent.RepeatEndDateChanged -> if (current.dialog == TaskEditorDialog.RepeatEndDatePicker) current.editedRecurrence?.let {
                reduce(TaskEditorMessage.Recurrence(it.copy(endDate = intent.date, occurrenceCount = null)))
            }
            TaskEditorIntent.ConfirmDelete -> if (current.dialog == TaskEditorDialog.ConfirmDelete && taskId != null) {
                reduce(TaskEditorMessage.Saving)
                launchEffect { recover("Couldn't delete this task. Please try again.") { repository.delete(taskId); TaskEditorMessage.Finished } }
            }
            is TaskEditorIntent.DateChanged -> if (!current.isLoading && current.loadError == null && (current.dialog == null || current.dialog == TaskEditorDialog.DatePicker)) reduce(TaskEditorMessage.Date(intent.date))
            else -> {
                if (current.isBusy || current.loadError != null || current.groupsLoadError != null || current.dialog != null) return
                when (intent) {
                    is TaskEditorIntent.GroupChanged -> if (intent.id == null || current.groups.any { it.id == intent.id }) reduce(TaskEditorMessage.GroupSelected(intent.id))
                    is TaskEditorIntent.RecurrenceChanged -> if (current.dueDate != null || intent.recurrence == null) reduce(TaskEditorMessage.Recurrence(intent.recurrence))
                    is TaskEditorIntent.RepeatIntervalChanged -> if (intent.value.all(Char::isDigit) && intent.value.length <= 4) reduce(TaskEditorMessage.RepeatInput(interval = intent.value))
                    is TaskEditorIntent.RepeatDayChanged -> if (intent.value.all(Char::isDigit) && intent.value.length <= 2) reduce(TaskEditorMessage.RepeatInput(day = intent.value))
                    is TaskEditorIntent.RepeatCountChanged -> if (intent.value.all(Char::isDigit) && intent.value.length <= 7) reduce(TaskEditorMessage.RepeatInput(count = intent.value))
                    TaskEditorIntent.ChooseRepeatEndDate -> if (current.recurrence != null) reduce(TaskEditorMessage.Dialog(TaskEditorDialog.RepeatEndDatePicker))
                    is TaskEditorIntent.ShiftDate -> current.dueDate?.let {
                        reduce(TaskEditorMessage.Date(it.plus(intent.days, DateTimeUnit.DAY).plus(intent.months, DateTimeUnit.MONTH)))
                    }
                    TaskEditorIntent.ChooseDate -> reduce(TaskEditorMessage.Dialog(TaskEditorDialog.DatePicker))
                    TaskEditorIntent.PrepareDelete -> if (current.original != null) reduce(TaskEditorMessage.Dialog(if (occurrenceId != null) TaskEditorDialog.DeleteOccurrence else TaskEditorDialog.ConfirmDelete))
                    TaskEditorIntent.Save -> if (current.canSave) {
                        if (occurrenceId != null && current.original?.dueDate != current.dueDate && current.original?.recurrence == current.editedRecurrence)
                            reduce(TaskEditorMessage.Dialog(TaskEditorDialog.MoveOccurrence))
                        else save(current, TaskChangeScope.ThisAndFollowing)
                    }
                }
            }
        }
    }
    private fun save(current: TaskEditorState, scope: TaskChangeScope) {
        reduce(TaskEditorMessage.Saving)
        val task = (current.original ?: Task(name = "")).copy(name = current.normalizedName,
            description = current.description.text.toString(), dueDate = current.dueDate, groupId = current.groupId,
            recurrence = current.editedRecurrence)
        launchEffect { recover("Couldn't save this task. Please try again.") {
            if (repository.saveOccurrence(task, scope)) TaskEditorMessage.Finished else TaskEditorMessage.Failed("This task has changed or no longer exists. Reopen it and try again.")
        } }
    }
    private suspend fun recover(message: String, block: suspend () -> TaskEditorMessage): TaskEditorMessage = try { block() }
        catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { TaskEditorMessage.Failed(message) }
}
