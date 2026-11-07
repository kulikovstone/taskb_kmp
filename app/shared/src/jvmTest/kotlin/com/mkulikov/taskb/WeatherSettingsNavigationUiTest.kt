package com.mkulikov.taskb

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.holiday.api.model.HolidayCalendar
import com.mkulikov.taskb.feature.holiday.api.model.PublicHoliday
import com.mkulikov.taskb.feature.holiday.api.repository.IHolidayRepository
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.feature.weather.api.model.*
import com.mkulikov.taskb.feature.weather.api.repository.IWeatherRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class WeatherSettingsNavigationUiTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun `shared location settings update weather and holidays when reopening the calendar`() {
        val today = Clock.System.now().toLocalDateTime(timeZone = TimeZone.currentSystemDefault()).date
        val city = AppLocation(
            name = "Paris, France",
            latitude = 48.85,
            longitude = 2.35,
            timeZone = TimeZone.currentSystemDefault().id,
            countryCode = "FR",
            regionName = "Île-de-France",
        )
        val selectedLocation = MutableStateFlow<AppLocation?>(value = null)
        val locations =
            object : IAppLocationRepository {
                override fun observeLocation() = selectedLocation

                override suspend fun searchLocations(query: String) = listOf(element = city)

                override suspend fun setLocation(location: AppLocation?) {
                    selectedLocation.value = location
                }
            }
        val holidays =
            object : IHolidayRepository {
                override fun observeHolidays(from: LocalDate, through: LocalDate): Flow<HolidayCalendar> {
                    return selectedLocation.map { value ->
                        HolidayCalendar(
                            location = value,
                            holidays = if (value == null || today !in from..through) {
                                emptyList()
                            } else {
                                listOf(
                                    element = PublicHoliday(
                                        id = "fr-example",
                                        date = today,
                                        name = "Public holiday",
                                        countryCode = "FR",
                                    )
                                )
                            },
                        )
                    }
                }
            }
        val weather =
            object : IWeatherRepository {
                override fun observeForecast(): Flow<WeatherForecast> {
                    return selectedLocation.map { value ->
                        WeatherForecast(
                            location = value,
                            days = if (value == null) {
                                emptyList()
                            } else {
                                listOf(
                                    element = DailyWeather(
                                        date = today,
                                        condition = WeatherCondition.Clear,
                                        minimumCelsius = 10.0,
                                        maximumCelsius = 20.0,
                                    )
                                )
                            },
                        )
                    }
                }
            }
        withAppKoin(file = directory.root.toPath().resolve("weather-settings.db")) { app ->
            app.koin.loadModules(
                modules = listOf(
                    element = module {
                        single<IWeatherRepository> { weather }
                        single<IAppLocationRepository> { locations }
                        single<IHolidayRepository> { holidays }
                    }
                )
            )
            runBlocking { app.koin.get<IAppSettings>().completeWelcome() }
            runSkikoComposeUiTest(size = Size(width = 1440f, height = 900f)) {
                setContent {
                    KoinIsolatedContext(context = app) {
                        TaskBTheme(darkTheme = false) { TaskBApp(settings = app.koin.get()) }
                    }
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText(text = "Settings").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText(text = "Settings").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText(
                            text = "No location selected. Choose a city to enable weather and public holidays."
                        )
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Search city").performTextInput(text = "Paris")
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText(text = city.name).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText(text = city.name).performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText(text = "Clear location").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithContentDescription(label = "Back").performClick()
                onNodeWithText(text = "Calendar").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithTag(testTag = "calendar-weather-$today", useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithTag(testTag = "calendar-weather-$today", useUnmergedTree = true).assertIsDisplayed()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText(text = "Public holiday", useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Back").performClick()
                onNodeWithText(text = "Settings").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText(text = "Clear location").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText(text = "Clear location").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText(
                            text = "No location selected. Choose a city to enable weather and public holidays."
                        )
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Back").performClick()
                onNodeWithText(text = "Calendar").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithTag(testTag = "calendar-grid").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag(testTag = "calendar-grid").assertIsDisplayed()
                onNodeWithTag(testTag = "calendar-weather-$today", useUnmergedTree = true).assertDoesNotExist()
                onAllNodesWithText(text = "Public holiday", useUnmergedTree = true)
                    .assertCountEquals(expectedSize = 0)
            }
        }
    }
}
