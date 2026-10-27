package com.mkulikov.taskb.feature.settings

import androidx.room3.useReaderConnection
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import org.koin.core.error.InstanceCreationException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    private val databaseFile get() = temporaryDirectory.resolve("data/taskb.db")

    @Test
    fun `first launch creates database and shows welcome`() = runBlocking {
        assertFalse(Files.exists(databaseFile))

        withSettings(databaseFile) { assertFalse(it.hasCompletedWelcome()) }

        assertTrue(Files.isRegularFile(databaseFile))
    }

    @Test
    fun `completed welcome is remembered after restart`() = runBlocking {
        withSettings(databaseFile) {
            it.completeWelcome()
            it.completeWelcome()
        }

        withSettings(databaseFile) { assertTrue(it.hasCompletedWelcome()) }
        AppDatabase.open(databaseFile).useDatabase { database ->
            database.useReaderConnection { connection ->
                connection.usePrepared("SELECT COUNT(*) FROM application_settings") {
                    assertTrue(it.step())
                    assertEquals(1L, it.getLong(0))
                }
            }
        }
    }

    @Test
    fun `settings write failure is reported without changing existing file`() = runBlocking {
        val blockedConfig = Files.writeString(temporaryDirectory.resolve("config"), "existing")

        withSettings(blockedConfig.resolve("taskb.db")) { settings ->
            val failure = assertFailsWith<InstanceCreationException> { settings.completeWelcome() }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is IOException })
        }

        assertEquals("existing", Files.readString(blockedConfig))
    }
}
