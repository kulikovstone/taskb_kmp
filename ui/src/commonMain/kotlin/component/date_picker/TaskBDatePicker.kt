package com.mkulikov.taskb.ui.component.date_picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.dialog.TaskBDialog
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.yearMonth

@Composable
fun TaskBDatePicker(
    date: LocalDate?,
    today: LocalDate,
    onChoose: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    availableHeight: Dp,
) {
    val state = rememberSaveable(date, today, saver = DatePickerState.Saver) { DatePickerState((date ?: today).yearMonth) }
    TaskBDialog(onDismiss) {
        Box(Modifier.widthIn(max = 392.dp).padding(horizontal = 16.dp)) {
            DatePickerContent(date, today, state, onChoose, onDismiss,
                Modifier.fillMaxWidth().heightIn(max = (availableHeight - 48.dp).coerceAtLeast(240.dp)))
        }
    }
}

@Composable
internal fun DatePickerContent(
    date: LocalDate?, today: LocalDate, state: DatePickerState,
    onChoose: (LocalDate) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.view) { scroll.scrollTo(0) }
    val shape = RoundedCornerShape(10.dp)
    Surface(modifier.shadow(4.dp, shape).testTag("date-picker-panel"), shape = shape, color = TaskBTheme.colors.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Choose date", Modifier.height(28.dp), style = TaskBTheme.typography.title, color = TaskBTheme.colors.heading)
            DatePickerNavigation(state)
            Box(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll)) {
                when (state.view) {
                    DatePickerState.View.Days -> DatePickerCalendar(state.month, date, today, onChoose)
                    DatePickerState.View.Months -> DatePickerMonths(state)
                    DatePickerState.View.Years -> DatePickerYears(state)
                }
            }
            Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ state.showToday(today.yearMonth) }, Modifier.width(64.dp).fillMaxHeight()) {
                    Text("Today", style = TaskBTheme.typography.button, color = TaskBTheme.colors.accent)
                }
                TextButton(onDismiss, Modifier.width(72.dp).fillMaxHeight()) {
                    Text("Cancel", style = TaskBTheme.typography.button, color = TaskBTheme.colors.accent)
                }
            }
        }
    }
}

@Composable
private fun DatePickerNavigation(state: DatePickerState) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 320.dp
        val small = maxWidth < 280.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 8.dp)) {
            DatePickerArrow("Previous month", TaskBIcons.ChevronLeft, { state.moveMonth(false) }, state.canGoBack, if (small) 32.dp else 40.dp)
            val monthNames = if (compact) MonthNames.ENGLISH_ABBREVIATED else MonthNames.ENGLISH_FULL
            PeriodSelector(monthNames.names[state.month.month.ordinal], "Choose month", state.view == DatePickerState.View.Months,
                state::toggleMonths, Modifier.weight(1f))
            PeriodSelector(state.month.year.toString(), "Choose year", state.view == DatePickerState.View.Years,
                state::toggleYears, Modifier.width(if (compact) 80.dp else 96.dp))
            DatePickerArrow("Next month", TaskBIcons.ChevronRight, { state.moveMonth(true) }, state.canGoForward, if (small) 32.dp else 40.dp)
        }
    }
}

@Composable
internal fun DatePickerArrow(description: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true, width: Dp = 40.dp, height: Dp = 44.dp) {
    Box(Modifier.width(width).height(height).clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(18.dp), tint = TaskBTheme.colors.muted.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

@Composable
private fun PeriodSelector(label: String, description: String, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.height(44.dp).clip(shape).background(if (active) TaskBTheme.colors.selected else TaskBTheme.colors.surface)
        .border(1.dp, if (active) TaskBTheme.colors.accent else TaskBTheme.colors.border, shape)
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description; selected = active }
        .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = TaskBTheme.typography.button, color = TaskBTheme.colors.text, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(TaskBIcons.Dropdown, null, Modifier.size(18.dp), tint = TaskBTheme.colors.muted)
    }
}

@Preview(name = "Date picker", widthDp = 360, heightDp = 476)
@Composable
private fun DatePickerPreview() {
    val today = LocalDate(2026, 11, 1)
    TaskBTheme(false) { DatePickerContent(null, today, DatePickerState(today.yearMonth), {}, {}, Modifier.width(360.dp)) }
}
