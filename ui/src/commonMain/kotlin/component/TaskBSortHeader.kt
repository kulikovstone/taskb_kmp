package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
fun TaskBSortHeader(
    label: String,
    isActive: Boolean,
    reverseSort: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
) {
    val colors = TaskBTheme.colors
    Row(
        modifier = modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Sort by $label" }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
    ) {
        Text(label, style = TaskBTheme.typography.label, color = colors.secondary, maxLines = 1)
        if (isActive) {
            Spacer(Modifier.width(4.dp))
            Icon(
                TaskBIcons.Sort,
                contentDescription = null,
                tint = if (reverseSort) colors.danger else colors.success,
                modifier = Modifier.size(14.dp).rotate(if (reverseSort) 180f else 0f),
            )
        }
    }
}

@Preview(name = "Light", group = "TaskBSortHeader")
@Composable
private fun TaskBSortHeaderLightPreview() {
    TaskBSortHeaderPreview(darkTheme = false)
}

@Preview(name = "Dark", group = "TaskBSortHeader")
@Composable
private fun TaskBSortHeaderDarkPreview() {
    TaskBSortHeaderPreview(darkTheme = true)
}

@Composable
private fun TaskBSortHeaderPreview(darkTheme: Boolean) {
    TaskBTheme(darkTheme = darkTheme) {
        Surface(color = TaskBTheme.colors.surface) {
            Row(
                modifier = Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                TaskBSortHeader("Unsorted", isActive = false, reverseSort = false, onClick = {})
                TaskBSortHeader("Ascending", isActive = true, reverseSort = false, onClick = {})
                TaskBSortHeader("Descending", isActive = true, reverseSort = true, onClick = {})
            }
        }
    }
}
