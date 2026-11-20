package com.mkulikov.taskb.feature.calendar.screen.calendar_two

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.calendar.screen.calendar.timedCalendarPreviewState
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.feature.task.api.model.TaskFilter
import com.mkulikov.taskb.ui.dialog.TaskBDialog
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

private val mainFilters = listOf(
    TaskFilter.Scheduled,
    TaskFilter.Overdue,
    TaskFilter.Today,
    TaskFilter.Tomorrow,
    TaskFilter.NoDate,
)

@Composable
internal fun Calendar2FiltersDialog(
    groups: List<Group>,
    groupCounts: Map<Int?, Int>,
    filter: TaskFilter?,
    groupId: Int?,
    onDismiss: () -> Unit,
    onApply: (TaskFilter?, Int?) -> Unit,
) {
    TaskBDialog(onDismissRequest = onDismiss) {
        Calendar2FiltersPanel(
            groups = groups,
            groupCounts = groupCounts,
            filter = filter,
            groupId = groupId,
            onDismiss = onDismiss,
            onApply = onApply,
            modifier = Modifier.padding(all = 16.dp),
        )
    }
}

@Composable
private fun Calendar2FiltersPanel(
    groups: List<Group>,
    groupCounts: Map<Int?, Int>,
    filter: TaskFilter?,
    groupId: Int?,
    onDismiss: () -> Unit,
    onApply: (TaskFilter?, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftFilter by rememberSaveable { mutableStateOf(value = filter) }
    var draftGroup by rememberSaveable { mutableStateOf(value = groupId) }
    val colors = TaskBTheme.colors
    Surface(
        modifier = modifier
            .widthIn(max = 800.dp)
            .fillMaxWidth()
            .testTag(tag = "calendar-2-filters"),
        shape = RoundedCornerShape(size = 10.dp),
        color = colors.surface,
    ) {
        Column(
            modifier = Modifier.padding(all = 24.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(height = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filters",
                    modifier = Modifier.weight(weight = 1f),
                    color = colors.heading,
                    style = TaskBTheme.typography.title,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(size = 48.dp)) {
                    Icon(imageVector = TaskBIcons.Clear, contentDescription = "Close filters", tint = colors.muted)
                }
            }
            Column(
                modifier = Modifier.weight(weight = 1f, fill = false).verticalScroll(state = rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(space = 24.dp),
            ) {
                FilterSection(title = "Main filters") {
                    for (option in mainFilters) {
                        FilterOption(label = option.label, selected = draftFilter == option) { selected ->
                            draftFilter = option.takeIf { selected }
                        }
                    }
                }
                FilterSection(title = "Groups") {
                    FilterOption(
                        label = "No group",
                        selected = draftGroup == noGroupFilterId,
                        groupTint = colors.muted,
                        count = groupCounts[null] ?: 0,
                    ) { selected ->
                        draftGroup = noGroupFilterId.takeIf { selected }
                    }
                    for (group in groups.sortedBy { group -> group.name.lowercase() }) {
                        FilterOption(
                            label = group.name,
                            selected = draftGroup == group.id,
                            groupTint = group.color.color,
                            count = groupCounts[group.id] ?: 0,
                        ) { selected ->
                            draftGroup = group.id.takeIf { selected }
                        }
                    }
                }
            }
            Button(
                onClick = { onApply(draftFilter, draftGroup) },
                modifier = Modifier
                    .align(alignment = Alignment.End)
                    .padding(top = 8.dp)
                    .size(width = 96.dp, height = 44.dp),
                shape = RoundedCornerShape(size = 10.dp),
                elevation = null,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.actionFill,
                    contentColor = colors.onAction,
                ),
            ) {
                Text(text = "Apply", style = TaskBTheme.typography.button)
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 12.dp)) {
        Text(
            text = title,
            modifier = Modifier.height(height = 24.dp).wrapContentHeight(),
            color = TaskBTheme.colors.heading,
            style = TaskBTheme.typography.section,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun FilterOption(
    label: String,
    selected: Boolean,
    groupTint: Color? = null,
    count: Int? = null,
    onToggle: (Boolean) -> Unit,
) {
    val colors = TaskBTheme.colors
    val shape = RoundedCornerShape(size = 8.dp)
    val fill = if (selected) {
        Modifier.background(color = colors.selected)
    } else {
        Modifier.border(width = 1.dp, color = colors.border, shape = shape)
    }
    Row(
        modifier = Modifier
            .height(height = 40.dp)
            .clip(shape = shape)
            .then(other = fill)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        if (groupTint != null) {
            Icon(
                imageVector = TaskBIcons.FolderFilled,
                contentDescription = null,
                tint = groupTint,
                modifier = Modifier.size(size = 14.dp),
            )
        }
        Text(
            text = label,
            color = if (selected) colors.accent else colors.text,
            style = TaskBTheme.typography.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f, fill = false),
        )
        if (count != null) {
            Text(text = count.toString(), color = colors.muted, style = TaskBTheme.typography.small)
        }
        if (selected) {
            Icon(
                imageVector = TaskBIcons.Done,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(size = 24.dp),
            )
        }
    }
}

@Preview(name = "Calendar 2 – filters", widthDp = 800, heightDp = 404)
@Composable
private fun Calendar2FiltersPreview() {
    TaskBTheme(darkTheme = false) {
        val state = timedCalendarPreviewState()
        Calendar2FiltersPanel(
            groups = state.groups,
            groupCounts = state.tasks.groupingBy { task -> task.groupId }.eachCount(),
            filter = null,
            groupId = state.groups.firstOrNull()?.id,
            onDismiss = {},
            onApply = { _, _ -> },
        )
    }
}
