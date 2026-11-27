package com.mkulikov.taskb.feature.item.screen.event_editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.ui.component.date_picker.TaskBDatePicker
import com.mkulikov.taskb.ui.dialog.TaskBChoiceDialog
import com.mkulikov.taskb.ui.dialog.TaskBConfirmationDialog
import com.mkulikov.taskb.ui.dialog.TaskBDialogChoice
import com.mkulikov.taskb.ui.dialog.TaskBMessageDialog
import kotlinx.datetime.LocalDate

@Composable
internal fun EventEditorDialogs(
    state: EventEditorState,
    today: LocalDate,
    onIntent: (EventEditorIntent) -> Unit,
    availableHeight: Dp,
) {
    when (val dialog = state.dialog) {
        EventEditorDialog.ConfirmDelete -> TaskBConfirmationDialog(
            title = "Delete event",
            message = "Delete \"${state.normalizedName}\"?",
            confirmText = "Delete",
            onConfirm = { onIntent(EventEditorIntent.ConfirmDelete) },
            onDismiss = { onIntent(EventEditorIntent.DismissDialog) },
        )
        is EventEditorDialog.Error -> TaskBMessageDialog(title = "Event", message = dialog.message) {
            onIntent(EventEditorIntent.DismissDialog)
        }
        EventEditorDialog.DatePicker -> TaskBDatePicker(
            date = state.dueDate,
            today = today,
            onChoose = { date -> onIntent(EventEditorIntent.DateChanged(date = date)) },
            onDismiss = { onIntent(EventEditorIntent.DismissDialog) },
            availableHeight = availableHeight,
        )
        EventEditorDialog.RepeatEndDatePicker -> TaskBDatePicker(
            date = state.recurrence?.endDate,
            today = today,
            onChoose = { date -> onIntent(EventEditorIntent.RepeatEndDateChanged(date = date)) },
            onDismiss = { onIntent(EventEditorIntent.DismissDialog) },
            availableHeight = availableHeight,
        )
        EventEditorDialog.EventEndDatePicker -> TaskBDatePicker(
            date = state.endDate,
            today = today,
            onChoose = { date -> onIntent(EventEditorIntent.EventEndDateChanged(date = date)) },
            onDismiss = { onIntent(EventEditorIntent.DismissDialog) },
            availableHeight = availableHeight,
        )
        EventEditorDialog.StartTimePicker -> EventTimePickerDialog(
            title = "Choose time",
            initialTime = state.startTime,
            availableHeight = availableHeight,
            onConfirm = { time -> onIntent(EventEditorIntent.StartTimeChanged(time = time)) },
            onDismiss = { onIntent(EventEditorIntent.DismissDialog) },
        )
        EventEditorDialog.EndTimePicker -> EventTimePickerDialog(
            title = "Choose end time",
            initialTime = state.endTime ?: state.startTime,
            availableHeight = availableHeight,
            onConfirm = { time -> onIntent(EventEditorIntent.EndTimeChanged(time = time)) },
            onDismiss = { onIntent(EventEditorIntent.DismissDialog) },
        )
        EventEditorDialog.DeleteOccurrence -> TaskBChoiceDialog(
            title = "Delete repeating event",
            message = "Choose which occurrences to delete.",
            choices = listOf(
                TaskBDialogChoice(
                    label = "Only this event",
                    onClick = {
                        onIntent(
                            EventEditorIntent.DeleteOccurrence(scope = ItemChangeScope.ThisOccurrence)
                        )
                    },
                    destructive = true,
                ),
                TaskBDialogChoice(
                    label = "This and following events",
                    onClick = {
                        onIntent(
                            EventEditorIntent.DeleteOccurrence(scope = ItemChangeScope.ThisAndFollowing)
                        )
                    },
                    destructive = true,
                ),
                TaskBDialogChoice(
                    label = "Entire event and all occurrences",
                    onClick = {
                        onIntent(EventEditorIntent.DeleteOccurrence(scope = ItemChangeScope.EntireItem))
                    },
                    destructive = true,
                ),
            ),
            onDismiss = { onIntent(EventEditorIntent.DismissDialog) },
        )
        EventEditorDialog.MoveOccurrence -> TaskBChoiceDialog(
            title = "Move repeating event",
            message =
                "Choose which occurrences to move. Name, description and group changes apply to the entire event.",
            choices = listOf(
                TaskBDialogChoice(
                    label = "Only this event",
                    onClick = {
                        onIntent(EventEditorIntent.SaveOccurrence(scope = ItemChangeScope.ThisOccurrence))
                    },
                ),
                TaskBDialogChoice(
                    label = "This and following events",
                    onClick = {
                        onIntent(
                            EventEditorIntent.SaveOccurrence(scope = ItemChangeScope.ThisAndFollowing)
                        )
                    },
                ),
            ),
            onDismiss = { onIntent(EventEditorIntent.DismissDialog) },
        )
        null -> Unit
    }
}
