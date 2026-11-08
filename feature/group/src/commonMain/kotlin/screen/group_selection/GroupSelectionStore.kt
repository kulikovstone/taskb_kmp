package com.mkulikov.taskb.feature.group.screen.group_selection

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.model.Group
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class GroupSelectionStore(
    private val groupsByUsage: Flow<List<Group>>,
    selectedGroupId: Int? = null,
    showNoGroup: Boolean = false,
    noGroupSelected: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineStore<GroupSelectionIntent, GroupSelectionState, GroupSelectionMessage>(
    initialState = GroupSelectionState(
        selectedGroupId = selectedGroupId,
        showNoGroup = showNoGroup,
        noGroupSelected = noGroupSelected,
    ),
    reducer = ::reduceGroupSelection,
    context = dispatcher,
) {
    private var observation: Job? = null

    init {
        accept(intent = GroupSelectionIntent.RetryLoad)
    }

    override fun handleIntent(intent: GroupSelectionIntent) {
        val current = state.value
        if (current.result != null) return
        when (intent) {
            is GroupSelectionIntent.Select -> {
                if (current.isLoading || current.loadError != null) return
                val valid = if (intent.groupId == null) {
                    current.isNoGroupVisible
                } else {
                    current.matchingGroups.any { group -> group.id == intent.groupId }
                }
                if (valid)
                    reduce(
                        message = GroupSelectionMessage.Selected(
                            result = GroupSelectionResult(groupId = intent.groupId),
                        )
                    )
            }
            GroupSelectionIntent.RetryLoad -> if (observation?.isActive != true) {
                reduce(message = GroupSelectionMessage.Loading)
                observation = collectEffect(
                    messages = groupsByUsage
                        .map<List<Group>, GroupSelectionMessage> { groups ->
                            GroupSelectionMessage.Loaded(groups = groups)
                        }
                        .catch { exception ->
                            if (exception is CancellationException) throw exception
                            emit(value = GroupSelectionMessage.LoadFailed)
                        }
                )
            }
        }
    }
}
