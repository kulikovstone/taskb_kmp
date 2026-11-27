package com.mkulikov.taskb.core.database

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mkulikov.taskb.core.database.migration.Migration4To5
import com.mkulikov.taskb.core.database.migration.Migration5To6
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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
    fun `schema is initialized once and data survives reopening`() {
        runBlocking {
            val file = temporaryDirectory.resolve("taskb.db")
            AppDatabase.open(file = file).useDatabase { database ->
                database.settingsDao().upsert(setting = ApplicationSetting(key = "example", value = "saved"))
            }

            AppDatabase.open(file = file).useDatabase { database ->
                assertEquals(expected = "saved", actual = database.settingsDao().getValue(key = "example"))
                assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
            }
        }
    }

    @Test
    fun `existing version two database retains groups and settings`() {
        runBlocking {
            val file = temporaryDirectory.resolve("existing.db")
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                connection.execSQL(
                    sql = "CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)"
                )
                connection.execSQL(
                    sql = "CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)"
                )
                connection.execSQL(sql = "CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
                connection.execSQL(sql = "INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
                connection.execSQL(sql = "INSERT INTO `groups` VALUES (7, 'Work', 123)")
                connection.execSQL(sql = "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                connection.execSQL(
                    sql = "INSERT INTO room_master_table VALUES (42, '1f6fcf3a57abd711d5a91fe555af4949')"
                )
                connection.execSQL(sql = "PRAGMA user_version = 2")
            }

            AppDatabase.open(file = file).useDatabase { database ->
                assertEquals(
                    expected = GroupEntity(id = 7, name = "Work", color = 123),
                    actual = database.groupDao().getById(id = 7),
                )
                assertEquals(expected = "true", actual = database.settingsDao().getValue(key = "welcomeCompleted"))
                assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
                assertEquals(expected = 0L, actual = database.queryLong(sql = "SELECT COUNT(*) FROM tasks"))
                assertEquals(expected = 0, actual = database.groupDao().getWithTaskCounts(id = 7)?.planned)
            }
        }
    }

    @Test
    fun `version three migration retains tasks relationships and completion with stable timestamps`() {
        runBlocking {
            val file = temporaryDirectory.resolve("version-three.db")
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                connection.execSQL(
                    sql = "CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)"
                )
                connection.execSQL(
                    sql =
                        ("CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT " +
                            "NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)")
                )
                connection.execSQL(sql = "CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
                connection.execSQL(
                    sql = """
                    CREATE TABLE tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                        dueDate INTEGER, groupId INTEGER, completed INTEGER NOT NULL,
                        FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """
                        .trimIndent()
                )
                connection.execSQL(sql = "CREATE INDEX index_tasks_groupId ON tasks (groupId)")
                connection.execSQL(sql = "INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
                connection.execSQL(sql = "INSERT INTO `groups` VALUES (7, 'Work', 123), (8, 'Empty', 456)")
                connection.execSQL(
                    sql =
                        ("INSERT INTO tasks VALUES (10, 'Planned', 20400, 7, 0), (20, 'Done', NULL, 7, 1)," +
                            " (30, 'Ungrouped', NULL, NULL, 0)")
                )
                connection.execSQL(
                    sql = "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
                )
                connection.execSQL(
                    sql = "INSERT INTO room_master_table VALUES (42, '121e080f0db91c7c40cea2361ca47274')"
                )
                connection.execSQL(sql = "PRAGMA user_version = 3")
            }

            val beforeMigration = Clock.System.now().toEpochMilliseconds()
            var migratedAt = 0L
            AppDatabase.open(file = file).useDatabase { database ->
                val planned = assertNotNull(actual = database.taskDao().getById(id = 10))
                val completed = assertNotNull(actual = database.taskDao().getById(id = 20))
                val ungrouped = assertNotNull(actual = database.taskDao().getById(id = 30))
                assertTrue(actual = planned.createdAt in beforeMigration..Clock.System.now().toEpochMilliseconds())
                migratedAt = planned.createdAt
                assertEquals(expected = "Planned", actual = planned.name)
                assertEquals(expected = 20400L, actual = planned.dueDate)
                assertEquals(expected = 7, actual = planned.groupId)
                assertNull(actual = planned.completedAt)
                assertEquals(expected = "Done", actual = completed.name)
                assertEquals(expected = migratedAt, actual = completed.createdAt)
                assertEquals(expected = migratedAt, actual = completed.completedAt)
                assertNull(actual = completed.dueDate)
                assertEquals(expected = 7, actual = completed.groupId)
                assertEquals(expected = "Ungrouped", actual = ungrouped.name)
                assertNull(actual = ungrouped.groupId)
                assertNull(actual = ungrouped.completedAt)
                assertEquals(expected = migratedAt, actual = ungrouped.createdAt)
                val counts = assertNotNull(actual = database.groupDao().getWithTaskCounts(id = 7))
                assertEquals(expected = 1, actual = counts.planned)
                assertEquals(expected = 1, actual = counts.completed)
                val empty = assertNotNull(actual = database.groupDao().getWithTaskCounts(id = 8))
                assertEquals(expected = 0, actual = empty.planned)
                assertEquals(expected = 0, actual = empty.completed)
                assertEquals(
                    expected = GroupEntity(id = 7, name = "Work", color = 123),
                    actual = database.groupDao().getById(id = 7),
                )
                assertEquals(expected = "true", actual = database.settingsDao().getValue(key = "welcomeCompleted"))
                assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
            }

            AppDatabase.open(file = file).useDatabase { database ->
                assertEquals(expected = migratedAt, actual = database.taskDao().getById(id = 10)?.createdAt)
                assertEquals(expected = migratedAt, actual = database.taskDao().getById(id = 20)?.completedAt)
                database.groupDao().deleteById(id = 7)
                assertNull(actual = assertNotNull(actual = database.taskDao().getById(id = 10)).groupId)
                assertNull(actual = assertNotNull(actual = database.taskDao().getById(id = 20)).groupId)
                assertEquals(expected = 3L, actual = database.queryLong(sql = "SELECT COUNT(*) FROM tasks"))
            }
        }
    }

    @Test
    fun `version four migration preserves one-off tasks timestamps and initializes empty recurrence tables`() {
        runBlocking {
            val file = temporaryDirectory.resolve("version-four.db")
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                connection.execSQL(
                    sql = "CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)"
                )
                connection.execSQL(
                    sql =
                        ("CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name " +
                            "TEXT NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)")
                )
                connection.execSQL(sql = "CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
                connection.execSQL(
                    sql = """
                    CREATE TABLE tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                        dueDate INTEGER, groupId INTEGER, createdAt INTEGER NOT NULL, completedAt INTEGER,
                        FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """
                        .trimIndent()
                )
                connection.execSQL(sql = "CREATE INDEX index_tasks_groupId ON tasks (groupId)")
                connection.execSQL(sql = "INSERT INTO `groups` VALUES (7, 'Work', 123)")
                connection.execSQL(
                    sql =
                        ("INSERT INTO tasks VALUES (10, 'Existing', 20400, 7, 1000, 2000), (20, " +
                            "'Undated', NULL, NULL, 3000, NULL)")
                )
                connection.execSQL(
                    sql = "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
                )
                connection.execSQL(
                    sql = "INSERT INTO room_master_table VALUES (42, '24cb2fa7c171c7cfc361b6fcd04b881f')"
                )
                connection.execSQL(sql = "PRAGMA user_version = 4")
            }
            AppDatabase.open(file = file).useDatabase { database ->
                val existing = assertNotNull(actual = database.taskDao().getRecord(id = 10))
                assertEquals(expected = "Existing", actual = existing.task.name)
                assertEquals(expected = 20400L, actual = existing.task.dueDate)
                assertEquals(expected = 7, actual = existing.task.groupId)
                assertEquals(expected = 1000L, actual = existing.task.createdAt)
                assertEquals(expected = 2000L, actual = existing.task.completedAt)
                assertTrue(actual = !existing.task.recurring)
                assertTrue(actual = existing.schedules.isEmpty())
                assertTrue(actual = existing.occurrences.isEmpty())
                assertNull(actual = database.taskDao().getById(id = 20)?.dueDate)
                assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
            }
        }
    }

    @Test
    fun `version five migration adds empty descriptions without changing tasks schedules or exceptions`() {
        runBlocking {
            val file = temporaryDirectory.resolve("version-five.db")
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                connection.execSQL(
                    sql = "CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)"
                )
                connection.execSQL(
                    sql =
                        ("CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name " +
                            "TEXT NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)")
                )
                connection.execSQL(sql = "CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
                connection.execSQL(
                    sql = """
                    CREATE TABLE tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                        dueDate INTEGER, groupId INTEGER, createdAt INTEGER NOT NULL, completedAt INTEGER,
                        FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """
                        .trimIndent()
                )
                connection.execSQL(sql = "CREATE INDEX index_tasks_groupId ON tasks (groupId)")
                Migration4To5().migrate(connection = connection)
                connection.execSQL(sql = "INSERT INTO `groups` VALUES (7, 'Health', 123)")
                connection.execSQL(
                    sql =
                        ("INSERT INTO tasks VALUES (10, 'Workout', 20760, 7, 1000, NULL, 1), (20, " +
                            "'One-off', NULL, NULL, 3000, 4000, 0)")
                )
                connection.execSQL(
                    sql = "INSERT INTO task_schedules VALUES (30, 10, 20760, 'Daily', 1, 0, 1, 1, NULL, NULL, NULL)"
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO task_occurrences VALUES (30, 20760, 10, NULL, 2000, 0), (30, " +
                            "20761, 10, 20762, NULL, 0)")
                )
                connection.execSQL(
                    sql = "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
                )
                connection.execSQL(
                    sql = "INSERT INTO room_master_table VALUES (42, '57598c43e645afd970e5aafdaff4c85b')"
                )
                connection.execSQL(sql = "PRAGMA user_version = 5")
            }
            AppDatabase.open(file = file).useDatabase { database ->
                val recurring = assertNotNull(actual = database.taskDao().getRecord(id = 10))
                assertEquals(expected = "", actual = recurring.task.description)
                assertEquals(expected = "Workout", actual = recurring.task.name)
                assertEquals(expected = 7, actual = recurring.task.groupId)
                assertEquals(expected = 20760L, actual = recurring.task.dueDate)
                assertEquals(expected = 1000L, actual = recurring.task.createdAt)
                assertTrue(actual = recurring.task.recurring)
                assertEquals(expected = 30L, actual = recurring.schedules.single().id)
                assertEquals(expected = 20760L, actual = recurring.schedules.single().startDate)
                assertEquals(expected = 2, actual = recurring.occurrences.size)
                assertEquals(
                    expected = 2000L,
                    actual = recurring.occurrences.single { occurrence ->
                        occurrence.scheduledDate == 20760L
                    }.completedAt,
                )
                assertEquals(
                    expected = 20762L,
                    actual = recurring.occurrences.single { occurrence ->
                        occurrence.scheduledDate == 20761L
                    }.movedDate,
                )
                val oneOff = assertNotNull(actual = database.taskDao().getById(id = 20))
                assertEquals(expected = "", actual = oneOff.description)
                assertEquals(expected = 4000L, actual = oneOff.completedAt)
                assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
                database.taskDao().update(task = recurring.task.copy(description = "First line\nSecond line"))
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertEquals(
                    expected = "First line\nSecond line",
                    actual = database.taskDao().getById(id = 10)?.description,
                )
                assertEquals(expected = "", actual = database.taskDao().getById(id = 20)?.description)
            }
        }
    }

    @Test
    fun `version six migration retains descriptions recurrence and completion and leaves timing unset`() {
        runBlocking {
            val file = temporaryDirectory.resolve("version-six.db")
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                connection.execSQL(
                    sql = "CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)"
                )
                connection.execSQL(
                    sql =
                        ("CREATE TABLE `groups` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT " +
                            "NOT NULL COLLATE NOCASE, color INTEGER NOT NULL)")
                )
                connection.execSQL(sql = "CREATE UNIQUE INDEX index_groups_name ON `groups` (name)")
                connection.execSQL(
                    sql = """
                    CREATE TABLE tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
                        dueDate INTEGER, groupId INTEGER, createdAt INTEGER NOT NULL, completedAt INTEGER,
                        FOREIGN KEY(groupId) REFERENCES `groups`(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """
                        .trimIndent()
                )
                connection.execSQL(sql = "CREATE INDEX index_tasks_groupId ON tasks (groupId)")
                Migration4To5().migrate(connection = connection)
                Migration5To6().migrate(connection = connection)
                connection.execSQL(sql = "INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
                connection.execSQL(sql = "INSERT INTO `groups` VALUES (7, 'Work', 123)")
                connection.execSQL(
                    sql =
                        ("INSERT INTO tasks VALUES (10, 'Routine', 20760, 7, 1000, NULL, 1, 'Existing " +
                            "description'), (20, 'Done', NULL, NULL, 3000, 4000, 0, 'Saved note')")
                )
                connection.execSQL(
                    sql = "INSERT INTO task_schedules VALUES (30, 10, 20760, 'Daily', 1, 0, 1, 1, NULL, 3, NULL)"
                )
                connection.execSQL(
                    sql =
                        ("INSERT INTO task_occurrences VALUES (30, 20760, 10, NULL, 2000, 0), (30, 20761, " +
                            "10, 20762, NULL, 0)")
                )
                connection.execSQL(
                    sql = "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
                )
                connection.execSQL(
                    sql = "INSERT INTO room_master_table VALUES (42, 'be1a15228ccb99640ff5ef123777e833')"
                )
                connection.execSQL(sql = "PRAGMA user_version = 6")
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
                val record = assertNotNull(actual = database.taskDao().getRecord(id = 10))
                assertEquals(expected = "Existing description", actual = record.task.description)
                assertEquals(expected = 7, actual = record.task.groupId)
                assertEquals(expected = 1000L, actual = record.task.createdAt)
                assertEquals(expected = 3, actual = record.schedules.single().occurrenceCount)
                assertEquals(expected = 2, actual = record.occurrences.size)
                assertEquals(
                    expected = 2000L,
                    actual = record.occurrences.single { occurrence -> occurrence.scheduledDate == 20760L }.completedAt,
                )
                assertEquals(
                    expected = 20762L,
                    actual = record.occurrences.single { occurrence -> occurrence.scheduledDate == 20761L }.movedDate,
                )
                assertEquals(expected = "Saved note", actual = database.taskDao().getById(id = 20)?.description)
                assertEquals(expected = 4000L, actual = database.taskDao().getById(id = 20)?.completedAt)
                assertEquals(expected = "true", actual = database.settingsDao().getValue(key = "welcomeCompleted"))
            }
        }
    }

    @Test
    fun `foreign keys protect relationships on every new connection`() {
        runBlocking {
            val file = temporaryDirectory.resolve("taskb.db")
            AppDatabase.open(file = file).useDatabase { database ->
                database.useWriterConnection { connection ->
                    connection
                        .usePrepared(sql = "CREATE TABLE test_parents (id INTEGER NOT NULL PRIMARY KEY)") { statement ->
                            statement.step()
                        }
                    connection.usePrepared(
                        sql = "CREATE TABLE test_children (id INTEGER NOT NULL PRIMARY KEY, " +
                            "parent_id INTEGER NOT NULL REFERENCES test_parents(id) ON DELETE RESTRICT)",
                        block = SQLiteStatement::step,
                    )
                }
            }

            repeat(times = 2) {
                AppDatabase.open(file = file).useDatabase { database ->
                    assertEquals(expected = 1L, actual = database.queryLong(sql = "PRAGMA foreign_keys"))
                    assertFailsWith<SQLiteException> {
                        database.useWriterConnection { connection ->
                            connection.usePrepared(sql = "INSERT INTO test_children VALUES (1, 999)") { statement ->
                                statement.step()
                            }
                        }
                    }
                    assertEquals(expected = 0L, actual = database.queryLong(sql = "SELECT COUNT(*) FROM test_children"))
                }
            }
        }
    }

    @Test
    fun `failed transaction leaves no partial changes`() {
        runBlocking {
            AppDatabase.open(file = temporaryDirectory.resolve("taskb.db")).useDatabase { database ->
                assertFailsWith<IllegalStateException> {
                    database.withWriteTransaction {
                        database
                            .settingsDao()
                            .upsert(setting = ApplicationSetting(key = "incomplete", value = "must be rolled back"))
                        error(message = "Simulated failure")
                    }
                }

                assertEquals(
                    expected = 0L,
                    actual = database.queryLong(sql = "SELECT COUNT(*) FROM application_settings"),
                )
            }
        }
    }

    @Test
    fun `newer database schema is rejected without changing existing data`() {
        runBlocking {
            val file = temporaryDirectory.resolve("newer.db")
            val driver = BundledSQLiteDriver()
            driver.open(fileName = file.toString()).use { connection ->
                connection.execSQL(sql = "CREATE TABLE future_data (value TEXT NOT NULL)")
                connection.execSQL(sql = "INSERT INTO future_data VALUES ('preserved')")
                connection.execSQL(sql = "PRAGMA user_version = 14")
            }

            AppDatabase.open(file = file).useDatabase { database ->
                assertFailsWith<IllegalStateException> {
                    database.settingsDao().getValue(key = "example")
                }
            }

            driver.open(fileName = file.toString()).use { connection ->
                connection.prepare(sql = "SELECT value FROM future_data").use { statement ->
                    assertTrue(actual = statement.step())
                    assertEquals(expected = "preserved", actual = statement.getText(index = 0))
                }
                connection.prepare(sql = "PRAGMA user_version").use { statement ->
                    assertTrue(actual = statement.step())
                    assertEquals(expected = 14L, actual = statement.getLong(index = 0))
                }
            }
        }
    }

    @Test
    fun `version one database is recreated without a data migration`() {
        runBlocking {
            val file = temporaryDirectory.resolve("old.db")
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                connection.execSQL(
                    sql = "CREATE TABLE application_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)"
                )
                connection.execSQL(sql = "INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
                connection.execSQL(sql = "PRAGMA user_version = 1")
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertNull(actual = database.settingsDao().getValue(key = "welcomeCompleted"))
                assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
                assertEquals(expected = 0L, actual = database.queryLong(sql = "SELECT COUNT(*) FROM `groups`"))
            }
        }
    }

    @Test
    fun `version seven upgrade creates event storage and transfers timed tasks through the complete migration chain`() {
        runBlocking {
            val file = temporaryDirectory.resolve("version-seven.db")
            BundledSQLiteDriver().open(fileName = file.toString()).use { connection ->
                connection.execSQL(
                    sql =
                        ("CREATE TABLE IF NOT EXISTS `application_settings` (`key` TEXT NOT NULL, " +
                            "`value` TEXT NOT NULL, PRIMARY KEY(`key`))")
                )
                connection.execSQL(
                    sql =
                        ("CREATE TABLE IF NOT EXISTS `groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                            "NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, `color` INTEGER NOT NULL)")
                )
                connection.execSQL(
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS `index_groups_name` ON `groups` (`name`)"
                )
                connection.execSQL(
                    sql =
                        ("CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                            "NOT NULL, `name` TEXT NOT NULL, `dueDate` INTEGER, `groupId` INTEGER, " +
                            "`createdAt` INTEGER NOT NULL, `completedAt` INTEGER, `recurring` INTEGER " +
                            "NOT NULL DEFAULT 0, `description` TEXT NOT NULL DEFAULT '', " +
                            "`startTimeMinutes` INTEGER, `durationMinutes` INTEGER, FOREIGN " +
                            "KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE SET " +
                            "NULL )")
                )
                connection.execSQL(sql = "CREATE INDEX IF NOT EXISTS `index_tasks_groupId` ON `tasks` (`groupId`)")
                connection.execSQL(
                    sql =
                        ("CREATE TABLE IF NOT EXISTS `task_schedules` (`id` INTEGER PRIMARY KEY " +
                            "AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `startDate` INTEGER NOT " +
                            "NULL, `frequency` TEXT NOT NULL, `interval` INTEGER NOT NULL, `weekdays` " +
                            "INTEGER NOT NULL, `dayOfMonth` INTEGER NOT NULL, `month` INTEGER NOT NULL, " +
                            "`endDate` INTEGER, `occurrenceCount` INTEGER, `closedBefore` INTEGER, " +
                            "`startTimeMinutes` INTEGER, `durationMinutes` INTEGER, FOREIGN " +
                            "KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE " +
                            "CASCADE )")
                )
                connection.execSQL(
                    sql = "CREATE INDEX IF NOT EXISTS `index_task_schedules_taskId` ON `task_schedules` (`taskId`)"
                )
                connection.execSQL(
                    sql =
                        ("CREATE TABLE IF NOT EXISTS `task_occurrences` (`scheduleId` INTEGER NOT " +
                            "NULL, `scheduledDate` INTEGER NOT NULL, `taskId` INTEGER NOT NULL, " +
                            "`movedDate` INTEGER, `completedAt` INTEGER, `skipped` INTEGER NOT NULL, " +
                            "`overridesTiming` INTEGER NOT NULL DEFAULT 0, `startTimeMinutes` INTEGER, " +
                            "`durationMinutes` INTEGER, PRIMARY KEY(`scheduleId`, `scheduledDate`), " +
                            "FOREIGN KEY(`scheduleId`) REFERENCES `task_schedules`(`id`) ON UPDATE NO " +
                            "ACTION ON DELETE CASCADE , FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )")
                )
                connection.execSQL(
                    sql =
                        "CREATE INDEX IF NOT EXISTS `index_task_occurrences_taskId` ON `task_occurrences` (`taskId`)"
                )
                connection.execSQL(
                    sql =
                        ("CREATE INDEX IF NOT EXISTS `index_task_occurrences_scheduleId` ON " +
                            "`task_occurrences` (`scheduleId`)")
                )
                connection.execSQL(sql = "INSERT INTO application_settings VALUES ('welcomeCompleted', 'true')")
                connection.execSQL(sql = "INSERT INTO groups VALUES (7, 'Work', 123)")
                connection.execSQL(
                    sql = "INSERT INTO tasks VALUES (10, 'Meeting', 20760, 7, 1000, 2000, 1, 'Notes', 540, 90)"
                )
                connection.execSQL(
                    sql =
                        "INSERT INTO task_schedules VALUES (30, 10, 20760, 'Daily', 1, 0, 1, 1, NULL, 3, NULL, 540, 90)"
                )
                connection.execSQL(
                    sql = "INSERT INTO task_occurrences VALUES (30, 20760, 10, 20761, 2000, 0, 1, 600, 30)"
                )
                connection.execSQL(
                    sql = "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
                )
                connection.execSQL(
                    sql = "INSERT INTO room_master_table VALUES (42, 'ebf3640ce9b83ba4ef80e0a6f0323469')"
                )
                connection.execSQL(sql = "PRAGMA user_version = 7")
            }
            AppDatabase.open(file = file).useDatabase { database ->
                assertEquals(expected = 14L, actual = database.queryLong(sql = "PRAGMA user_version"))
                assertNull(actual = database.taskDao().getRecord(id = 10))
                val migrated = database.eventDao().observeRecords().first().single()
                assertEquals(expected = "Meeting", actual = migrated.event.name)
                assertEquals(expected = "Notes", actual = migrated.event.description)
                assertEquals(expected = 1000L, actual = migrated.event.createdAt)
                assertEquals(expected = 540, actual = migrated.schedules.single().startTimeMinutes)
                assertEquals(expected = 600, actual = migrated.occurrences.single().startTimeMinutes)
                assertEquals(expected = 20761L, actual = migrated.occurrences.single().movedDate)
                val id = database
                    .eventDao()
                    .insert(
                        event = com.mkulikov.taskb.feature.item.api.database.EventEntity(
                            name = "Independent event",
                            groupId = 7,
                        )
                    )
                    .toInt()
                assertEquals(expected = "Independent event", actual = database.eventDao().getById(id = id)?.name)
                assertEquals(expected = 0L, actual = database.queryLong(sql = "SELECT COUNT(*) FROM tasks"))
                assertEquals(expected = "true", actual = database.settingsDao().getValue(key = "welcomeCompleted"))
                database.groupDao().deleteById(id = 7)
                assertNull(actual = database.eventDao().getById(id = id)?.groupId)
                assertNull(actual = database.eventDao().getById(id = migrated.event.id)?.groupId)
            }
        }
    }

    private suspend fun AppDatabase.queryLong(sql: String): Long {
        return useReaderConnection { connection ->
            connection.usePrepared(sql = sql) { statement ->
                check(value = statement.step()) { "Query returned no rows: $sql" }
                statement.getLong(index = 0)
            }
        }
    }
}
