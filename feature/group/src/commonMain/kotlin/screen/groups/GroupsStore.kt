package com.mkulikov.taskb.feature.group.screen.groups

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class GroupsStore(
    private val repository: IGroupRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineStore<GroupsIntent, GroupsState, GroupsMessage>(
    initialState = GroupsState(),
    reducer = ::reduceGroups,
    context = dispatcher,
) {
    private var observation: Job? = null

    init {
        accept(intent = GroupsIntent.RetryLoad)
    }

    override fun handleIntent(intent: GroupsIntent) {
        when (intent) {
            is GroupsIntent.Filter -> reduce(message = GroupsMessage.FilterChanged(filter = intent.filter))
            is GroupsIntent.Sort -> reduce(message = GroupsMessage.SortChanged(sort = intent.sort))
            GroupsIntent.RetryLoad -> if (observation?.isActive != true) {
                reduce(message = GroupsMessage.Loading)
                observation = collectEffect(
                    messages = repository.groups
                        .map<List<Group>, GroupsMessage> { groups ->
                            GroupsMessage.Loaded(groups = groups)
                        }
                        .catch { exception ->
                            if (exception is CancellationException) throw exception
                            emit(value = GroupsMessage.LoadFailed)
                        }
                )
            }
        }
    }
}
