package com.mkulikov.taskb.feature.item.screen.item_editor

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun ItemSelectionField(
    label: String,
    icon: ImageVector,
    tint: Color,
    placeholder: Boolean,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
    clearDescription: String,
    height: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(size = 8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = height)
            .clip(shape = shape)
            .border(width = 1.dp, color = TaskBTheme.colors.border, shape = shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(weight = 1f)
                .fillMaxHeight()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size = 24.dp))
            Text(
                text = label,
                color = if (placeholder) TaskBTheme.colors.muted else TaskBTheme.colors.text,
                style = TaskBTheme.typography.body,
                modifier = Modifier.weight(weight = 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = TaskBIcons.ChevronRight,
                contentDescription = null,
                tint = TaskBTheme.colors.muted,
                modifier = Modifier.size(size = 18.dp),
            )
        }
        onClear?.let { clear ->
            Box(
                modifier = Modifier
                    .width(width = 44.dp)
                    .fillMaxHeight()
                    .clickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = clear,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TaskBIcons.Clear,
                    contentDescription = clearDescription,
                    tint = TaskBTheme.colors.muted,
                )
            }
        }
    }
}
