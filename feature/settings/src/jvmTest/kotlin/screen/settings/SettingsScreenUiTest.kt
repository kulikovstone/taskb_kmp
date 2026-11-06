package com.mkulikov.taskb.feature.settings.screen.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class SettingsScreenUiTest {
    @Test
    fun `city search selects a city and can turn weather off in a compact window`() = runSkikoComposeUiTest(size = Size(420f, 720f)) {
        val city = WeatherLocation("Paris, Île-de-France, France", 48.85, 2.35, "Europe/Paris")
        val location = MutableStateFlow<WeatherLocation?>(null)
        val repository = object : IWeatherRepository {
            override fun observeLocation() = location
            override fun observeForecast() = flowOf(WeatherForecast())
            override suspend fun searchLocations(query: String) = if (query == "Paris") listOf(city) else emptyList()
            override suspend fun setLocation(value: WeatherLocation?) { location.value = value }
        }
        SettingsStore(repository).use { store ->
            setContent {
                val state = store.state.collectAsState().value
                TaskBTheme(false) { SettingsScreen(state, store::accept, {}) }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Weather is off. Choose a city to enable it.").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("Search city").performTextInput("Paris")
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(city.name).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText(city.name).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Turn off weather").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("settings-weather-location").assertTextEquals(city.name)
            onNodeWithContentDescription("Search city").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            val path = Path.of("build/reports/screenshots/settings-weather-compact.png")
            Files.createDirectories(path.parent)
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.toFile())
            onNodeWithText("Turn off weather").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Weather is off. Choose a city to enable it.").fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
