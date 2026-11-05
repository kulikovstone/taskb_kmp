package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.calendar.resources.Res
import com.mkulikov.taskb.feature.calendar.resources.day_completed
import com.mkulikov.taskb.ui.theme.TaskBTheme
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun CalendarDayCompleted(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag("calendar-day-completed"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.day_completed),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "All done for today",
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            color = TaskBTheme.colors.heading,
            style = TaskBTheme.typography.section,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Enjoy your free time.",
            modifier = Modifier.fillMaxWidth(),
            color = TaskBTheme.colors.muted,
            style = TaskBTheme.typography.body,
            textAlign = TextAlign.Center,
        )
    }
}
