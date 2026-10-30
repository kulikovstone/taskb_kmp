package com.mkulikov.taskb.feature.task.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.component.TaskBVerticalScrollbar
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import com.mkulikov.taskb.feature.group.api.model.Group

@Composable
internal fun GroupSelectionContent(
    groups: List<Group>, selectedId: Int?, noGroupSelected: Boolean, query: String,
    onSearch: (String) -> Unit, onSelect: (Int?) -> Unit, modifier: Modifier, scrollbar: Boolean = false,
) {
    Column(modifier) {
        TaskBTextField(query, onSearch, "Search groups", placeholder = "Search groups", search = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        val scroll = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth().selectableGroup()) {
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(end = if (scrollbar) 20.dp else 0.dp)) {
                val search = query.trim()
                if ("No group".contains(search, true)) item(key = "none") {
                    SelectionRow("No group", noGroupSelected, { onSelect(null) }, TaskBTheme.colors.muted)
                }
                val matching = groups.filter { it.name.contains(search, true) }.sortedBy { it.name.lowercase() }
                items(matching, key = { it.id }) { group -> SelectionRow(group.name, selectedId == group.id, { onSelect(group.id) }, group.color.color) }
                if (matching.isEmpty() && !"No group".contains(search, true)) item {
                    Text("No groups match your search.", color = TaskBTheme.colors.muted, modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            if (scrollbar && (scroll.canScrollForward || scroll.canScrollBackward)) TaskBVerticalScrollbar(scroll,
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().background(TaskBTheme.colors.border.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
internal fun SelectionRow(label: String, selected: Boolean, onClick: () -> Unit, groupColor: androidx.compose.ui.graphics.Color? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(63.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (groupColor != null) Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Icon(TaskBIcons.FolderFilled, null, tint = groupColor, modifier = Modifier.size(24.dp)) }
            Text(label, style = TaskBTheme.typography.section, color = TaskBTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected) Icon(TaskBIcons.Done, null, tint = TaskBTheme.colors.accent, modifier = Modifier.size(24.dp))
        }
        Divider(color = TaskBTheme.colors.border)
    }
}
