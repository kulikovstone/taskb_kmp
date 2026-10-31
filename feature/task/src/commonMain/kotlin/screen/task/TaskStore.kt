package com.mkulikov.taskb.feature.task.screen.task

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.task.api.repository.ITaskRepository
import com.mkulikov.taskb.core.utils.time.currentDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
internal class TaskStore(
    private val repository: ITaskRepository,
    private val groups: IGroupRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val today: () -> LocalDate = ::currentDate,
    private val clock: Clock = Clock.System,
) : CoroutineStore<TaskIntent, TaskState, TaskMessage>(TaskState(today = today(), now = clock.now()), ::reduceTask, dispatcher) {
    private var observation: Job? = null
    init {
        accept(TaskIntent.RetryLoad)
        collectEffect(flow {
            while (true) {
                delay(60_000)
                emit(TaskMessage.Today(today(), clock.now()))
            }
        })
        collectEffect(state.map { it.nextCompletionExpiry }.distinctUntilChanged().flatMapLatest { expiresAt ->
            flow {
                if (expiresAt != null) {
                    delay((expiresAt - clock.now()).coerceAtLeast(Duration.ZERO))
                    emit(TaskMessage.Today(today(), clock.now()))
                }
            }
        })
    }
    override fun handleIntent(intent: TaskIntent) {
        when (intent) {
            is TaskIntent.Search -> reduce(TaskMessage.Query(intent.query))
            is TaskIntent.Filter -> reduce(TaskMessage.Filter(intent.filter))
            is TaskIntent.FilterGroup -> reduce(TaskMessage.GroupFilter(intent.filter))
            is TaskIntent.Sort -> reduce(TaskMessage.Sort(intent.sort))
            is TaskIntent.Choose -> reduce(TaskMessage.Selection(intent.selection))
            TaskIntent.CloseSelection -> reduce(TaskMessage.Selection(null))
            TaskIntent.DismissError -> reduce(TaskMessage.DismissError)
            is TaskIntent.ToggleCompleted -> {
                val task = state.value.tasks.find { it.id == intent.id } ?: return
                if (task.id in state.value.pendingCompletion) return
                reduce(TaskMessage.Completing(task.id))
                launchEffect {
                    try {
                        repository.setCompleted(task.id, !task.isCompleted)
                        TaskMessage.Completed(task.id, false)
                    } catch (exception: CancellationException) { throw exception }
                    catch (_: Exception) { TaskMessage.Completed(task.id, true) }
                }
            }
            TaskIntent.RetryLoad -> if (observation?.isActive != true) {
                reduce(TaskMessage.Loading)
                observation = collectEffect(combine(repository.tasks, groups.groups) { tasks, groups ->
                    TaskMessage.Loaded(tasks, groups, clock.now()) as TaskMessage
                }.catch {
                    if (it is CancellationException) throw it
                    emit(TaskMessage.LoadFailed)
                })
            }
        }
    }
}
