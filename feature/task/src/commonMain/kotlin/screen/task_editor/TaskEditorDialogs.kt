package com.mkulikov.taskb.feature.task.screen.task_editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.mkulikov.taskb.feature.task.api.model.TaskChangeScope
import com.mkulikov.taskb.ui.component.date_picker.TaskBDatePicker
import com.mkulikov.taskb.ui.dialog.TaskBChoiceDialog
import com.mkulikov.taskb.ui.dialog.TaskBConfirmationDialog
import com.mkulikov.taskb.ui.dialog.TaskBDialogChoice
import com.mkulikov.taskb.ui.dialog.TaskBMessageDialog
import kotlinx.datetime.LocalDate

@Composable
internal fun TaskEditorDialogs(
    state: TaskEditorState,
    today: LocalDate,
    onIntent: (TaskEditorIntent) -> Unit,
    availableHeight: Dp,
) {
    when (val dialog = state.dialog) {
        TaskEditorDialog.ConfirmDelete -> TaskBConfirmationDialog(
            title = "Delete task",
            message = "Delete \"${state.normalizedName}\"?",
            confirmText = "Delete",
            onConfirm = { onIntent(TaskEditorIntent.ConfirmDelete) },
            onDismiss = { onIntent(TaskEditorIntent.DismissDialog) },
        )
        is TaskEditorDialog.Error -> TaskBMessageDialog(title = "Task", message = dialog.message) {
            onIntent(TaskEditorIntent.DismissDialog)
        }
        TaskEditorDialog.DatePicker -> TaskBDatePicker(
            date = state.dueDate,
            today = today,
            onChoose = { date -> onIntent(TaskEditorIntent.DateChanged(date = date)) },
            onDismiss = { onIntent(TaskEditorIntent.DismissDialog) },
            availableHeight = availableHeight,
        )
        TaskEditorDialog.RepeatEndDatePicker -> TaskBDatePicker(
            date = state.recurrence?.endDate,
            today = today,
            onChoose = { date -> onIntent(TaskEditorIntent.RepeatEndDateChanged(date = date)) },
            onDismiss = { onIntent(TaskEditorIntent.DismissDialog) },
            availableHeight = availableHeight,
        )
        TaskEditorDialog.DeleteOccurrence -> TaskBChoiceDialog(
            title = "Delete repeating task",
            message =
                "Choose which events to delete. Deleting the entire task also removes its completed history.",
            choices = listOf(
                TaskBDialogChoice(
                    label = "Only this event",
                    onClick = {
                        onIntent(TaskEditorIntent.DeleteOccurrence(scope = TaskChangeScope.ThisOccurrence))
                    },
                    destructive = true,
                ),
                TaskBDialogChoice(
                    label = "This and following events",
                    onClick = {
                        onIntent(
                            TaskEditorIntent.DeleteOccurrence(scope = TaskChangeScope.ThisAndFollowing)
                        )
                    },
                    destructive = true,
                ),
                TaskBDialogChoice(
                    label = "Entire task and all events",
                    onClick = {
                        onIntent(TaskEditorIntent.DeleteOccurrence(scope = TaskChangeScope.EntireTask))
                    },
                    destructive = true,
                ),
            ),
            onDismiss = { onIntent(TaskEditorIntent.DismissDialog) },
        )
        TaskEditorDialog.MoveOccurrence -> TaskBChoiceDialog(
            title = "Move repeating task",
            message =
                ("Choose which events to move. Name and group changes apply to the entire task. " +
                    "Completed history is preserved."),
            choices = listOf(
                TaskBDialogChoice(
                    label = "Only this event",
                    onClick = {
                        onIntent(TaskEditorIntent.SaveOccurrence(scope = TaskChangeScope.ThisOccurrence))
                    },
                ),
                TaskBDialogChoice(
                    label = "This and following events",
                    onClick = {
                        onIntent(TaskEditorIntent.SaveOccurrence(scope = TaskChangeScope.ThisAndFollowing))
                    },
                ),
            ),
            onDismiss = { onIntent(TaskEditorIntent.DismissDialog) },
        )
        null -> Unit
    }
}
