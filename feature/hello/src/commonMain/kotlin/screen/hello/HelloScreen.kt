package com.mkulikov.taskb.feature.hello.screen.hello

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkulikov.taskb.feature.hello.resources.Res
import com.mkulikov.taskb.feature.hello.resources.welcome_birds
import com.mkulikov.taskb.ui.button.TaskBButton
import com.mkulikov.taskb.ui.theme.TaskBTheme
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun HelloScreen(error: String? = null, onContinue: () -> Unit) {
    val surface = TaskBTheme.colors.surface
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(color = surface)) {
        if (maxWidth >= 840.dp) {
            WelcomeIllustration(modifier = Modifier.matchParentSize())
            Surface(
                modifier = Modifier
                    .align(alignment = Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .width(width = 368.dp),
                shape = RoundedCornerShape(size = 24.dp),
                color = surface,
            ) {
                WelcomeContent(
                    error = error,
                    onContinue = onContinue,
                    actionSpacing = 16.dp,
                    modifier = Modifier
                        .verticalScroll(state = rememberScrollState())
                        .padding(all = 32.dp),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                WelcomeIllustration(modifier = Modifier.weight(weight = 1f).fillMaxWidth())
                WelcomeContent(
                    error = error,
                    onContinue = onContinue,
                    actionSpacing = 24.dp,
                    modifier = Modifier
                        .widthIn(max = 352.dp)
                        .fillMaxWidth()
                        .verticalScroll(state = rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun WelcomeIllustration(modifier: Modifier) {
    Image(
        painter = painterResource(resource = Res.drawable.welcome_birds),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.testTag(tag = "welcome-illustration"),
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun WelcomeContent(error: String?, onContinue: () -> Unit, actionSpacing: Dp, modifier: Modifier) {
    val colors = TaskBTheme.colors
    val typography = TaskBTheme.typography
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = "Welcome to TaskB",
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .wrapContentHeight(align = Alignment.CenterVertically)
                    .semantics { heading() },
                color = colors.heading,
                style = typography.display.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Plan your day with all your tasks\nand events in one place.",
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .wrapContentHeight(align = Alignment.CenterVertically),
                color = colors.secondary,
                style = typography.body.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(height = actionSpacing))
        // The button has room around it for Compose's expanded touch target.
        CompositionLocalProvider(value = LocalMinimumInteractiveComponentEnforcement provides false) {
            TaskBButton(
                label = "Get started",
                onClick = onContinue,
                modifier = Modifier.sizeIn(minWidth = 136.dp, minHeight = 44.dp),
            )
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(height = 12.dp))
            Text(text = error, color = colors.danger, style = typography.body, textAlign = TextAlign.Center)
        }
    }
}

@Preview(name = "Welcome", widthDp = 1440, heightDp = 900)
@Preview(name = "Welcome – compact", widthDp = 400, heightDp = 900)
@Composable
private fun HelloScreenPreview() {
    TaskBTheme(darkTheme = false) {
        HelloScreen(onContinue = {})
    }
}

@Preview(name = "Welcome with error", widthDp = 400, heightDp = 720)
@Composable
private fun HelloScreenErrorPreview() {
    TaskBTheme(darkTheme = true) {
        HelloScreen(error = "Couldn't read settings.", onContinue = {})
    }
}
