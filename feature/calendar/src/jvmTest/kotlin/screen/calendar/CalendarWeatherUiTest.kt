package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherCondition
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.feature.weather.api.model.WeatherLocation
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CalendarWeatherUiTest {
    @Test
    fun `all painterly weather resources render in both themes and preserve their colors`() = runSkikoComposeUiTest(size = Size(720f, 640f)) {
        val first = LocalDate(2026, 11, 1)
        setContent {
            Row(Modifier.fillMaxSize()) {
                listOf(false, true).forEachIndexed { themeIndex, dark ->
                    TaskBTheme(dark) {
                        Surface(Modifier.weight(1f).fillMaxHeight(), color = TaskBTheme.colors.surface) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text(if (dark) "Dark" else "Light", color = TaskBTheme.colors.heading, style = TaskBTheme.typography.title)
                                WeatherCondition.entries.forEachIndexed { index, condition ->
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        CalendarWeatherSummary(DailyWeather(first.plus(themeIndex * 9 + index, DateTimeUnit.DAY),
                                            condition, -5.0, 2.0), Modifier.width(120.dp))
                                        Text(condition.name, color = TaskBTheme.colors.text, style = TaskBTheme.typography.body)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (index in 0 until 18) {
            val date = first.plus(index, DateTimeUnit.DAY)
            onNodeWithTag("calendar-weather-icon-$date", useUnmergedTree = true).assertIsDisplayed()
        }
        for (index in listOf(0, 9)) {
            val date = first.plus(index, DateTimeUnit.DAY)
            val pixels = onNodeWithTag("calendar-weather-icon-$date", useUnmergedTree = true).captureToImage().toPixelMap()
            var goldenPixels = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (pixel.red > pixel.blue + 0.15f && pixel.green > pixel.blue + 0.05f) goldenPixels++
            }
            assertTrue(goldenPixels >= 4, "The sun should keep its golden colors in both themes")
        }
        val path = Path.of("build/reports/screenshots/weather-icons-light-dark.png")
        Files.createDirectories(path.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.toFile())
    }

    @Test
    fun `painterly weather icons stay inside day headers in a narrow dark calendar`() = runSkikoComposeUiTest(size = Size(900f, 720f)) {
        val date = LocalDate(2026, 10, 29)
        val city = WeatherLocation("Yekaterinburg, Russia", 56.84, 60.61, "Asia/Yekaterinburg")
        val days = List(10) { index -> DailyWeather(date.plus(index, DateTimeUnit.DAY),
            WeatherCondition.entries[index % WeatherCondition.entries.size], -5.0 + index, 2.0 + index) }
        val state = calendarPreviewState().copy(now = Instant.parse("2026-10-29T09:00:00Z"), weather = WeatherForecast(city, days))
        setContent { TaskBTheme(true) { CalendarScreen(state, {}, {}, {}, {}) } }
        for (day in days.filter { it.date in state.days }) {
            val icon = onNodeWithTag("calendar-weather-icon-${day.date}", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val cell = onNodeWithTag("calendar-day-${day.date}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val number = onNodeWithTag("calendar-day-number-${day.date}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(4f, icon.left - number.right, 0.5f)
            assertTrue(icon.right <= cell.right)
            assertTrue(icon.bottom <= number.bottom)
        }
        val path = Path.of("build/reports/screenshots/calendar-weather-dark-narrow.png")
        Files.createDirectories(path.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.toFile())
    }

    @Test
    fun `weather follows day number across month boundaries and does not replace day selection`() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val date = LocalDate(2026, 10, 29)
        val city = WeatherLocation("Yekaterinburg, Russia", 56.84, 60.61, "Asia/Yekaterinburg")
        val days = List(10) { index -> DailyWeather(date.plus(index, DateTimeUnit.DAY),
            WeatherCondition.entries[index % (WeatherCondition.entries.size - 1)], -5.0 + index, 2.0 + index) }
        val state = calendarPreviewState().copy(now = Instant.parse("2026-10-29T09:00:00Z"), weather = WeatherForecast(city, days))
        val intents = mutableListOf<CalendarIntent>()
        setContent { TaskBTheme(false) { CalendarScreen(state, intents::add, {}, {}, {}) } }
        val number = onNodeWithTag("calendar-day-number-$date", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val weather = onNodeWithTag("calendar-weather-$date", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(4f, weather.left - number.right, 0.5f)
        assertEquals(number.center.y, weather.center.y, 0.5f)
        onNodeWithTag("calendar-weather-2026-11-01", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("calendar-weather-2026-10-28", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("calendar-weather-$date", useUnmergedTree = true).assertContentDescriptionEquals("Clear sky, low -5 degrees Celsius, high 2 degrees Celsius")
        onNodeWithTag("calendar-day-number-$date", useUnmergedTree = true).performClick()
        runOnIdle { assertEquals(CalendarIntent.SelectDay(date), intents.last()) }
        val path = Path.of("build/reports/screenshots/calendar-weather.png")
        Files.createDirectories(path.parent)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.toFile())
    }
}
