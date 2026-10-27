package com.mkulikov.taskb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.di.AppDependencyGraph
import org.koin.compose.KoinIsolatedContext
import org.jetbrains.compose.resources.painterResource

import taskb_kmp.app.shared.generated.resources.Res
import taskb_kmp.app.shared.generated.resources.compose_multiplatform

@Composable
expect fun App()

@Composable
internal fun App(databaseFactory: () -> AppDatabase) {
    val graph = remember(databaseFactory) { AppDependencyGraph(databaseFactory) }
    KoinIsolatedContext(context = graph.application) {
        AppContent()
    }
}

@Composable
internal fun AppContent() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = { showContent = !showContent }) {
                Text("Click me!")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}
