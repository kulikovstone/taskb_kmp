package com.mkulikov.taskb

import com.mkulikov.taskb.di.appModule
import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import java.nio.file.Path

internal inline fun <T> withAppKoin(file: Path, block: (KoinApplication) -> T): T {
    val application = koinApplication { modules(appModule { AppDatabase.open(file) }) }
    return try {
        block(application)
    } finally {
        application.close()
    }
}
