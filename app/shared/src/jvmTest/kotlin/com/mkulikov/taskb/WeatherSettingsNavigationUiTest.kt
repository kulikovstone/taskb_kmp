package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.weather.api.model.*
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class WeatherSettingsNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `settings opens from home and calendar and changing the city updates the retained calendar`() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val city = WeatherLocation("Paris, France", 48.85, 2.35, TimeZone.currentSystemDefault().id)
        val selectedLocation = MutableStateFlow<WeatherLocation?>(null)
        val weather = object : IWeatherRepository {
            override fun observeLocation() = selectedLocation
            override fun observeForecast() = selectedLocation.map { value ->
                WeatherForecast(value, if (value == null) emptyList() else listOf(DailyWeather(today, WeatherCondition.Clear, 10.0, 20.0)))
            }
            override suspend fun searchLocations(query: String) = listOf(city)
            override suspend fun setLocation(location: WeatherLocation?) { selectedLocation.value = location }
        }
        withAppKoin(directory.root.toPath().resolve("weather-settings.db")) { app ->
            app.koin.loadModules(listOf(module { single<IWeatherRepository> { weather } }))
            runBlocking { app.koin.get<IAppSettings>().completeWelcome() }
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { TaskBApp(app.koin.get()) } } }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("Settings").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Weather is off. Choose a city to enable it.").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Search city").performTextInput("Paris")
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(city.name).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText(city.name).performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Turn off weather").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Back").performClick()
                onNodeWithText("Calendar").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("calendar-weather-$today", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("calendar-weather-$today", useUnmergedTree = true).assertIsDisplayed()
                onNodeWithTag("calendar-weather-settings").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Turn off weather").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("Turn off weather").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Weather is off. Choose a city to enable it.").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Back").performClick()
                onNodeWithTag("calendar-grid").assertIsDisplayed()
                onNodeWithTag("calendar-weather-$today", useUnmergedTree = true).assertDoesNotExist()
            }
        }
    }

}
