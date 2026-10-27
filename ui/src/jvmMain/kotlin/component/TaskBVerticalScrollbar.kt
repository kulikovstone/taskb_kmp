package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
actual fun TaskBVerticalScrollbar(state: LazyListState, modifier: Modifier) {
    val colors = TaskBTheme.colors
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier,
        style = LocalScrollbarStyle.current.copy(
            thickness = 4.dp,
            shape = RoundedCornerShape(2.dp),
            unhoverColor = colors.muted.copy(alpha = 0.35f),
            hoverColor = colors.muted.copy(alpha = 0.65f),
        ),
    )
}
