package com.mkulikov.taskb.feature.settings

import androidx.room3.useReaderConnection
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.koin.core.error.InstanceCreationException

class AppSettingsTest {
    private lateinit var temporaryDirectory: Path

    @BeforeTest
    fun createTemporaryDirectory() {
        temporaryDirectory = java.nio.file.Files.createTempDirectory("taskb-database-test")
    }

    @AfterTest
    fun removeTemporaryDirectory() {
        java.nio.file.Files.walk(temporaryDirectory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(java.nio.file.Files::deleteIfExists)
        }
    }

    private val databaseFile
        get() = temporaryDirectory.resolve("data/taskb.db")

    @Test
    fun `closing before confirming welcome shows it again on the next launch`() {
        runBlocking {
            withSettings(file = databaseFile) { settings ->
                assertTrue(actual = settings.shouldShowWelcomeScreen(version = "1.9"))
            }
            withSettings(file = databaseFile) { settings ->
                assertTrue(actual = settings.shouldShowWelcomeScreen(version = "1.9"))
            }
        }
    }

    @Test
    fun `welcome is shown once per upgrade across restarts and downgrades`() {
        runBlocking {
            withSettings(file = databaseFile) { settings ->
                assertTrue(actual = settings.markWelcomeShownIfNewer(version = "1.9"))
                assertFalse(actual = settings.markWelcomeShownIfNewer(version = "1.9"))
            }

            withSettings(file = databaseFile) { settings ->
                assertFalse(actual = settings.shouldShowWelcomeScreen(version = "1.9"))
                assertTrue(actual = settings.shouldShowWelcomeScreen(version = "1.10"))
                assertTrue(actual = settings.markWelcomeShownIfNewer(version = "1.10"))
                assertFalse(actual = settings.shouldShowWelcomeScreen(version = "1.8"))
                assertFalse(actual = settings.markWelcomeShownIfNewer(version = "1.8"))
                assertFalse(actual = settings.markWelcomeShownIfNewer(version = "1.10"))
                assertTrue(actual = settings.markWelcomeShownIfNewer(version = "2.0"))
            }
            withSettings(file = databaseFile) { settings ->
                assertFalse(actual = settings.shouldShowWelcomeScreen(version = "2.0"))
            }
            AppDatabase.open(file = databaseFile).useDatabase { database ->
                database.useReaderConnection { connection ->
                    connection.usePrepared(sql = "SELECT COUNT(*) FROM application_settings") { statement ->
                        assertTrue(actual = statement.step())
                        assertEquals(expected = 1L, actual = statement.getLong(index = 0))
                    }
                }
            }
        }
    }

    @Test
    fun `legacy startup markers do not acknowledge welcome and concurrent confirmations are idempotent`() {
        runBlocking {
            AppDatabase.open(file = databaseFile).useDatabase { database ->
                database.settingsDao().upsert(setting = ApplicationSetting(key = "showWelcomeScreen", value = "false"))
                database.settingsDao().upsert(setting = ApplicationSetting(key = "lastWelcomeVersion", value = "1.10"))
            }
            withSettings(file = databaseFile) { settings ->
                assertTrue(actual = settings.shouldShowWelcomeScreen(version = "1.10"))
                val confirmations = List(size = 4) { async { settings.markWelcomeShownIfNewer(version = "1.10") } }
                assertEquals(expected = 1, actual = confirmations.awaitAll().count { recorded -> recorded })
                assertFalse(actual = settings.shouldShowWelcomeScreen(version = "1.10"))
            }
        }
    }

    @Test
    fun `settings write failure is reported without changing existing file`() {
        runBlocking {
            val blockedConfig = Files.writeString(temporaryDirectory.resolve("config"), "existing")

            withSettings(file = blockedConfig.resolve("taskb.db")) { settings ->
                val failure = assertFailsWith<InstanceCreationException> {
                    settings.markWelcomeShownIfNewer(version = "1.9")
                }
                assertTrue(
                    actual = generateSequence<Throwable>(seed = failure) { exception ->
                        exception.cause
                    }.any { exception -> exception is IOException },
                )
            }

            assertEquals(expected = "existing", actual = Files.readString(blockedConfig))
        }
    }
}
