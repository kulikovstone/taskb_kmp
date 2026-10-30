package com.mkulikov.taskb.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.Palette
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
fun TaskBEditorButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = TaskBTheme.colors
    val foreground = if (destructive) colors.danger else if (enabled) colors.onAction else colors.muted
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = if (!enabled && !destructive) BorderStroke(1.dp, colors.border) else null,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (destructive) Palette.Transparent else colors.actionFill,
            contentColor = foreground, disabledBackgroundColor = if (destructive) Palette.Transparent else colors.surface,
            disabledContentColor = colors.muted.copy(alpha = 0.5f),
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = foreground.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = TaskBTheme.typography.button, maxLines = 1)
    }
}

@Composable
fun TaskBLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
}

@Composable
fun TaskBLoadError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, color = TaskBTheme.colors.muted, style = TaskBTheme.typography.body)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
