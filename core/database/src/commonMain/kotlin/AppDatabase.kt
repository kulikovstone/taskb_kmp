package com.mkulikov.taskb.core.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import com.mkulikov.taskb.core.database.migration.Migration10To11
import com.mkulikov.taskb.core.database.migration.Migration11To12
import com.mkulikov.taskb.core.database.migration.Migration12To13
import com.mkulikov.taskb.core.database.migration.Migration3To4
import com.mkulikov.taskb.core.database.migration.Migration4To5
import com.mkulikov.taskb.core.database.migration.Migration5To6
import com.mkulikov.taskb.core.database.migration.Migration6To7
import com.mkulikov.taskb.core.database.migration.Migration7To8
import com.mkulikov.taskb.core.database.migration.Migration8To9
import com.mkulikov.taskb.core.database.migration.Migration9To10
import com.mkulikov.taskb.feature.event.api.database.*
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.group.api.database.IGroupDao
import com.mkulikov.taskb.feature.holiday.api.database.HolidayEntity
import com.mkulikov.taskb.feature.holiday.api.database.HolidayMonthEntity
import com.mkulikov.taskb.feature.holiday.api.database.IHolidayDao
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.task.api.database.ITaskDao
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.feature.task.api.database.TaskOccurrenceEntity
import com.mkulikov.taskb.feature.task.api.database.TaskScheduleEntity
import kotlinx.coroutines.Dispatchers

@Database(
    entities =
        [
            ApplicationSetting::class,
            GroupEntity::class,
            TaskEntity::class,
            TaskScheduleEntity::class,
            TaskOccurrenceEntity::class,
            EventEntity::class,
            EventScheduleEntity::class,
            EventOccurrenceEntity::class,
            HolidayEntity::class,
            HolidayMonthEntity::class,
        ],
    version = 13,
    autoMigrations = [AutoMigration(from = 2, to = 3)],
)
@ConstructedBy(value = AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): IAppSettingsDao

    abstract fun groupDao(): IGroupDao

    abstract fun taskDao(): ITaskDao

    abstract fun eventDao(): IEventDao

    abstract fun holidayDao(): IHolidayDao

    companion object {
        internal fun open(builder: Builder<AppDatabase>, driver: SQLiteDriver): AppDatabase {
            return builder
                .setDriver(driver = driver)
                .setQueryCoroutineContext(context = Dispatchers.Default)
                .addMigrations(
                    Migration3To4(),
                    Migration4To5(),
                    Migration5To6(),
                    Migration6To7(),
                    Migration7To8(),
                    Migration8To9(),
                    Migration9To10(),
                    Migration10To11(),
                    Migration11To12(),
                    Migration12To13(),
                )
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()
        }
    }
}
