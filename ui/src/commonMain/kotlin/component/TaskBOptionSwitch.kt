package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.TaskBTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TaskBOptionSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    minHeight: Dp = 48.dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(weight = 1f),
            style = TaskBTheme.typography.body,
            color = TaskBTheme.colors.text,
        )
        CompositionLocalProvider(value = LocalMinimumInteractiveComponentEnforcement provides false) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = label },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TaskBTheme.colors.accent,
                    checkedTrackColor = TaskBTheme.colors.accent,
                    uncheckedThumbColor = TaskBTheme.colors.muted,
                    uncheckedTrackColor = TaskBTheme.colors.muted,
                ),
            )
        }
    }
}
