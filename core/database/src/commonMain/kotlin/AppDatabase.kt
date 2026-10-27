package com.mkulikov.taskb.core.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import com.mkulikov.taskb.feature.settings.api.database.ApplicationSetting
import com.mkulikov.taskb.feature.settings.api.database.IAppSettingsDao
import com.mkulikov.taskb.feature.task.api.database.GroupEntity
import com.mkulikov.taskb.feature.task.api.database.IGroupDao
import com.mkulikov.taskb.feature.task.api.database.ITaskDao
import com.mkulikov.taskb.feature.task.api.database.TaskEntity
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [ApplicationSetting::class, GroupEntity::class, TaskEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 2, to = 3)],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): IAppSettingsDao
    abstract fun groupDao(): IGroupDao
    abstract fun taskDao(): ITaskDao

    companion object {
        fun open(name: String, driver: SQLiteDriver): AppDatabase =
            Room.databaseBuilder<AppDatabase>(name = name, factory = AppDatabaseConstructor::initialize)
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.Default)
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
    }
}
