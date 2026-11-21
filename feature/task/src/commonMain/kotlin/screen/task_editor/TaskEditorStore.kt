package com.mkulikov.taskb.feature.task.screen.task_editor

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.task.api.model.Task
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.feature.task.api.model.TaskOccurrenceId
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

internal class TaskEditorStore(
    private val repository: ITaskRepository,
    private val groups: IGroupRepository,
    private val taskId: Int? = null,
    initialDate: LocalDate? = null,
    initialGroup: Int? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    today: () -> LocalDate = ::currentDate,
    private val occurrenceId: TaskOccurrenceId? = null,
    private val onGroupSelected: (Int?) -> Unit = {},
) : CoroutineStore<TaskEditorIntent, TaskEditorState, TaskEditorMessage>(
    initialState = TaskEditorState(
        isNew = taskId == null,
        isLoading = taskId != null,
        dueDate = initialDate,
        groupId = initialGroup,
        today = today(),
    ),
    reducer = ::reduceTaskEditor,
    context = dispatcher,
) {
    private var loadRequested = false
    private var groupObservation: Job? = null

    init {
        accept(intent = TaskEditorIntent.RetryGroups)
        if (taskId != null) accept(intent = TaskEditorIntent.RetryLoad)
        collectEffect(
            messages = flow {
                while (true) {
                    delay(timeMillis = 60_000)
                    emit(value = TaskEditorMessage.Today(value = today()))
                }
            }
        )
    }

    override fun handleIntent(intent: TaskEditorIntent) {
        val current = state.value
        if (current.isFinished || current.isSaving) return
        when (intent) {
            TaskEditorIntent.RetryGroups -> if (groupObservation?.isActive != true) {
                reduce(message = TaskEditorMessage.GroupsLoading)
                groupObservation = collectEffect(
                    messages = groups.groups
                        .map { groups -> TaskEditorMessage.GroupsLoaded(groups = groups) as TaskEditorMessage }
                        .catch { exception ->
                            if (exception is CancellationException) throw exception
                            emit(value = TaskEditorMessage.GroupsLoadFailed)
                        }
                )
            }
            TaskEditorIntent.RetryLoad -> {
                if (taskId == null || current.isLoading && loadRequested || current.original != null) return
                loadRequested = true
                reduce(message = TaskEditorMessage.Loading)
                launchEffect {
                    try {
                        TaskEditorMessage.Loaded(
                            task = if (occurrenceId == null) {
                                repository.getById(id = taskId)
                            } else {
                                repository.getOccurrence(id = taskId, occurrence = occurrenceId)
                            }
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        TaskEditorMessage.LoadFailed
                    }
                }
            }
            TaskEditorIntent.DismissDialog -> reduce(message = TaskEditorMessage.Dialog(value = null))
            is TaskEditorIntent.SaveOccurrence ->
                if (current.dialog == TaskEditorDialog.MoveOccurrence && current.copy(dialog = null).canSave)
                    save(current = current, scope = intent.scope)
            is TaskEditorIntent.DeleteOccurrence ->
                if (current.dialog == TaskEditorDialog.DeleteOccurrence && taskId != null) {
                    reduce(message = TaskEditorMessage.Saving)
                    launchEffect {
                        recover(message = "Couldn't delete this event. Please try again.") {
                            if (
                                repository.deleteOccurrence(
                                    id = taskId,
                                    occurrence = occurrenceId,
                                    scope = intent.scope,
                                )
                            ) {
                                TaskEditorMessage.Finished
                            } else {
                                TaskEditorMessage.Failed(message = "This event no longer exists.")
                            }
                        }
                    }
                }
            is TaskEditorIntent.RepeatEndDateChanged -> if (current.dialog == TaskEditorDialog.RepeatEndDatePicker)
                current.editedRecurrence?.let { recurrence ->
                    reduce(
                        message = TaskEditorMessage.Recurrence(
                            value = recurrence.copy(endDate = intent.date, occurrenceCount = null)
                        )
                    )
                }
            TaskEditorIntent.ConfirmDelete -> if (current.dialog == TaskEditorDialog.ConfirmDelete && taskId != null) {
                reduce(message = TaskEditorMessage.Saving)
                launchEffect {
                    recover(message = "Couldn't delete this task. Please try again.") {
                        repository.delete(id = taskId)
                        TaskEditorMessage.Finished
                    }
                }
            }
            is TaskEditorIntent.DateChanged -> if (
                !current.isLoading &&
                    current.loadError == null &&
                    (current.dialog == null || current.dialog == TaskEditorDialog.DatePicker)
            )
                reduce(message = TaskEditorMessage.Date(value = intent.date))
            else -> {
                if (
                    current.isBusy ||
                        current.loadError != null ||
                        current.groupsLoadError != null ||
                        current.dialog != null
                )
                    return
                when (intent) {
                    is TaskEditorIntent.CalendarVisibilityChanged -> if (current.recurrence != null)
                        reduce(message = TaskEditorMessage.CalendarVisibility(value = intent.showInCalendar))
                    is TaskEditorIntent.QuickDeleteChanged -> if (current.recurrence != null)
                        reduce(message = TaskEditorMessage.QuickDelete(value = intent.enabled))
                    is TaskEditorIntent.GroupChanged ->
                        if (intent.id == null || current.groups.any { group -> group.id == intent.id }) {
                            reduce(message = TaskEditorMessage.GroupSelected(value = intent.id))
                            onGroupSelected(intent.id)
                        }
                    is TaskEditorIntent.RecurrenceChanged -> if (current.dueDate != null || intent.recurrence == null)
                        reduce(message = TaskEditorMessage.Recurrence(value = intent.recurrence))
                    TaskEditorIntent.ChooseRepeatEndDate -> if (current.recurrence != null)
                        reduce(message = TaskEditorMessage.Dialog(value = TaskEditorDialog.RepeatEndDatePicker))
                    TaskEditorIntent.ChooseDate -> reduce(
                        message = TaskEditorMessage.Dialog(value = TaskEditorDialog.DatePicker),
                    )
                    TaskEditorIntent.PrepareDelete -> if (current.original != null)
                        reduce(
                            message = TaskEditorMessage.Dialog(
                                value = if (occurrenceId != null) {
                                    TaskEditorDialog.DeleteOccurrence
                                } else {
                                    TaskEditorDialog.ConfirmDelete
                                }
                            )
                        )
                    TaskEditorIntent.Save -> if (current.canSave) {
                        if (
                            occurrenceId != null &&
                                current.original?.recurrence == current.editedRecurrence &&
                                current.original?.dueDate != current.dueDate
                        ) {
                            reduce(message = TaskEditorMessage.Dialog(value = TaskEditorDialog.MoveOccurrence))
                        } else {
                            save(current = current, scope = TaskChangeScope.ThisAndFollowing)
                        }
                    }
                }
            }
        }
    }

    private fun save(current: TaskEditorState, scope: TaskChangeScope) {
        reduce(message = TaskEditorMessage.Saving)
        val task = (current.original ?: Task(name = "")).copy(
            name = current.normalizedName,
            description = current.description.text.toString(),
            dueDate = current.dueDate,
            showInCalendar = current.showInCalendar,
            quickDelete = current.quickDelete,
            groupId = current.groupId,
            recurrence = current.editedRecurrence,
        )
        launchEffect {
            recover(message = "Couldn't save this task. Please try again.") {
                if (repository.saveOccurrence(task = task, scope = scope)) {
                    TaskEditorMessage.Finished
                } else {
                    TaskEditorMessage.Failed(
                        message = "This task has changed or no longer exists. Reopen it and try again."
                    )
                }
            }
        }
    }

    private suspend fun recover(message: String, block: suspend () -> TaskEditorMessage): TaskEditorMessage {
        return try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            TaskEditorMessage.Failed(message = message)
        }
    }
}
