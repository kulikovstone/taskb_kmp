package com.mkulikov.taskb.feature.calendar.screen.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkulikov.taskb.feature.weather.api.model.DailyWeather
import com.mkulikov.taskb.feature.weather.api.model.WeatherCondition
import com.mkulikov.taskb.feature.weather.api.model.WeatherForecast
import com.mkulikov.taskb.ui.icon.TaskBIcons
import com.mkulikov.taskb.ui.theme.TaskBTheme
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
internal fun CalendarWeatherSummary(weather: DailyWeather, modifier: Modifier = Modifier) {
    val colors = TaskBTheme.colors
    val description = when (weather.condition) {
        WeatherCondition.Clear -> "Clear sky"
        WeatherCondition.PartlyCloudy -> "Partly cloudy"
        WeatherCondition.Cloudy -> "Cloudy"
        WeatherCondition.Fog -> "Fog"
        WeatherCondition.Drizzle -> "Drizzle"
        WeatherCondition.Rain -> "Rain"
        WeatherCondition.Snow -> "Snow"
        WeatherCondition.Thunderstorm -> "Thunderstorm"
        WeatherCondition.Unknown -> "Unknown conditions"
    }
    val icon = when (weather.condition) {
        WeatherCondition.Clear -> TaskBIcons.Sunny
        WeatherCondition.PartlyCloudy -> TaskBIcons.PartlyCloudy
        WeatherCondition.Cloudy -> TaskBIcons.Cloudy
        WeatherCondition.Fog -> TaskBIcons.Fog
        WeatherCondition.Drizzle -> TaskBIcons.Drizzle
        WeatherCondition.Rain -> TaskBIcons.Rain
        WeatherCondition.Snow -> TaskBIcons.Snow
        WeatherCondition.Thunderstorm -> TaskBIcons.Thunderstorm
        WeatherCondition.Unknown -> TaskBIcons.UnknownWeather
    }
    val low = weather.minimumCelsius.roundToInt()
    val high = weather.maximumCelsius.roundToInt()
    BoxWithConstraints(modifier.testTag("calendar-weather-${weather.date}").semantics(mergeDescendants = true) {
        contentDescription = "$description, low $low degrees Celsius, high $high degrees Celsius"
    }) {
        val temperature = if (maxWidth < 74.dp) "$high°" else "$low°/$high°"
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Image(painterResource(icon), contentDescription = null,
                modifier = Modifier.size(20.dp).testTag("calendar-weather-icon-${weather.date}"))
            Text(temperature, style = TaskBTheme.typography.small, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun CalendarWeatherBar(weather: WeatherForecast, onSettings: () -> Unit, onRetry: () -> Unit) {
    val colors = TaskBTheme.colors
    val uriHandler = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            Row(Modifier.clickable(role = Role.Button, onClick = onSettings).testTag("calendar-weather-settings"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(TaskBIcons.Settings, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                Text(weather.location?.let { "${it.name} · °C" } ?: "Set weather city in Settings",
                    style = TaskBTheme.typography.small, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (weather.isLoading) Text("Updating weather…", style = TaskBTheme.typography.small, color = colors.muted)
        if (weather.error != null) Text(if (weather.days.isEmpty()) "Weather unavailable · Retry" else "Weather may be outdated · Retry",
            style = TaskBTheme.typography.small, color = colors.muted,
            modifier = Modifier.clickable(role = Role.Button, onClick = onRetry).semantics { contentDescription = weather.error.orEmpty() })
        if (weather.location != null) {
            Text("Weather by Open-Meteo", style = TaskBTheme.typography.small, color = colors.muted,
                modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri("https://open-meteo.com/") })
            Text("CC BY 4.0", style = TaskBTheme.typography.small, color = colors.muted,
                modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri("https://creativecommons.org/licenses/by/4.0/") })
        }
    }
}
