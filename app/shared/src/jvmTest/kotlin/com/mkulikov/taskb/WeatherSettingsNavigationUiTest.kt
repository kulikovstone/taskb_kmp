package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.holiday.api.repository.IHolidayRepository
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
    fun `shared location settings update weather and holidays when reopening the calendar`() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val city = AppLocation("Paris, France", 48.85, 2.35, TimeZone.currentSystemDefault().id, "FR", "Île-de-France")
        val selectedLocation = MutableStateFlow<AppLocation?>(null)
        val locations = object : IAppLocationRepository {
            override fun observeLocation() = selectedLocation
            override suspend fun searchLocations(query: String) = listOf(city)
            override suspend fun setLocation(location: AppLocation?) { selectedLocation.value = location }
        }
        val holidays = object : IHolidayRepository {
            override fun observeHolidays(from: LocalDate, through: LocalDate) = selectedLocation.map { value ->
                HolidayCalendar(value, if (value == null || today !in from..through) emptyList()
                    else listOf(PublicHoliday("fr-example", today, "Public holiday", "FR")))
            }
        }
        val weather = object : IWeatherRepository {
            override fun observeForecast() = selectedLocation.map { value ->
                WeatherForecast(value, if (value == null) emptyList() else listOf(DailyWeather(today, WeatherCondition.Clear, 10.0, 20.0)))
            }
        }
        withAppKoin(directory.root.toPath().resolve("weather-settings.db")) { app ->
            app.koin.loadModules(listOf(module {
                single<IWeatherRepository> { weather }
                single<IAppLocationRepository> { locations }
                single<IHolidayRepository> { holidays }
            }))
            runBlocking { app.koin.get<IAppSettings>().completeWelcome() }
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent { KoinIsolatedContext(app) { TaskBTheme(false) { TaskBApp(app.koin.get()) } } }
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("Settings").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("No location selected. Choose a city to enable weather and public holidays.").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Search city").performTextInput("Paris")
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(city.name).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText(city.name).performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Clear location").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Back").performClick()
                onNodeWithText("Calendar").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("calendar-weather-$today", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("calendar-weather-$today", useUnmergedTree = true).assertIsDisplayed()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Public holiday", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Back").performClick()
                onNodeWithText("Settings").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Clear location").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("Clear location").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("No location selected. Choose a city to enable weather and public holidays.").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("Back").performClick()
                onNodeWithText("Calendar").performClick()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("calendar-grid").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("calendar-grid").assertIsDisplayed()
                onNodeWithTag("calendar-weather-$today", useUnmergedTree = true).assertDoesNotExist()
                onAllNodesWithText("Public holiday", useUnmergedTree = true).assertCountEquals(0)
            }
        }
    }

}
