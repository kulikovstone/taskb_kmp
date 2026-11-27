package com.mkulikov.taskb.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
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
        modifier = modifier.height(height = 44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(size = 10.dp),
        border = if (!enabled && !destructive) BorderStroke(width = 1.dp, color = colors.border) else null,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (destructive) Palette.Transparent else colors.actionFill,
            contentColor = foreground,
            disabledBackgroundColor = if (destructive) Palette.Transparent else colors.surface,
            disabledContentColor = colors.muted.copy(alpha = 0.5f),
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.size(size = 18.dp),
        )
        Spacer(modifier = Modifier.width(width = 8.dp))
        Text(text = label, style = TaskBTheme.typography.button, maxLines = 1)
    }
}

@Composable
fun TaskBLoading(modifier: Modifier = Modifier, fastRotation: Boolean = false) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val indicator = Modifier.size(size = 28.dp)
        if (fastRotation) {
            val transition = rememberInfiniteTransition(label = "Loading")
            val rotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(animation = tween(durationMillis = 400, easing = LinearEasing)),
                label = "Spinner rotation",
            )
            CircularProgressIndicator(
                progress = 0.75f,
                modifier = indicator
                    .graphicsLayer { rotationZ = rotation }
                    .clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
            )
        } else {
            CircularProgressIndicator(modifier = indicator)
        }
    }
}

@Composable
fun TaskBLoadError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(all = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        Text(text = message, color = TaskBTheme.colors.muted, style = TaskBTheme.typography.body)
        TextButton(onClick = onRetry) { Text(text = "Retry") }
    }
}
