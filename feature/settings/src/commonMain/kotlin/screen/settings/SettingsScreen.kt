package com.mkulikov.taskb.feature.settings.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.ui.component.TaskBTextField
import com.mkulikov.taskb.ui.component.TaskBTopAppBar
import com.mkulikov.taskb.ui.theme.TaskBTheme

@Composable
internal fun SettingsScreen(state: SettingsState, onIntent: (SettingsIntent) -> Unit, onBack: () -> Unit) {
    val colors = TaskBTheme.colors
    val uriHandler = LocalUriHandler.current
    val query = rememberTextFieldState(initialText = state.query)
    val currentQuery by rememberUpdatedState(newValue = state.query)
    LaunchedEffect(key1 = state.query) {
        if (query.text.toString() != state.query) query.setTextAndPlaceCursorAtEnd(text = state.query)
    }
    LaunchedEffect(key1 = query) {
        snapshotFlow { query.text.toString() }
            .collect { text -> if (text != currentQuery) onIntent(SettingsIntent.Search(query = text)) }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Column {
            TaskBTopAppBar(title = "Settings", onBack = onBack)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(state = rememberScrollState())
                        .padding(all = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(space = 16.dp),
                ) {
                    Text(text = "Location", style = TaskBTheme.typography.title, color = colors.heading)
                    Text(
                        text = "Weather and public holidays in the calendar use this application location.",
                        style = TaskBTheme.typography.body,
                        color = colors.secondary,
                    )
                    if (state.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.accent)
                    } else {
                        Text(
                            text = state.location?.name
                                ?: "No location selected. Choose a city to enable weather and public holidays.",
                            style = TaskBTheme.typography.button,
                            color = colors.text,
                            modifier = Modifier.testTag(tag = "settings-app-location"),
                        )
                        if (state.location != null)
                            TextButton(
                                onClick = { onIntent(SettingsIntent.ClearLocation) },
                                enabled = !state.isSaving,
                            ) {
                                Text(text = "Clear location", color = colors.accent)
                            }
                    }
                    if (state.location != null && state.location.countryCode == null) {
                        Text(
                            text =
                                ("The saved location's country is checked automatically. If holidays " +
                                    "stay unavailable, choose the city again."),
                            style = TaskBTheme.typography.small,
                            color = colors.muted,
                        )
                    }
                    TaskBTextField(
                        state = query,
                        description = "Search city",
                        placeholder = "City name",
                        search = true,
                        enabled = !state.isLoading && !state.isSaving,
                        modifier = Modifier.fillMaxWidth().testTag(tag = "settings-city-search"),
                    )
                    if (state.isSearching || state.isSaving)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.accent)
                    state.error?.let { message ->
                        Text(text = message, color = colors.danger, style = TaskBTheme.typography.body)
                        TextButton(
                            onClick = {
                                if (state.query.trim().length >= 2) {
                                    onIntent(SettingsIntent.Search(query = state.query))
                                } else {
                                    onIntent(SettingsIntent.RetryLoad)
                                }
                            }
                        ) {
                            Text(text = "Try again", color = colors.accent)
                        }
                    }
                    if (state.hasSearched && state.results.isEmpty())
                        Text(text = "No cities found. Try another name.", color = colors.muted)
                    state.results.forEach { location ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !state.isSaving, role = Role.Button) {
                                    onIntent(SettingsIntent.SelectLocation(location = location))
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(text = location.name, style = TaskBTheme.typography.body, color = colors.text)
                            Text(
                                text = location.timeZone,
                                style = TaskBTheme.typography.small,
                                color = colors.muted,
                            )
                        }
                        Divider(color = colors.border)
                    }
                    Text(
                        text =
                            ("Forecast dates follow the selected city's time zone. Public holidays " +
                                "use its country and region."),
                        style = TaskBTheme.typography.small,
                        color = colors.muted,
                    )
                    Spacer(modifier = Modifier.height(height = 8.dp))
                    Text(text = "Data sources", style = TaskBTheme.typography.section, color = colors.heading)
                    Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                        Text(
                            text = "Weather and location data by Open-Meteo",
                            style = TaskBTheme.typography.small,
                            color = colors.accent,
                            modifier = Modifier
                                .clickable(role = Role.Button) {
                                    uriHandler.openUri(uri = "https://open-meteo.com/")
                                },
                        )
                        Text(
                            text = "CC BY 4.0",
                            style = TaskBTheme.typography.small,
                            color = colors.accent,
                            modifier = Modifier
                                .clickable(role = Role.Button) {
                                    uriHandler.openUri(uri = "https://creativecommons.org/licenses/by/4.0/")
                                },
                        )
                        Text(
                            text = "Public holidays by Enrico",
                            style = TaskBTheme.typography.small,
                            color = colors.accent,
                            modifier = Modifier
                                .clickable(role = Role.Button) {
                                    uriHandler.openUri(uri = "https://kayaposoft.com/enrico/")
                                },
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Settings", widthDp = 800, heightDp = 700)
@Composable
private fun SettingsPreview() {
    TaskBTheme(darkTheme = false) {
        SettingsScreen(state = SettingsState(isLoading = false), onIntent = {}, onBack = {})
    }
}
