package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.group.api.model.Group
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.flow.filterNot

@Composable
internal fun Modifier.quickFilterClick(enabled: Boolean, onClickLabel: String, onClick: () -> Unit): Modifier {
    val interactions = remember {
        val source = MutableInteractionSource()
        object : MutableInteractionSource by source {
            override val interactions = source.interactions.filterNot { interaction -> interaction is HoverInteraction }
        }
    }
    return clickable(
        interactionSource = interactions,
        indication = LocalIndication.current,
        enabled = enabled,
        role = Role.Button,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
}

@Composable
internal fun CalendarGroupLabel(
    group: Group,
    enabled: Boolean,
    onFilterByGroup: ((Int) -> Unit)?,
    style: TextStyle = TaskBTheme.typography.small,
) {
    val modifier = if (onFilterByGroup == null) {
        Modifier
    } else {
        Modifier.quickFilterClick(enabled = enabled, onClickLabel = "Filter by group") {
            onFilterByGroup(group.id)
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Icon(
            imageVector = TaskBIcons.FolderFilled,
            contentDescription = null,
            tint = group.color.color,
            modifier = Modifier.size(size = 14.dp),
        )
        Text(
            text = group.name,
            color = TaskBTheme.colors.muted,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
