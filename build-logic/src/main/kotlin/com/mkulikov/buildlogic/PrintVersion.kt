package com.mkulikov.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Prints the current version without producing an output file")
abstract class PrintVersion : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @TaskAction
    fun print() {
        logger.quiet(versionName.get())
    }
}
