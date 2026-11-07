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
    val state = rememberSaveable(date, today, saver = DatePickerState.Saver) {
        DatePickerState(initialMonth = (date ?: today).yearMonth)
    }
    TaskBDialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.widthIn(max = 392.dp).padding(horizontal = 16.dp)) {
            DatePickerContent(
                date = date,
                today = today,
                state = state,
                onChoose = onChoose,
                onDismiss = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (availableHeight - 48.dp).coerceAtLeast(minimumValue = 240.dp)),
            )
        }
    }
}

@Composable
internal fun DatePickerContent(
    date: LocalDate?,
    today: LocalDate,
    state: DatePickerState,
    onChoose: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(key1 = state.view) { scroll.scrollTo(value = 0) }
    val shape = RoundedCornerShape(size = 10.dp)
    Surface(
        modifier = modifier.shadow(elevation = 4.dp, shape = shape).testTag(tag = "date-picker-panel"),
        shape = shape,
        color = TaskBTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier.padding(all = 20.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Text(
                text = "Choose date",
                modifier = Modifier.height(height = 28.dp),
                style = TaskBTheme.typography.title,
                color = TaskBTheme.colors.heading,
            )
            DatePickerNavigation(state = state)
            Box(
                modifier = Modifier.weight(weight = 1f, fill = false).fillMaxWidth().verticalScroll(state = scroll)
            ) {
                when (state.view) {
                    DatePickerState.View.Days -> DatePickerCalendar(
                        month = state.month,
                        date = date,
                        today = today,
                        onChoose = onChoose,
                    )
                    DatePickerState.View.Months -> DatePickerMonths(state = state)
                    DatePickerState.View.Years -> DatePickerYears(state = state)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(height = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { state.showToday(today = today.yearMonth) },
                    modifier = Modifier.width(width = 64.dp).fillMaxHeight(),
                ) {
                    Text(text = "Today", style = TaskBTheme.typography.button, color = TaskBTheme.colors.accent)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.width(width = 72.dp).fillMaxHeight()) {
                    Text(text = "Cancel", style = TaskBTheme.typography.button, color = TaskBTheme.colors.accent)
                }
            }
        }
    }
}

@Composable
private fun DatePickerNavigation(state: DatePickerState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 320.dp
        val small = maxWidth < 280.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = if (small) 4.dp else 8.dp),
        ) {
            DatePickerArrow(
                description = "Previous month",
                icon = TaskBIcons.ChevronLeft,
                onClick = { state.moveMonth(forward = false) },
                enabled = state.canGoBack,
                width = if (small) 32.dp else 40.dp,
            )
            val monthNames = if (compact) MonthNames.ENGLISH_ABBREVIATED else MonthNames.ENGLISH_FULL
            PeriodSelector(
                label = monthNames.names[state.month.month.ordinal],
                description = "Choose month",
                active = state.view == DatePickerState.View.Months,
                onClick = state::toggleMonths,
                modifier = Modifier.weight(weight = 1f),
            )
            PeriodSelector(
                label = state.month.year.toString(),
                description = "Choose year",
                active = state.view == DatePickerState.View.Years,
                onClick = state::toggleYears,
                modifier = Modifier.width(width = if (compact) 80.dp else 96.dp),
            )
            DatePickerArrow(
                description = "Next month",
                icon = TaskBIcons.ChevronRight,
                onClick = { state.moveMonth(forward = true) },
                enabled = state.canGoForward,
                width = if (small) 32.dp else 40.dp,
            )
        }
    }
}

@Composable
internal fun DatePickerArrow(
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    width: Dp = 40.dp,
    height: Dp = 44.dp,
) {
    Box(
        modifier = Modifier
            .width(width = width)
            .height(height = height)
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(size = 18.dp),
            tint = TaskBTheme.colors.muted.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

@Composable
private fun PeriodSelector(
    label: String,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(size = 8.dp)
    Row(
        modifier = modifier
            .height(height = 44.dp)
            .clip(shape = shape)
            .background(color = if (active) TaskBTheme.colors.selected else TaskBTheme.colors.surface)
            .border(
                width = 1.dp,
                color = if (active) TaskBTheme.colors.accent else TaskBTheme.colors.border,
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                selected = active
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(weight = 1f),
            style = TaskBTheme.typography.button,
            color = TaskBTheme.colors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = TaskBIcons.Dropdown,
            contentDescription = null,
            modifier = Modifier.size(size = 18.dp),
            tint = TaskBTheme.colors.muted,
        )
    }
}

@Preview(name = "Date picker", widthDp = 360, heightDp = 476)
@Composable
private fun DatePickerPreview() {
    val today = LocalDate(year = 2026, month = 11, day = 1)
    TaskBTheme(darkTheme = false) {
        DatePickerContent(
            date = null,
            today = today,
            state = DatePickerState(initialMonth = today.yearMonth),
            onChoose = {},
            onDismiss = {},
            modifier = Modifier.width(width = 360.dp),
        )
    }
}
