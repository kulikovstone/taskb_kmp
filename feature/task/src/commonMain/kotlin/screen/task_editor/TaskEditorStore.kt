package com.mkulikov.taskb.feature.task.screen.task_editor

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.task.api.model.Task
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
            TaskEditorIntent.CloseGroupSelection -> reduce(TaskEditorMessage.ChoosingGroup(false))
            is TaskEditorIntent.SearchGroups -> if (current.isChoosingGroup) reduce(TaskEditorMessage.GroupQuery(intent.query))
            TaskEditorIntent.RetryLoad -> {
                if (taskId == null || current.isLoading && loadRequested || current.original != null) return
                loadRequested = true
                reduce(TaskEditorMessage.Loading)
                launchEffect {
                    try { TaskEditorMessage.Loaded(repository.getById(taskId)) }
                    catch (exception: CancellationException) { throw exception }
                    catch (_: Exception) { TaskEditorMessage.LoadFailed }
                }
            }
            TaskEditorIntent.DismissDialog -> reduce(TaskEditorMessage.Dialog(null))
            TaskEditorIntent.ConfirmDelete -> if (current.dialog == TaskEditorDialog.ConfirmDelete && taskId != null) {
                reduce(TaskEditorMessage.Saving)
                launchEffect { recover("Couldn't delete this task. Please try again.") { repository.delete(taskId); TaskEditorMessage.Finished } }
            }
            is TaskEditorIntent.DateChanged -> if (!current.isLoading && current.loadError == null && (current.dialog == null || current.dialog == TaskEditorDialog.DatePicker)) reduce(TaskEditorMessage.Date(intent.date))
            else -> {
                if (current.isBusy || current.loadError != null || current.groupsLoadError != null || current.dialog != null) return
                when (intent) {
                    is TaskEditorIntent.GroupChanged -> if (intent.id == null || current.groups.any { it.id == intent.id }) reduce(TaskEditorMessage.GroupSelected(intent.id))
                    TaskEditorIntent.ChooseGroup -> reduce(TaskEditorMessage.ChoosingGroup(true))
                    is TaskEditorIntent.ShiftDate -> current.dueDate?.let {
                        reduce(TaskEditorMessage.Date(it.plus(intent.days, DateTimeUnit.DAY).plus(intent.months, DateTimeUnit.MONTH)))
                    }
                    TaskEditorIntent.ChooseDate -> reduce(TaskEditorMessage.Dialog(TaskEditorDialog.DatePicker))
                    TaskEditorIntent.PrepareDelete -> if (current.original != null) reduce(TaskEditorMessage.Dialog(TaskEditorDialog.ConfirmDelete))
                    TaskEditorIntent.Save -> if (current.canSave) {
                        reduce(TaskEditorMessage.Saving)
                        val task = (current.original ?: Task(name = "")).copy(name = current.normalizedName, dueDate = current.dueDate, groupId = current.groupId)
                        launchEffect { recover("Couldn't save this task. Please try again.") {
                            if (repository.save(task)) TaskEditorMessage.Finished else TaskEditorMessage.Failed("This task no longer exists.")
                        } }
                    }
                }
            }
        }
    }
    private suspend fun recover(message: String, block: suspend () -> TaskEditorMessage): TaskEditorMessage = try { block() }
        catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { TaskEditorMessage.Failed(message) }
}
