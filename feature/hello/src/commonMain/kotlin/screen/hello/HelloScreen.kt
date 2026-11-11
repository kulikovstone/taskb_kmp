package com.mkulikov.taskb.feature.hello.screen.hello

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun HelloScreen(error: String? = null, onContinue: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(all = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).verticalScroll(state = rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 24.dp),
        ) {
            Text(
                text = "Welcome to TaskB",
                style = MaterialTheme.typography.h4,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onContinue) {
                Text(text = "Continue", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (error != null) {
                Text(
                    text = error,
                    color = TaskBTheme.colors.danger,
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(name = "Welcome", widthDp = 400, heightDp = 720)
@Composable
private fun HelloScreenPreview() {
    TaskBTheme(darkTheme = false) {
        Surface(color = TaskBTheme.colors.surface) {
            HelloScreen(onContinue = {})
        }
    }
}

@Preview(name = "Welcome with error", widthDp = 400, heightDp = 720)
@Composable
private fun HelloScreenErrorPreview() {
    TaskBTheme(darkTheme = true) {
        Surface(color = TaskBTheme.colors.surface) {
            HelloScreen(
                error = "Couldn't read settings. Press Continue to get started.",
                onContinue = {},
            )
        }
    }
}
