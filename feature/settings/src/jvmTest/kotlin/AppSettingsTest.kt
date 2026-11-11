package com.mkulikov.taskb.feature.settings

import androidx.room3.useReaderConnection
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
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
    fun `first launch creates database and shows welcome`() {
        runBlocking {
            assertFalse(actual = Files.exists(databaseFile))

            withSettings(file = databaseFile) { settings -> assertTrue(actual = settings.shouldShowWelcomeScreen()) }

            assertTrue(actual = Files.isRegularFile(databaseFile))
        }
    }

    @Test
    fun `welcome screen preference is remembered after restart`() {
        runBlocking {
            withSettings(file = databaseFile) { settings ->
                settings.setShowWelcomeScreen(enabled = false)
                settings.setShowWelcomeScreen(enabled = false)
            }

            withSettings(file = databaseFile) { settings ->
                assertFalse(actual = settings.shouldShowWelcomeScreen())
                settings.setShowWelcomeScreen(enabled = true)
            }
            withSettings(file = databaseFile) { settings -> assertTrue(actual = settings.shouldShowWelcomeScreen()) }
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
    fun `settings write failure is reported without changing existing file`() {
        runBlocking {
            val blockedConfig = Files.writeString(temporaryDirectory.resolve("config"), "existing")

            withSettings(file = blockedConfig.resolve("taskb.db")) { settings ->
                val failure = assertFailsWith<InstanceCreationException> {
                    settings.setShowWelcomeScreen(enabled = false)
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
