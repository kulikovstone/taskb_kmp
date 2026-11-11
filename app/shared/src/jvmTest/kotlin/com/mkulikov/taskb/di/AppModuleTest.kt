package com.mkulikov.taskb.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AppModuleTest {
    @Test
    fun `application modules create a shared lazy database and retain settings after restart`() {
        runBlocking {
            val directory = Files.createTempDirectory("taskb-di-test")
            val file = directory.resolve("taskb.db")
            var opens = 0
            try {
                repeat(times = 2) { launch ->
                    val graph = AppDependencyGraph {
                        opens++
                        AppDatabase.open(file = file)
                    }
                    val application = graph.application
                    lateinit var database: AppDatabase
                    try {
                        val settings = application.koin.get<IAppSettings>()
                        val navigator = application.koin.get<IAppNavigator>()
                        assertSame(expected = navigator, actual = application.koin.get<IAppNavigator>())
                        assertEquals(expected = 7, actual = application.koin.getAll<INavEntry>().size)
                        val locations = application.koin.get<IAppLocationRepository>()
                        val city = AppLocation(
                            name = "Paris, France",
                            latitude = 48.85,
                            longitude = 2.35,
                            timeZone = "Europe/Paris",
                            countryCode = "FR",
                            regionName = "Île-de-France",
                        )
                        val groups = application.koin.get<IGroupRepository>()
                        assertSame(expected = groups, actual = application.koin.get<IGroupRepository>())
                        assertEquals(expected = launch, actual = opens)
                        if (launch == 0) {
                            assertTrue(actual = settings.shouldShowWelcomeScreen())
                            settings.setShowWelcomeScreen(enabled = false)
                            locations.setLocation(location = city)
                        } else {
                            assertFalse(actual = settings.shouldShowWelcomeScreen())
                            assertEquals(expected = city, actual = locations.observeLocation().first())
                        }
                        assertEquals(expected = launch + 1, actual = opens)
                        assertSame(
                            expected = application.koin.get<AppDatabase>(),
                            actual = application.koin.get<AppDatabase>(),
                        )
                        database = application.koin.get()
                    } finally {
                        if (launch == 0) graph.onForgotten() else graph.onAbandoned()
                        graph.close()
                    }
                    assertFailsWith<IllegalStateException> {
                        database.settingsDao().getValue(key = "example")
                    }
                }
            } finally {
                Files.walk(directory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
    }
}
