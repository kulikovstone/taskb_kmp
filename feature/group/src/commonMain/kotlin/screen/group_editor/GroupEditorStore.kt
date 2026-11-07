package com.mkulikov.taskb.feature.group.screen.group_editor

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.group.api.model.GroupColor
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal class GroupEditorStore(
    private val repository: IGroupRepository,
    private val groupId: Int? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineStore<GroupEditorIntent, GroupEditorState, GroupEditorMessage>(
    initialState = GroupEditorState(
        isNew = groupId == null,
        color = if (groupId == null) GroupColor.entries.random() else GroupColor.Grey,
    ),
    reducer = ::reduceGroupEditor,
    context = dispatcher,
) {
    private var loadRequested = false

    init {
        if (groupId != null) accept(intent = GroupEditorIntent.RetryLoad)
    }

    override fun handleIntent(intent: GroupEditorIntent) {
        val current = state.value
        if (current.isFinished) return
        when (intent) {
            is GroupEditorIntent.ColorChanged ->
                if (!current.isBusy && current.loadError == null && current.dialog == null) {
                    reduce(message = GroupEditorMessage.ColorChanged(color = intent.color))
                }
            GroupEditorIntent.Save -> {
                if (!current.canSave || current.dialog != null) return
                reduce(message = GroupEditorMessage.Saving)
                launchEffect {
                    recover(failure = GroupEditorMessage.SaveFailed) {
                        val result = if (current.isNew) {
                            repository.add(name = current.normalizedName, color = current.color)
                        } else {
                            repository.update(
                                group = requireNotNull(value = current.original)
                                    .copy(name = current.normalizedName, color = current.color)
                            )
                        }
                        GroupEditorMessage.Saved(result = result)
                    }
                }
            }
            GroupEditorIntent.PrepareDelete ->
                if (current.original != null && !current.isBusy && current.dialog == null) {
                    reduce(message = GroupEditorMessage.AskDelete)
                }
            GroupEditorIntent.ConfirmDelete -> {
                if (current.dialog != GroupEditorDialog.ConfirmDelete || current.isBusy || groupId == null) return
                reduce(message = GroupEditorMessage.Deleting)
                launchEffect {
                    recover(failure = GroupEditorMessage.DeleteFailed) {
                        repository.delete(id = groupId)
                        GroupEditorMessage.Deleted
                    }
                }
            }
            GroupEditorIntent.DismissDialog -> if (!current.isBusy) reduce(message = GroupEditorMessage.DismissDialog)
            GroupEditorIntent.RetryLoad -> {
                if (
                    groupId == null ||
                        current.original != null ||
                        current.isSaving ||
                        current.isDeleting ||
                        current.isLoading && loadRequested
                )
                    return
                loadRequested = true
                reduce(message = GroupEditorMessage.Loading)
                launchEffect {
                    recover(failure = GroupEditorMessage.LoadFailed) {
                        GroupEditorMessage.Loaded(group = repository.getById(id = groupId))
                    }
                }
            }
        }
    }

    private suspend fun recover(
        failure: GroupEditorMessage,
        block: suspend () -> GroupEditorMessage,
    ): GroupEditorMessage {
        return try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            failure
        }
    }
}
