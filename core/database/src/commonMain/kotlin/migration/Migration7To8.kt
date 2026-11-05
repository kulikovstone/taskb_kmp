package com.mkulikov.taskb.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration7To8 : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dueDate` INTEGER, `groupId` INTEGER, `createdAt` INTEGER NOT NULL, `recurring` INTEGER NOT NULL DEFAULT 0, `description` TEXT NOT NULL DEFAULT '', `startTimeMinutes` INTEGER, `durationMinutes` INTEGER, FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_events_groupId` ON `events` (`groupId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `event_schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `frequency` TEXT NOT NULL, `interval` INTEGER NOT NULL, `weekdays` INTEGER NOT NULL, `dayOfMonth` INTEGER NOT NULL, `month` INTEGER NOT NULL, `endDate` INTEGER, `occurrenceCount` INTEGER, `closedBefore` INTEGER, `startTimeMinutes` INTEGER, `durationMinutes` INTEGER, FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_event_schedules_eventId` ON `event_schedules` (`eventId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `event_occurrences` (`scheduleId` INTEGER NOT NULL, `scheduledDate` INTEGER NOT NULL, `eventId` INTEGER NOT NULL, `movedDate` INTEGER, `skipped` INTEGER NOT NULL, `overridesTiming` INTEGER NOT NULL DEFAULT 0, `startTimeMinutes` INTEGER, `durationMinutes` INTEGER, PRIMARY KEY(`scheduleId`, `scheduledDate`), FOREIGN KEY(`scheduleId`) REFERENCES `event_schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_event_occurrences_eventId` ON `event_occurrences` (`eventId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_event_occurrences_scheduleId` ON `event_occurrences` (`scheduleId`)")
    }
}
