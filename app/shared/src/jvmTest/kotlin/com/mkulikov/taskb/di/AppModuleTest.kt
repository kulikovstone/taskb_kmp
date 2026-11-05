package com.mkulikov.taskb.di

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.core.navigation.IAppNavigator
import com.mkulikov.taskb.core.navigation.INavEntry
import com.mkulikov.taskb.feature.group.api.repository.IGroupRepository
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppModuleTest {
    @Test
    fun `application modules create a shared lazy database and retain settings after restart`() = runBlocking {
        val directory = Files.createTempDirectory("taskb-di-test")
        val file = directory.resolve("taskb.db")
        var opens = 0
        try {
            repeat(2) { launch ->
                val graph = AppDependencyGraph {
                    opens++
                    AppDatabase.open(file)
                }
                val application = graph.application
                lateinit var database: AppDatabase
                try {
                    val settings = application.koin.get<IAppSettings>()
                    val navigator = application.koin.get<IAppNavigator>()
                    assertSame(navigator, application.koin.get<IAppNavigator>())
                    assertEquals(6, application.koin.getAll<INavEntry>().size)
                    val groups = application.koin.get<IGroupRepository>()
                    assertSame(groups, application.koin.get<IGroupRepository>())
                    assertEquals(launch, opens)
                    if (launch == 0) {
                        assertFalse(settings.hasCompletedWelcome())
                        settings.completeWelcome()
                    } else {
                        assertTrue(settings.hasCompletedWelcome())
                    }
                    assertEquals(launch + 1, opens)
                    assertSame(application.koin.get<AppDatabase>(), application.koin.get<AppDatabase>())
                    database = application.koin.get()
                } finally {
                    if (launch == 0) graph.onForgotten() else graph.onAbandoned()
                    graph.close()
                }
                assertFailsWith<IllegalStateException> {
                    database.settingsDao().getValue("example")
                }
            }
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
