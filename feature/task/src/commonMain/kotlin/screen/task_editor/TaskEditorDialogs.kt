package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.mkulikov.taskb.ui.dialog.TaskBConfirmationDialog
import com.mkulikov.taskb.ui.dialog.TaskBMessageDialog
import com.mkulikov.taskb.ui.dialog.TaskBChoiceDialog
import com.mkulikov.taskb.ui.dialog.TaskBDialogChoice
import com.mkulikov.taskb.ui.component.date_picker.TaskBDatePicker
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import kotlinx.datetime.LocalDate

@Composable
internal fun TaskEditorDialogs(state: TaskEditorState, today: LocalDate, onIntent: (TaskEditorIntent) -> Unit, availableHeight: Dp) {
    when (val dialog = state.dialog) {
        TaskEditorDialog.ConfirmDelete -> TaskBConfirmationDialog("Delete task", "Delete \"${state.normalizedName}\"?", "Delete",
            { onIntent(TaskEditorIntent.ConfirmDelete) }, { onIntent(TaskEditorIntent.DismissDialog) })
        is TaskEditorDialog.Error -> TaskBMessageDialog("Task", dialog.message) { onIntent(TaskEditorIntent.DismissDialog) }
        TaskEditorDialog.DatePicker -> TaskBDatePicker(state.dueDate, today, { onIntent(TaskEditorIntent.DateChanged(it)) }, { onIntent(TaskEditorIntent.DismissDialog) }, availableHeight)
        TaskEditorDialog.RepeatEndDatePicker -> TaskBDatePicker(state.recurrence?.endDate, today, { onIntent(TaskEditorIntent.RepeatEndDateChanged(it)) }, { onIntent(TaskEditorIntent.DismissDialog) }, availableHeight)
        TaskEditorDialog.DeleteOccurrence -> TaskBChoiceDialog("Delete repeating task", "Choose which events to delete. Deleting the entire task also removes its completed history.", listOf(
            TaskBDialogChoice("Only this event", { onIntent(TaskEditorIntent.DeleteOccurrence(TaskChangeScope.ThisOccurrence)) }, true),
            TaskBDialogChoice("This and following events", { onIntent(TaskEditorIntent.DeleteOccurrence(TaskChangeScope.ThisAndFollowing)) }, true),
            TaskBDialogChoice("Entire task and all events", { onIntent(TaskEditorIntent.DeleteOccurrence(TaskChangeScope.EntireTask)) }, true),
        ), { onIntent(TaskEditorIntent.DismissDialog) })
        TaskEditorDialog.MoveOccurrence -> TaskBChoiceDialog("Move repeating task", "Choose which events to move. Name and group changes apply to the entire task. Completed history is preserved.", listOf(
            TaskBDialogChoice("Only this event", { onIntent(TaskEditorIntent.SaveOccurrence(TaskChangeScope.ThisOccurrence)) }),
            TaskBDialogChoice("This and following events", { onIntent(TaskEditorIntent.SaveOccurrence(TaskChangeScope.ThisAndFollowing)) }),
        ), { onIntent(TaskEditorIntent.DismissDialog) })
        null -> Unit
    }
}
