package com.mkulikov.taskb.feature.settings.screen.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class SettingsScreenUiTest {
    @Test
    fun `city search selects a city and can clear the shared location in a compact window`() = runSkikoComposeUiTest(size = Size(420f, 720f)) {
        val city = AppLocation("Paris, Île-de-France, France", 48.85, 2.35, "Europe/Paris", "FR", "Île-de-France")
        val location = MutableStateFlow<AppLocation?>(null)
        val repository = object : IAppLocationRepository {
            override fun observeLocation() = location
            override suspend fun searchLocations(query: String) = if (query == "Paris") listOf(city) else emptyList()
            override suspend fun setLocation(value: AppLocation?) { location.value = value }
        }
        SettingsStore(repository).use { store ->
            setContent {
                val state = store.state.collectAsState().value
                TaskBTheme(false) { SettingsScreen(state, store::accept, {}) }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("No location selected. Choose a city to enable weather and public holidays.").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("Search city").performTextInput("Paris")
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(city.name).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText(city.name).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Clear location").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("settings-app-location").assertTextEquals(city.name)
            onNodeWithContentDescription("Search city").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            val path = Path.of("build/reports/screenshots/settings-location-compact.png")
            Files.createDirectories(path.parent)
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.toFile())
            onNodeWithText("Clear location").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("No location selected. Choose a city to enable weather and public holidays.").fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
