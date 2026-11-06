package com.mkulikov.taskb.feature.group.screen.group_selection

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.group.api.GroupSelectionResult
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class GroupSelectionStore(
    private val repository: IGroupRepository,
    selectedGroupId: Int? = null,
    showNoGroup: Boolean = false,
    noGroupSelected: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineStore<GroupSelectionIntent, GroupSelectionState, GroupSelectionMessage>(
    GroupSelectionState(selectedGroupId, showNoGroup, noGroupSelected), ::reduceGroupSelection, dispatcher,
) {
    private var observation: Job? = null

    init { accept(GroupSelectionIntent.RetryLoad) }

    override fun handleIntent(intent: GroupSelectionIntent) {
        val current = state.value
        if (current.result != null) return
        when (intent) {
            is GroupSelectionIntent.Select -> {
                if (current.isLoading || current.loadError != null) return
                val valid = if (intent.groupId == null) current.isNoGroupVisible
                    else current.matchingGroups.any { it.id == intent.groupId }
                if (valid) reduce(GroupSelectionMessage.Selected(GroupSelectionResult(intent.groupId)))
            }
            GroupSelectionIntent.RetryLoad -> if (observation?.isActive != true) {
                reduce(GroupSelectionMessage.Loading)
                observation = collectEffect(repository.groups.map<List<Group>, GroupSelectionMessage> {
                    GroupSelectionMessage.Loaded(it)
                }.catch {
                    if (it is CancellationException) throw it
                    emit(GroupSelectionMessage.LoadFailed)
                })
            }
        }
    }
}
