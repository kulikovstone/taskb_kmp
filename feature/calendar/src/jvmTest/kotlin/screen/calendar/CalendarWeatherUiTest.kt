package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherCondition
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@OptIn(ExperimentalTestApi::class)
class CalendarWeatherUiTest {
    @Test
    fun `all painterly weather resources render in both themes and preserve their colors`() {
        runSkikoComposeUiTest(
            size = Size(width = 720f, height = 640f),
        ) {
            val first = LocalDate(year = 2026, month = 11, day = 1)
            setContent {
                Row(modifier = Modifier.fillMaxSize()) {
                    listOf(false, true).forEachIndexed { themeIndex, dark ->
                        TaskBTheme(darkTheme = dark) {
                            Surface(
                                modifier = Modifier.weight(weight = 1f).fillMaxHeight(),
                                color = TaskBTheme.colors.surface,
                            ) {
                                Column(
                                    modifier = Modifier.padding(all = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(space = 20.dp),
                                ) {
                                    Text(
                                        text = if (dark) "Dark" else "Light",
                                        color = TaskBTheme.colors.heading,
                                        style = TaskBTheme.typography.title,
                                    )
                                    WeatherCondition.entries.forEachIndexed { index, condition ->
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            CalendarWeatherSummary(
                                                weather = DailyWeather(
                                                    date = first.plus(
                                                        value = themeIndex * 9 + index,
                                                        unit = DateTimeUnit.DAY,
                                                    ),
                                                    condition = condition,
                                                    minimumCelsius = -5.0,
                                                    maximumCelsius = 2.0,
                                                ),
                                                modifier = Modifier.width(width = 120.dp),
                                            )
                                            Text(
                                                text = condition.name,
                                                color = TaskBTheme.colors.text,
                                                style = TaskBTheme.typography.body,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (index in 0 until 18) {
                val date = first.plus(value = index, unit = DateTimeUnit.DAY)
                onNodeWithTag(testTag = "calendar-weather-icon-$date", useUnmergedTree = true).assertIsDisplayed()
            }
            for (index in listOf(0, 9)) {
                val date = first.plus(value = index, unit = DateTimeUnit.DAY)
                val pixels = onNodeWithTag(testTag = "calendar-weather-icon-$date", useUnmergedTree = true)
                    .captureToImage()
                    .toPixelMap()
                var goldenPixels = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val pixel = pixels[x, y]
                    if (pixel.red > pixel.blue + 0.15f && pixel.green > pixel.blue + 0.05f) goldenPixels++
                }
                assertTrue(
                    actual = goldenPixels >= 4,
                    message = "The sun should keep its golden colors in both themes",
                )
            }
            val path = Path.of("build/reports/screenshots/weather-icons-light-dark.png")
            Files.createDirectories(path.parent)
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.toFile())
        }
    }

    @Test
    fun `painterly weather icons stay inside day headers in a narrow dark calendar`() {
        runSkikoComposeUiTest(
            size = Size(width = 900f, height = 720f),
        ) {
            val date = LocalDate(year = 2026, month = 10, day = 29)
            val city = WeatherLocation(
                name = "Yekaterinburg, Russia",
                latitude = 56.84,
                longitude = 60.61,
                timeZone = "Asia/Yekaterinburg",
            )
            val days = List(size = 10) { index ->
                DailyWeather(
                    date = date.plus(value = index, unit = DateTimeUnit.DAY),
                    condition = WeatherCondition.entries[index % WeatherCondition.entries.size],
                    minimumCelsius = -5.0 + index,
                    maximumCelsius = 2.0 + index,
                )
            }
            val state = calendarPreviewState()
                .copy(
                    now = Instant.parse(input = "2026-10-29T09:00:00Z"),
                    weather = WeatherForecast(location = city, days = days),
                )
            setContent {
                TaskBTheme(darkTheme = true) {
                    CalendarScreen(state = state, onIntent = {}, onBack = {}, onOpenTask = {}, onCreateTask = {})
                }
            }
            for (day in days.filter { weather -> weather.date in state.days }) {
                val icon = onNodeWithTag(testTag = "calendar-weather-icon-${day.date}", useUnmergedTree = true)
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot
                val cell = onNodeWithTag(testTag = "calendar-day-${day.date}", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                val number = onNodeWithTag(testTag = "calendar-day-number-${day.date}", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                assertEquals(expected = 4f, actual = icon.left - number.right, absoluteTolerance = 0.5f)
                assertTrue(actual = icon.right <= cell.right)
                assertTrue(actual = icon.bottom <= number.bottom)
            }
            val path = Path.of("build/reports/screenshots/calendar-weather-dark-narrow.png")
            Files.createDirectories(path.parent)
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.toFile())
        }
    }

    @Test
    fun `weather follows day number across month boundaries and does not replace day selection`() {
        runSkikoComposeUiTest(size = Size(width = 1440f, height = 900f)) {
            val date = LocalDate(year = 2026, month = 10, day = 29)
            val city = WeatherLocation(
                name = "Yekaterinburg, Russia",
                latitude = 56.84,
                longitude = 60.61,
                timeZone = "Asia/Yekaterinburg",
            )
            val days = List(size = 10) { index ->
                DailyWeather(
                    date = date.plus(value = index, unit = DateTimeUnit.DAY),
                    condition = WeatherCondition.entries[index % (WeatherCondition.entries.size - 1)],
                    minimumCelsius = -5.0 + index,
                    maximumCelsius = 2.0 + index,
                )
            }
            val state = calendarPreviewState()
                .copy(
                    now = Instant.parse(input = "2026-10-29T09:00:00Z"),
                    weather = WeatherForecast(location = city, days = days),
                )
            val intents = mutableListOf<CalendarIntent>()
            setContent {
                TaskBTheme(darkTheme = false) {
                    CalendarScreen(
                        state = state,
                        onIntent = intents::add,
                        onBack = {},
                        onOpenTask = {},
                        onCreateTask = {},
                    )
                }
            }
            val number = onNodeWithTag(testTag = "calendar-day-number-$date", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val weather = onNodeWithTag(testTag = "calendar-weather-$date", useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            assertEquals(expected = 4f, actual = weather.left - number.right, absoluteTolerance = 0.5f)
            assertEquals(expected = number.center.y, actual = weather.center.y, absoluteTolerance = 0.5f)
            onNodeWithTag(testTag = "calendar-weather-2026-11-01", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag(testTag = "calendar-weather-2026-10-28", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithTag(testTag = "calendar-weather-$date", useUnmergedTree = true)
                .assertContentDescriptionEquals("Clear sky, low -5 degrees Celsius, high 2 degrees Celsius")
            onNodeWithTag(testTag = "calendar-day-number-$date", useUnmergedTree = true).performClick()
            runOnIdle { assertEquals(expected = CalendarIntent.SelectDay(date = date), actual = intents.last()) }
            val path = Path.of("build/reports/screenshots/calendar-weather.png")
            Files.createDirectories(path.parent)
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.toFile())
        }
    }
}
