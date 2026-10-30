package com.mkulikov.taskb.core.database

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class AppDatabaseTest {
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

    @Test
    fun `schema is initialized once and data survives reopening`() = runBlocking {
        val file = temporaryDirectory.resolve("taskb.db")
        AppDatabase.open(file).useDatabase { database ->
            database.settingsDao().upsert(ApplicationSetting("example", "saved"))
        }

        AppDatabase.open(file).useDatabase { database ->
            assertEquals("saved", database.settingsDao().getValue("example"))
            assertEquals(4L, database.queryLong("PRAGMA user_version"))
        }
    }

    @Test
    fun `existing version two database retains groups and settings`() = runBlocking {
        val file = temporaryDirectory.resolve("existing.db")
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.execSQL("CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            connection.execSQL(
                "CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)"
            )
            connection.execSQL("CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
            connection.execSQL("INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
            connection.execSQL("INSERT INTO `groups` VALUES (7, 'Work', 123)")
            connection.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL("INSERT INTO room_master_table VALUES (42, '1f6fcf3a57abd711d5a91fe555af4949')")
            connection.execSQL("PRAGMA user_version = 2")
        }

        AppDatabase.open(file).useDatabase { database ->
            assertEquals(GroupEntity(7, "Work", 123), database.groupDao().getById(7))
            assertEquals("true", database.settingsDao().getValue("welcomeCompleted"))
            assertEquals(4L, database.queryLong("PRAGMA user_version"))
            assertEquals(0L, database.queryLong("SELECT COUNT(*) FROM tasks"))
            assertEquals(0, database.groupDao().getWithTaskCounts(7)?.planned)
        }
    }

    @Test
    fun `version three migration retains tasks relationships and completion with stable timestamps`() = runBlocking {
        val file = temporaryDirectory.resolve("version-three.db")
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.execSQL("CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            connection.execSQL("CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)")
            connection.execSQL("CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
            connection.execSQL("""
                CREATE TABLE tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                    dueDate INTEGER, groupId INTEGER, completed INTEGER NOT NULL,
                    FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            connection.execSQL("CREATE INDEX index_tasks_groupId ON tasks (groupId)")
            connection.execSQL("INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
            connection.execSQL("INSERT INTO `groups` VALUES (7, 'Work', 123), (8, 'Empty', 456)")
            connection.execSQL("INSERT INTO tasks VALUES (10, 'Planned', 20400, 7, 0), (20, 'Done', NULL, 7, 1), (30, 'Ungrouped', NULL, NULL, 0)")
            connection.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL("INSERT INTO room_master_table VALUES (42, '121e080f0db91c7c40cea2361ca47274')")
            connection.execSQL("PRAGMA user_version = 3")
        }

        val beforeMigration = Clock.System.now().toEpochMilliseconds()
        var migratedAt = 0L
        AppDatabase.open(file).useDatabase { database ->
            val planned = assertNotNull(database.taskDao().getById(10))
            val completed = assertNotNull(database.taskDao().getById(20))
            val ungrouped = assertNotNull(database.taskDao().getById(30))
            assertTrue(planned.createdAt in beforeMigration..Clock.System.now().toEpochMilliseconds())
            migratedAt = planned.createdAt
            assertEquals("Planned", planned.name)
            assertEquals(20400L, planned.dueDate)
            assertEquals(7, planned.groupId)
            assertNull(planned.completedAt)
            assertEquals("Done", completed.name)
            assertEquals(migratedAt, completed.createdAt)
            assertEquals(migratedAt, completed.completedAt)
            assertNull(completed.dueDate)
            assertEquals(7, completed.groupId)
            assertEquals("Ungrouped", ungrouped.name)
            assertNull(ungrouped.groupId)
            assertNull(ungrouped.completedAt)
            assertEquals(migratedAt, ungrouped.createdAt)
            val counts = assertNotNull(database.groupDao().getWithTaskCounts(7))
            assertEquals(1, counts.planned)
            assertEquals(1, counts.completed)
            val empty = assertNotNull(database.groupDao().getWithTaskCounts(8))
            assertEquals(0, empty.planned)
            assertEquals(0, empty.completed)
            assertEquals(GroupEntity(7, "Work", 123), database.groupDao().getById(7))
            assertEquals("true", database.settingsDao().getValue("welcomeCompleted"))
            assertEquals(4L, database.queryLong("PRAGMA user_version"))
        }

        AppDatabase.open(file).useDatabase { database ->
            assertEquals(migratedAt, database.taskDao().getById(10)?.createdAt)
            assertEquals(migratedAt, database.taskDao().getById(20)?.completedAt)
            database.groupDao().deleteById(7)
            assertNull(assertNotNull(database.taskDao().getById(10)).groupId)
            assertNull(assertNotNull(database.taskDao().getById(20)).groupId)
            assertEquals(3L, database.queryLong("SELECT COUNT(*) FROM tasks"))
        }
    }

    @Test
    fun `foreign keys protect relationships on every new connection`() = runBlocking {
        val file = temporaryDirectory.resolve("taskb.db")
        AppDatabase.open(file).useDatabase { database ->
            database.useWriterConnection { connection ->
                connection.usePrepared("CREATE TABLE test_parents (id INTEGER NOT NULL PRIMARY KEY)") { it.step() }
                connection.usePrepared(
                    "CREATE TABLE test_children (id INTEGER NOT NULL PRIMARY KEY, " +
                        "parent_id INTEGER NOT NULL REFERENCES test_parents(id) ON DELETE RESTRICT)"
                ) { it.step() }
            }
        }

        repeat(2) {
            AppDatabase.open(file).useDatabase { database ->
                assertEquals(1L, database.queryLong("PRAGMA foreign_keys"))
                assertFailsWith<SQLiteException> {
                    database.useWriterConnection { connection ->
                        connection.usePrepared("INSERT INTO test_children VALUES (1, 999)") { it.step() }
                    }
                }
                assertEquals(0L, database.queryLong("SELECT COUNT(*) FROM test_children"))
            }
        }
    }

    @Test
    fun `failed transaction leaves no partial changes`() = runBlocking {
        AppDatabase.open(temporaryDirectory.resolve("taskb.db")).useDatabase { database ->
            assertFailsWith<IllegalStateException> {
                database.withWriteTransaction {
                    database.settingsDao().upsert(ApplicationSetting("incomplete", "must be rolled back"))
                    error("Simulated failure")
                }
            }

            assertEquals(0L, database.queryLong("SELECT COUNT(*) FROM application_settings"))
        }
    }

    @Test
    fun `newer database schema is rejected without changing existing data`() = runBlocking {
        val file = temporaryDirectory.resolve("newer.db")
        val driver = BundledSQLiteDriver()
        driver.open(file.toString()).use { connection ->
            connection.execSQL("CREATE TABLE future_data (value TEXT NOT NULL)")
            connection.execSQL("INSERT INTO future_data VALUES ('preserved')")
            connection.execSQL("PRAGMA user_version = 5")
        }

        AppDatabase.open(file).useDatabase { database ->
            assertFailsWith<IllegalStateException> {
                database.settingsDao().getValue("example")
            }
        }

        driver.open(file.toString()).use { connection ->
            connection.prepare("SELECT value FROM future_data").use {
                assertTrue(it.step())
                assertEquals("preserved", it.getText(0))
            }
            connection.prepare("PRAGMA user_version").use {
                assertTrue(it.step())
                assertEquals(5L, it.getLong(0))
            }
        }
    }

    @Test
    fun `version one database is recreated without a data migration`() = runBlocking {
        val file = temporaryDirectory.resolve("old.db")
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.execSQL("CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            connection.execSQL("INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
            connection.execSQL("PRAGMA user_version = 1")
        }
        AppDatabase.open(file).useDatabase { database ->
            assertNull(database.settingsDao().getValue("welcomeCompleted"))
            assertEquals(4L, database.queryLong("PRAGMA user_version"))
            assertEquals(0L, database.queryLong("SELECT COUNT(*) FROM `groups`"))
        }
    }

    private suspend fun AppDatabase.queryLong(sql: String): Long = useReaderConnection { connection ->
        connection.usePrepared(sql) {
            check(it.step()) { "Query returned no rows: $sql" }
            it.getLong(0)
        }
    }
}
