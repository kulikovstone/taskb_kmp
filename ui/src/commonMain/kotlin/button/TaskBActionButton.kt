package com.mkulikov.taskb.ui.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
fun TaskBActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        enabled = enabled,
        shape = CircleShape,
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = TaskBTheme.colors.accent
                .copy(alpha = 0.12f)
                .compositeOver(background = TaskBTheme.colors.surface),
            contentColor = TaskBTheme.colors.text,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(size = 18.dp))
        Spacer(modifier = Modifier.width(width = 8.dp))
        Text(text = label)
    }
}
