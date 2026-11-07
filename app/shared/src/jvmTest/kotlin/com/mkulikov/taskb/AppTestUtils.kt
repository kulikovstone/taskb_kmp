package com.mkulikov.taskb

import com.mkulikov.taskb.core.database.AppDatabase
import com.mkulikov.taskb.core.database.open
import com.mkulikov.taskb.di.appModule
import java.nio.file.Path
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

internal inline fun <T> withAppKoin(file: Path, block: (KoinApplication) -> T): T {
    val application = koinApplication { modules(modules = appModule { AppDatabase.open(file = file) }) }
    return try {
        block(application)
    } finally {
        application.close()
    }
}
