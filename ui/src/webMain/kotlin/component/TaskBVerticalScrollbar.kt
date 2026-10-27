package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun TaskBVerticalScrollbar(state: LazyListState, modifier: Modifier) {
    // The platform uses gesture scrolling without a separate draggable scrollbar.
}
