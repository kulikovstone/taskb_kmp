package com.mkulikov.taskb.core.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import com.mkulikov.taskb.core.database.migration.Migration3To4
import com.mkulikov.taskb.core.database.migration.Migration4To5
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.group.api.database.GroupEntity
import com.mkulikov.taskb.feature.group.api.database.IGroupDao
import com.mkulikov.taskb.feature.task.api.database.ITaskDao
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import com.mkulikov.taskb.feature.task.api.database.TaskScheduleEntity
import com.mkulikov.taskb.feature.task.api.database.TaskOccurrenceEntity
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [ApplicationSetting::class, GroupEntity::class, TaskEntity::class, TaskScheduleEntity::class, TaskOccurrenceEntity::class],
    version = 5,
    autoMigrations = [AutoMigration(from = 2, to = 3)],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): IAppSettingsDao
    abstract fun groupDao(): IGroupDao
    abstract fun taskDao(): ITaskDao

    companion object {
        internal fun open(builder: Builder<AppDatabase>, driver: SQLiteDriver): AppDatabase =
            builder
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.Default)
                .addMigrations(Migration3To4(), Migration4To5())
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
    }
}
