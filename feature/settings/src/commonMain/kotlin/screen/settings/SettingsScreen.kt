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
    val query = rememberTextFieldState(state.query)
    val currentQuery by rememberUpdatedState(state.query)
    LaunchedEffect(state.query) { if (query.text.toString() != state.query) query.setTextAndPlaceCursorAtEnd(state.query) }
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { if (it != currentQuery) onIntent(SettingsIntent.Search(it)) }
    }
    Surface(Modifier.fillMaxSize(), color = colors.surface) {
        Column {
            TaskBTopAppBar("Settings", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Calendar weather", style = TaskBTheme.typography.title, color = colors.heading)
                    Text("Show a 10-day forecast next to day numbers in the calendar. Temperatures are in °C.",
                        style = TaskBTheme.typography.body, color = colors.secondary)
                    if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent)
                    else {
                        Text(state.location?.name ?: "Weather is off. Choose a city to enable it.",
                            style = TaskBTheme.typography.button, color = colors.text, modifier = Modifier.testTag("settings-weather-location"))
                        if (state.location != null) TextButton({ onIntent(SettingsIntent.DisableWeather) }, enabled = !state.isSaving) {
                            Text("Turn off weather", color = colors.accent)
                        }
                    }
                    TaskBTextField(query, "Search city", placeholder = "City name", search = true,
                        enabled = !state.isLoading && !state.isSaving, modifier = Modifier.fillMaxWidth().testTag("settings-city-search"))
                    if (state.isSearching || state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent)
                    state.error?.let {
                        Text(it, color = colors.danger, style = TaskBTheme.typography.body)
                        TextButton({ if (state.query.trim().length >= 2) onIntent(SettingsIntent.Search(state.query)) else onIntent(SettingsIntent.RetryLoad) }) {
                            Text("Try again", color = colors.accent)
                        }
                    }
                    if (state.hasSearched && state.results.isEmpty()) Text("No cities found. Try another name.", color = colors.muted)
                    state.results.forEach { location ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = !state.isSaving, role = Role.Button) {
                            onIntent(SettingsIntent.SelectLocation(location))
                        }.padding(vertical = 12.dp)) {
                            Text(location.name, style = TaskBTheme.typography.body, color = colors.text)
                            Text(location.timeZone, style = TaskBTheme.typography.small, color = colors.muted)
                        }
                        Divider(color = colors.border)
                    }
                    Text("Forecast dates follow the selected city's time zone.", style = TaskBTheme.typography.small, color = colors.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Weather data by Open-Meteo", style = TaskBTheme.typography.small, color = colors.accent,
                            modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri("https://open-meteo.com/") })
                        Text("CC BY 4.0", style = TaskBTheme.typography.small, color = colors.accent,
                            modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri("https://creativecommons.org/licenses/by/4.0/") })
                    }
                }
            }
        }
    }
}

@Preview(name = "Settings", widthDp = 800, heightDp = 700)
@Composable
private fun SettingsPreview() {
    TaskBTheme(false) { SettingsScreen(SettingsState(isLoading = false), {}, {}) }
}
