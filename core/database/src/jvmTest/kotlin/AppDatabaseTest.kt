package com.mkulikov.taskb.core.database

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.core.database.migration.Migration4To5
import com.mkulikov.taskb.core.database.migration.Migration5To6
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
            assertEquals(10L, database.queryLong("PRAGMA user_version"))
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
            assertEquals(10L, database.queryLong("PRAGMA user_version"))
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
            assertEquals(10L, database.queryLong("PRAGMA user_version"))
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
    fun `version four migration preserves one-off tasks timestamps and initializes empty recurrence tables`() = runBlocking {
        val file = temporaryDirectory.resolve("version-four.db")
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.execSQL("CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            connection.execSQL("CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)")
            connection.execSQL("CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
            connection.execSQL("""
                CREATE TABLE tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                    dueDate INTEGER, groupId INTEGER, createdAt INTEGER NOT NULL, completedAt INTEGER,
                    FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            connection.execSQL("CREATE INDEX index_tasks_groupId ON tasks (groupId)")
            connection.execSQL("INSERT INTO `groups` VALUES (7, 'Work', 123)")
            connection.execSQL("INSERT INTO tasks VALUES (10, 'Existing', 20400, 7, 1000, 2000), (20, 'Undated', NULL, NULL, 3000, NULL)")
            connection.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL("INSERT INTO room_master_table VALUES (42, '24cb2fa7c171c7cfc361b6fcd04b881f')")
            connection.execSQL("PRAGMA user_version = 4")
        }
        AppDatabase.open(file).useDatabase { database ->
            val existing = assertNotNull(database.taskDao().getRecord(10))
            assertEquals("Existing", existing.task.name)
            assertEquals(20400L, existing.task.dueDate)
            assertEquals(7, existing.task.groupId)
            assertEquals(1000L, existing.task.createdAt)
            assertEquals(2000L, existing.task.completedAt)
            assertTrue(!existing.task.recurring)
            assertTrue(existing.schedules.isEmpty())
            assertTrue(existing.occurrences.isEmpty())
            assertNull(database.taskDao().getById(20)?.dueDate)
            assertEquals(10L, database.queryLong("PRAGMA user_version"))
        }
    }

    @Test
    fun `version five migration adds empty descriptions without changing tasks schedules or exceptions`() = runBlocking {
        val file = temporaryDirectory.resolve("version-five.db")
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.execSQL("CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            connection.execSQL("CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)")
            connection.execSQL("CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
            connection.execSQL("""
                CREATE TABLE tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                    dueDate INTEGER, groupId INTEGER, createdAt INTEGER NOT NULL, completedAt INTEGER,
                    FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            connection.execSQL("CREATE INDEX index_tasks_groupId ON tasks (groupId)")
            Migration4To5().migrate(connection)
            connection.execSQL("INSERT INTO `groups` VALUES (7, 'Health', 123)")
            connection.execSQL("INSERT INTO tasks VALUES (10, 'Workout', 20760, 7, 1000, NULL, 1), (20, 'One-off', NULL, NULL, 3000, 4000, 0)")
            connection.execSQL("INSERT INTO task_schedules VALUES (30, 10, 20760, 'Daily', 1, 0, 1, 1, NULL, NULL, NULL)")
            connection.execSQL("INSERT INTO task_occurrences VALUES (30, 20760, 10, NULL, 2000, 0), (30, 20761, 10, 20762, NULL, 0)")
            connection.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL("INSERT INTO room_master_table VALUES (42, '57598c43e645afd970e5aafdaff4c85b')")
            connection.execSQL("PRAGMA user_version = 5")
        }
        AppDatabase.open(file).useDatabase { database ->
            val recurring = assertNotNull(database.taskDao().getRecord(10))
            assertEquals("", recurring.task.description)
            assertEquals("Workout", recurring.task.name)
            assertEquals(7, recurring.task.groupId)
            assertEquals(20760L, recurring.task.dueDate)
            assertEquals(1000L, recurring.task.createdAt)
            assertTrue(recurring.task.recurring)
            assertEquals(30L, recurring.schedules.single().id)
            assertEquals(20760L, recurring.schedules.single().startDate)
            assertEquals(2, recurring.occurrences.size)
            assertEquals(2000L, recurring.occurrences.single { it.scheduledDate == 20760L }.completedAt)
            assertEquals(20762L, recurring.occurrences.single { it.scheduledDate == 20761L }.movedDate)
            val oneOff = assertNotNull(database.taskDao().getById(20))
            assertEquals("", oneOff.description)
            assertEquals(4000L, oneOff.completedAt)
            assertEquals(10L, database.queryLong("PRAGMA user_version"))
            database.taskDao().update(recurring.task.copy(description = "First line\nSecond line"))
        }
        AppDatabase.open(file).useDatabase { database ->
            assertEquals("First line\nSecond line", database.taskDao().getById(10)?.description)
            assertEquals("", database.taskDao().getById(20)?.description)
        }
    }

    @Test
    fun `version six migration retains descriptions recurrence and completion and leaves timing unset`() = runBlocking {
        val file = temporaryDirectory.resolve("version-six.db")
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.execSQL("CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            connection.execSQL("CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)")
            connection.execSQL("CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
            connection.execSQL("""
                CREATE TABLE tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                    dueDate INTEGER, groupId INTEGER, createdAt INTEGER NOT NULL, completedAt INTEGER,
                    FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            connection.execSQL("CREATE INDEX index_tasks_groupId ON tasks (groupId)")
            Migration4To5().migrate(connection)
            Migration5To6().migrate(connection)
            connection.execSQL("INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
            connection.execSQL("INSERT INTO `groups` VALUES (7, 'Work', 123)")
            connection.execSQL("INSERT INTO tasks VALUES (10, 'Routine', 20760, 7, 1000, NULL, 1, 'Existing description'), (20, 'Done', NULL, NULL, 3000, 4000, 0, 'Saved note')")
            connection.execSQL("INSERT INTO task_schedules VALUES (30, 10, 20760, 'Daily', 1, 0, 1, 1, NULL, 3, NULL)")
            connection.execSQL("INSERT INTO task_occurrences VALUES (30, 20760, 10, NULL, 2000, 0), (30, 20761, 10, 20762, NULL, 0)")
            connection.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL("INSERT INTO room_master_table VALUES (42, 'be1a15228ccb99640ff5ef123777e833')")
            connection.execSQL("PRAGMA user_version = 6")
        }
        AppDatabase.open(file).useDatabase { database ->
            assertEquals(10L, database.queryLong("PRAGMA user_version"))
            val record = assertNotNull(database.taskDao().getRecord(10))
            assertEquals("Existing description", record.task.description)
            assertEquals(7, record.task.groupId)
            assertEquals(1000L, record.task.createdAt)
            assertEquals(3, record.schedules.single().occurrenceCount)
            assertEquals(2, record.occurrences.size)
            assertEquals(2000L, record.occurrences.single { it.scheduledDate == 20760L }.completedAt)
            assertEquals(20762L, record.occurrences.single { it.scheduledDate == 20761L }.movedDate)
            assertEquals("Saved note", database.taskDao().getById(20)?.description)
            assertEquals(4000L, database.taskDao().getById(20)?.completedAt)
            assertEquals("true", database.settingsDao().getValue("welcomeCompleted"))
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
            connection.execSQL("PRAGMA user_version = 11")
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
                assertEquals(11L, it.getLong(0))
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
            assertEquals(10L, database.queryLong("PRAGMA user_version"))
            assertEquals(0L, database.queryLong("SELECT COUNT(*) FROM `groups`"))
        }
    }

    @Test
    fun `version seven upgrade creates event storage and transfers timed tasks through the complete migration chain`() = runBlocking {
        val file = temporaryDirectory.resolve("version-seven.db")
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.execSQL("CREATE TABLE IF NOT EXISTS `application_settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, `color` INTEGER NOT NULL)")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_groups_name` ON `groups` (`name`)")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dueDate` INTEGER, `groupId` INTEGER, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, `recurring` INTEGER NOT NULL DEFAULT 0, `description` TEXT NOT NULL DEFAULT '', `startTimeMinutes` INTEGER, `durationMinutes` INTEGER, FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_groupId` ON `tasks` (`groupId`)")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `task_schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `frequency` TEXT NOT NULL, `interval` INTEGER NOT NULL, `weekdays` INTEGER NOT NULL, `dayOfMonth` INTEGER NOT NULL, `month` INTEGER NOT NULL, `endDate` INTEGER, `occurrenceCount` INTEGER, `closedBefore` INTEGER, `startTimeMinutes` INTEGER, `durationMinutes` INTEGER, FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_task_schedules_taskId` ON `task_schedules` (`taskId`)")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `task_occurrences` (`scheduleId` INTEGER NOT NULL, `scheduledDate` INTEGER NOT NULL, `taskId` INTEGER NOT NULL, `movedDate` INTEGER, `completedAt` INTEGER, `skipped` INTEGER NOT NULL, `overridesTiming` INTEGER NOT NULL DEFAULT 0, `startTimeMinutes` INTEGER, `durationMinutes` INTEGER, PRIMARY KEY(`scheduleId`, `scheduledDate`), FOREIGN KEY(`scheduleId`) REFERENCES `task_schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_task_occurrences_taskId` ON `task_occurrences` (`taskId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_task_occurrences_scheduleId` ON `task_occurrences` (`scheduleId`)")
            connection.execSQL("INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
            connection.execSQL("INSERT INTO groups VALUES (7, 'Work', 123)")
            connection.execSQL("INSERT INTO tasks VALUES (10, 'Meeting', 20760, 7, 1000, 2000, 1, 'Notes', 540, 90)")
            connection.execSQL("INSERT INTO task_schedules VALUES (30, 10, 20760, 'Daily', 1, 0, 1, 1, NULL, 3, NULL, 540, 90)")
            connection.execSQL("INSERT INTO task_occurrences VALUES (30, 20760, 10, 20761, 2000, 0, 1, 600, 30)")
            connection.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL("INSERT INTO room_master_table VALUES (42, 'ebf3640ce9b83ba4ef80e0a6f0323469')")
            connection.execSQL("PRAGMA user_version = 7")
        }
        AppDatabase.open(file).useDatabase { database ->
            assertEquals(10L, database.queryLong("PRAGMA user_version"))
            assertNull(database.taskDao().getRecord(10))
            val migrated = database.eventDao().observeRecords().first().single()
            assertEquals("Meeting", migrated.event.name)
            assertEquals("Notes", migrated.event.description)
            assertEquals(1000L, migrated.event.createdAt)
            assertEquals(540, migrated.schedules.single().startTimeMinutes)
            assertEquals(600, migrated.occurrences.single().startTimeMinutes)
            assertEquals(20761L, migrated.occurrences.single().movedDate)
            val id = database.eventDao().insert(com.mkulikov.taskb.feature.event.api.database.EventEntity(name = "Independent event", groupId = 7)).toInt()
            assertEquals("Independent event", database.eventDao().getById(id)?.name)
            assertEquals(0L, database.queryLong("SELECT COUNT(*) FROM tasks"))
            assertEquals("true", database.settingsDao().getValue("welcomeCompleted"))
            database.groupDao().deleteById(7)
            assertNull(database.eventDao().getById(id)?.groupId)
            assertNull(database.eventDao().getById(migrated.event.id)?.groupId)
        }
    }

    private suspend fun AppDatabase.queryLong(sql: String): Long = useReaderConnection { connection ->
        connection.usePrepared(sql) {
            check(it.step()) { "Query returned no rows: $sql" }
            it.getLong(0)
        }
    }
}
