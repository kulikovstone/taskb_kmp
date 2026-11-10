package com.mkulikov.taskb.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun TaskBDialogPanel(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    actionSpacing: Dp = 8.dp,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit = {},
) {
    val colors = TaskBTheme.colors
    val typography = TaskBTheme.typography
    TaskBDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(all = 16.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(size = 28.dp),
            color = colors.surface,
        ) {
            Column(
                modifier = Modifier
                    .testTag(tag = "taskb-dialog-panel")
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(weight = 1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(state = rememberScrollState()),
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.heightIn(min = 24.dp).wrapContentHeight(),
                        color = colors.heading,
                        style = typography.section,
                    )
                    Spacer(modifier = Modifier.height(height = 12.dp))
                    Text(
                        text = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 22.dp)
                            .wrapContentHeight(),
                        color = colors.text,
                        style = typography.body,
                    )
                    content()
                }
                Spacer(modifier = Modifier.height(height = actionSpacing))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(space = 4.dp),
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
internal fun TaskBDialogAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = TaskBTheme.colors
    TextButton(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 80.dp)
            .heightIn(min = 40.dp),
        shape = RoundedCornerShape(size = 8.dp),
    ) {
        Text(
            text = label,
            color = if (destructive) colors.danger else colors.accent,
            style = TaskBTheme.typography.button,
        )
    }
}
