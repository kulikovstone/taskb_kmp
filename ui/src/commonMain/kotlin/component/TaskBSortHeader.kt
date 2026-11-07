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
    contentPadding: PaddingValues = PaddingValues(all = 0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
) {
    val colors = TaskBTheme.colors
    Row(
        modifier = modifier
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Sort by $label" }
            .padding(paddingValues = contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
    ) {
        Text(text = label, style = TaskBTheme.typography.label, color = colors.secondary, maxLines = 1)
        if (isActive) {
            Spacer(modifier = Modifier.width(width = 4.dp))
            Icon(
                imageVector = TaskBIcons.Sort,
                contentDescription = null,
                tint = if (reverseSort) colors.danger else colors.success,
                modifier = Modifier.size(size = 14.dp).rotate(degrees = if (reverseSort) 180f else 0f),
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
                modifier = Modifier.padding(all = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 24.dp),
            ) {
                TaskBSortHeader(label = "Unsorted", isActive = false, reverseSort = false, onClick = {})
                TaskBSortHeader(label = "Ascending", isActive = true, reverseSort = false, onClick = {})
                TaskBSortHeader(label = "Descending", isActive = true, reverseSort = true, onClick = {})
            }
        }
    }
}
