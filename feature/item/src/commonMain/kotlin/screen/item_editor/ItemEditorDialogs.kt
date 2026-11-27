package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.mkulikov.taskb.feature.item.api.model.ItemChangeScope
import com.mkulikov.taskb.ui.component.date_picker.TaskBDatePicker
import com.mkulikov.taskb.ui.dialog.TaskBChoiceDialog
import com.mkulikov.taskb.ui.dialog.TaskBConfirmationDialog
import com.mkulikov.taskb.ui.dialog.TaskBDialogChoice
import com.mkulikov.taskb.ui.dialog.TaskBMessageDialog

@Composable
internal fun ItemEditorDialogs(
    state: ItemEditorState,
    onIntent: (ItemEditorIntent) -> Unit,
    availableHeight: Dp,
) {
    val label = state.type.name.lowercase()
    val event = state.schedule.details as? EventSchedule
    when (val dialog = state.dialog) {
        ItemEditorDialog.ConfirmDelete -> TaskBConfirmationDialog(
            title = "Delete $label",
            message = "Delete \"${state.normalizedName}\"?",
            confirmText = "Delete",
            onConfirm = { onIntent(ItemEditorIntent.ConfirmDelete) },
            onDismiss = { onIntent(ItemEditorIntent.DismissDialog) },
        )
        is ItemEditorDialog.Error -> TaskBMessageDialog(title = state.type.name, message = dialog.message) {
            onIntent(ItemEditorIntent.DismissDialog)
        }
        ItemEditorDialog.DatePicker -> TaskBDatePicker(
            date = state.schedule.dueDate,
            today = state.today,
            onChoose = { date -> onIntent(ItemEditorIntent.DateChanged(date = date)) },
            onDismiss = { onIntent(ItemEditorIntent.DismissDialog) },
            availableHeight = availableHeight,
        )
        ItemEditorDialog.RepeatEndDatePicker -> TaskBDatePicker(
            date = state.schedule.recurrence?.endDate,
            today = state.today,
            onChoose = { date -> onIntent(ItemEditorIntent.RepeatEndDateChanged(date = date)) },
            onDismiss = { onIntent(ItemEditorIntent.DismissDialog) },
            availableHeight = availableHeight,
        )
        ItemEditorDialog.EventEndDatePicker -> TaskBDatePicker(
            date = event?.endDate,
            today = state.today,
            onChoose = { date -> onIntent(ItemEditorIntent.EventEndDateChanged(date = date)) },
            onDismiss = { onIntent(ItemEditorIntent.DismissDialog) },
            availableHeight = availableHeight,
        )
        ItemEditorDialog.StartTimePicker -> EventTimePickerDialog(
            title = "Choose time",
            initialTime = event?.startTime,
            availableHeight = availableHeight,
            onConfirm = { time -> onIntent(ItemEditorIntent.StartTimeChanged(time = time)) },
            onDismiss = { onIntent(ItemEditorIntent.DismissDialog) },
        )
        ItemEditorDialog.EndTimePicker -> EventTimePickerDialog(
            title = "Choose end time",
            initialTime = event?.endTime ?: event?.startTime,
            availableHeight = availableHeight,
            onConfirm = { time -> onIntent(ItemEditorIntent.EndTimeChanged(time = time)) },
            onDismiss = { onIntent(ItemEditorIntent.DismissDialog) },
        )
        ItemEditorDialog.DeleteOccurrence -> TaskBChoiceDialog(
            title = "Delete repeating $label",
            message = if (event == null) {
                "Choose which events to delete. Deleting the entire task also removes its completed history."
            } else {
                "Choose which occurrences to delete."
            },
            choices = listOf(
                TaskBDialogChoice(
                    label = "Only this event",
                    onClick = {
                        onIntent(
                            ItemEditorIntent.DeleteOccurrence(scope = ItemChangeScope.ThisOccurrence)
                        )
                    },
                    destructive = true,
                ),
                TaskBDialogChoice(
                    label = "This and following events",
                    onClick = {
                        onIntent(
                            ItemEditorIntent.DeleteOccurrence(scope = ItemChangeScope.ThisAndFollowing)
                        )
                    },
                    destructive = true,
                ),
                TaskBDialogChoice(
                    label = if (event == null) "Entire task and all events" else "Entire event and all occurrences",
                    onClick = {
                        onIntent(ItemEditorIntent.DeleteOccurrence(scope = ItemChangeScope.EntireItem))
                    },
                    destructive = true,
                ),
            ),
            onDismiss = { onIntent(ItemEditorIntent.DismissDialog) },
        )
        ItemEditorDialog.MoveOccurrence -> TaskBChoiceDialog(
            title = "Move repeating $label",
            message = if (event == null) {
                "Choose which events to move. Name and group changes apply to the entire task. " +
                    "Completed history is preserved."
            } else {
                "Choose which occurrences to move. Name, description and group changes apply to the entire event."
            },
            choices = listOf(
                TaskBDialogChoice(
                    label = "Only this event",
                    onClick = {
                        onIntent(ItemEditorIntent.SaveOccurrence(scope = ItemChangeScope.ThisOccurrence))
                    },
                ),
                TaskBDialogChoice(
                    label = "This and following events",
                    onClick = {
                        onIntent(
                            ItemEditorIntent.SaveOccurrence(scope = ItemChangeScope.ThisAndFollowing)
                        )
                    },
                ),
            ),
            onDismiss = { onIntent(ItemEditorIntent.DismissDialog) },
        )
        null -> Unit
    }
}
