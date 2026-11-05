package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
fun TaskBTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    backIcon: ImageVector = TaskBIcons.Back,
    backContentDescription: String = "Back",
    titleSpacing: Dp? = null,
    subtitle: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (titleSpacing == null) {
        TopAppBar(
            modifier = modifier.height(64.dp),
            backgroundColor = TaskBTheme.colors.surface,
            contentColor = TaskBTheme.colors.heading,
            elevation = 0.dp,
            title = {
                Column {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.h6.copy(fontSize = 22.sp, fontWeight = FontWeight.Normal),
                    )
                    subtitle?.invoke()
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(backIcon, contentDescription = backContentDescription)
                }
            },
            actions = actions,
        )
        return
    }
    TopAppBar(
        modifier = modifier.height(64.dp),
        backgroundColor = TaskBTheme.colors.surface,
        contentColor = TaskBTheme.colors.heading,
        elevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
            IconButton(onClick = onBack, enabled = backEnabled) {
                Icon(backIcon, contentDescription = backContentDescription)
            }
            Spacer(Modifier.width(titleSpacing))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.h6.copy(fontSize = 22.sp, fontWeight = FontWeight.Normal),
                )
                subtitle?.invoke()
            }
            actions()
        }
    }
}
