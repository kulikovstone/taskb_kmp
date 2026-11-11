package com.mkulikov.taskb.feature.settings.screen.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import com.mkulikov.taskb.feature.settings.TestAppSettings
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import com.mkulikov.taskb.ui.theme.TaskBTheme
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalTestApi::class)
class SettingsScreenUiTest {
    @Test
    fun `city search selects a city and can clear the shared location in a compact window`() {
        runSkikoComposeUiTest(
            size = Size(width = 420f, height = 720f),
        ) {
            val city = AppLocation(
                name = "Paris, Île-de-France, France",
                latitude = 48.85,
                longitude = 2.35,
                timeZone = "Europe/Paris",
                countryCode = "FR",
                regionName = "Île-de-France",
            )
            val location = MutableStateFlow<AppLocation?>(value = null)
            val repository =
                object : IAppLocationRepository {
                    override fun observeLocation() = location

                    override suspend fun searchLocations(query: String): List<AppLocation> {
                        return if (query == "Paris") listOf(element = city) else emptyList()
                    }

                    override suspend fun setLocation(value: AppLocation?) {
                        location.value = value
                    }
                }
            SettingsStore(locations = repository, settings = TestAppSettings()).use { store ->
                setContent {
                    val state = store.state.collectAsState().value
                    TaskBTheme(darkTheme = false) {
                        SettingsScreen(state = state, onIntent = store::accept, onBack = {})
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText(
                            text = "No location selected. Choose a city to enable weather and public holidays."
                        )
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                onNodeWithContentDescription(label = "Search city").performTextInput(text = "Paris")
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText(text = city.name).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText(text = city.name).performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText(text = "Clear location").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag(testTag = "settings-app-location").assertTextEquals(city.name)
                onNodeWithContentDescription(label = "Search city")
                    .assert(
                        matcher = SemanticsMatcher.expectValue(
                            key = SemanticsProperties.EditableText,
                            expectedValue = AnnotatedString(text = ""),
                        )
                    )
                val path = Path.of("build/reports/screenshots/settings-location-compact.png")
                Files.createDirectories(path.parent)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", path.toFile())
                onNodeWithText(text = "Clear location").performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText(
                            text = "No location selected. Choose a city to enable weather and public holidays."
                        )
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            }
        }
    }
}
