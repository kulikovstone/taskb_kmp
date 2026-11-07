package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop supplies a draggable scrollbar; touch targets use the list's scrolling. */
@Composable expect fun TaskBVerticalScrollbar(state: LazyListState, modifier: Modifier = Modifier)
