package com.mkulikov.taskb.feature.event.screen.event_editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.mkulikov.taskb.ui.dialog.TaskBConfirmationDialog
import com.mkulikov.taskb.ui.dialog.TaskBMessageDialog
import com.mkulikov.taskb.ui.dialog.TaskBChoiceDialog
import com.mkulikov.taskb.ui.dialog.TaskBDialogChoice
import com.mkulikov.taskb.ui.component.date_picker.TaskBDatePicker
import com.mkulikov.taskb.feature.event.api.model.EventChangeScope
import kotlinx.datetime.LocalDate

@Composable
internal fun EventEditorDialogs(state: EventEditorState, today: LocalDate, onIntent: (EventEditorIntent) -> Unit, availableHeight: Dp) {
    when (val dialog = state.dialog) {
        EventEditorDialog.ConfirmDelete -> TaskBConfirmationDialog("Delete event", "Delete \"${state.normalizedName}\"?", "Delete",
            { onIntent(EventEditorIntent.ConfirmDelete) }, { onIntent(EventEditorIntent.DismissDialog) })
        is EventEditorDialog.Error -> TaskBMessageDialog("Event", dialog.message) { onIntent(EventEditorIntent.DismissDialog) }
        EventEditorDialog.DatePicker -> TaskBDatePicker(state.dueDate, today, { onIntent(EventEditorIntent.DateChanged(it)) }, { onIntent(EventEditorIntent.DismissDialog) }, availableHeight)
        EventEditorDialog.RepeatEndDatePicker -> TaskBDatePicker(state.recurrence?.endDate, today, { onIntent(EventEditorIntent.RepeatEndDateChanged(it)) }, { onIntent(EventEditorIntent.DismissDialog) }, availableHeight)
        EventEditorDialog.EventEndDatePicker -> TaskBDatePicker(state.endDate ?: state.dueDate, today, { onIntent(EventEditorIntent.EventEndDateChanged(it)) }, { onIntent(EventEditorIntent.DismissDialog) }, availableHeight)
        EventEditorDialog.StartTimePicker -> EventTimePickerDialog("Choose start time", state.startTime, availableHeight,
            { onIntent(EventEditorIntent.StartTimeChanged(it)) }, { onIntent(EventEditorIntent.DismissDialog) })
        EventEditorDialog.EndTimePicker -> EventTimePickerDialog("Choose end time", state.endTime ?: state.startTime, availableHeight,
            { onIntent(EventEditorIntent.EndTimeChanged(it)) }, { onIntent(EventEditorIntent.DismissDialog) })
        EventEditorDialog.DeleteOccurrence -> TaskBChoiceDialog("Delete repeating event", "Choose which occurrences to delete.", listOf(
            TaskBDialogChoice("Only this event", { onIntent(EventEditorIntent.DeleteOccurrence(EventChangeScope.ThisOccurrence)) }, true),
            TaskBDialogChoice("This and following events", { onIntent(EventEditorIntent.DeleteOccurrence(EventChangeScope.ThisAndFollowing)) }, true),
            TaskBDialogChoice("Entire event and all occurrences", { onIntent(EventEditorIntent.DeleteOccurrence(EventChangeScope.EntireEvent)) }, true),
        ), { onIntent(EventEditorIntent.DismissDialog) })
        EventEditorDialog.MoveOccurrence -> TaskBChoiceDialog("Move repeating event", "Choose which occurrences to move. Name, description and group changes apply to the entire event.", listOf(
            TaskBDialogChoice("Only this event", { onIntent(EventEditorIntent.SaveOccurrence(EventChangeScope.ThisOccurrence)) }),
            TaskBDialogChoice("This and following events", { onIntent(EventEditorIntent.SaveOccurrence(EventChangeScope.ThisAndFollowing)) }),
        ), { onIntent(EventEditorIntent.DismissDialog) })
        null -> Unit
    }
}
