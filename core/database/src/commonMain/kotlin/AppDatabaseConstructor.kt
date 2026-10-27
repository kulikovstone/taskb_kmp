package com.mkulikov.taskb.core.database

import androidx.room3.RoomDatabaseConstructor

// Room's KSP processor generates an actual constructor for every target.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
