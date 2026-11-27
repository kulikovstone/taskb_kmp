package com.mkulikov.taskb.feature.item.screen.task

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.core.utils.time.currentDate
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.item.api.model.TaskFilter
import com.mkulikov.taskb.feature.item.api.repository.ITaskRepository
import kotlin.time.Clock
import kotlin.time.Duration
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class TaskStore(
    private val repository: ITaskRepository,
    private val groups: IGroupRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val today: () -> LocalDate = ::currentDate,
    private val clock: Clock = Clock.System,
    initialFilter: TaskFilter? = null,
) : CoroutineStore<TaskIntent, TaskState, TaskMessage>(
    initialState = TaskState(today = today(), now = clock.now(), filter = initialFilter),
    reducer = ::reduceTask,
    context = dispatcher,
) {
    private var observation: Job? = null

    init {
        accept(intent = TaskIntent.RetryLoad)
        collectEffect(
            messages = flow {
                while (true) {
                    delay(timeMillis = 60_000)
                    emit(value = TaskMessage.Today(value = today(), now = clock.now()))
                }
            }
        )
        collectEffect(
            messages = state
                .map { state -> state.nextCompletionExpiry }
                .distinctUntilChanged()
                .flatMapLatest { expiresAt ->
                    flow {
                        if (expiresAt != null) {
                            delay(
                                duration = (expiresAt - clock.now()).coerceAtLeast(minimumValue = Duration.ZERO)
                            )
                            emit(value = TaskMessage.Today(value = today(), now = clock.now()))
                        }
                    }
                }
        )
    }

    override fun handleIntent(intent: TaskIntent) {
        when (intent) {
            is TaskIntent.Filter -> reduce(message = TaskMessage.Filter(value = intent.filter))
            is TaskIntent.FilterGroup -> reduce(message = TaskMessage.GroupFilter(value = intent.filter))
            is TaskIntent.Sort -> reduce(message = TaskMessage.Sort(value = intent.sort))
            is TaskIntent.Choose -> reduce(message = TaskMessage.Selection(value = intent.selection))
            TaskIntent.CloseSelection -> reduce(message = TaskMessage.Selection(value = null))
            TaskIntent.DismissError -> reduce(message = TaskMessage.DismissError)
            is TaskIntent.ToggleCompleted -> {
                val task = state.value.tasks.find { task ->
                    task.id == intent.id && task.occurrenceId == intent.occurrenceId
                }
                    ?: return
                if (task.id in state.value.pendingCompletion) return
                reduce(message = TaskMessage.Completing(id = task.id))
                launchEffect {
                    try {
                        TaskMessage.Completed(
                            id = task.id,
                            failed =
                                !repository.setOccurrenceCompleted(
                                    id = task.id,
                                    occurrence = task.occurrenceId,
                                    completed = !task.isCompleted,
                                ),
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        TaskMessage.Completed(id = task.id, failed = true)
                    }
                }
            }
            TaskIntent.RetryLoad -> if (observation?.isActive != true) {
                reduce(message = TaskMessage.Loading)
                val taskEvents = state
                    .map { state -> state.today }
                    .distinctUntilChanged()
                    .flatMapLatest(transform = repository::observeTaskList)
                observation = collectEffect(
                    messages = combine(flow = taskEvents, flow2 = groups.groups) { tasks, groups ->
                        TaskMessage.Loaded(tasks = tasks, groups = groups, now = clock.now())
                            as TaskMessage
                    }
                        .catch { exception ->
                            if (exception is CancellationException) throw exception
                            emit(value = TaskMessage.LoadFailed)
                        }
                )
            }
        }
    }
}
