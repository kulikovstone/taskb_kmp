import com.mkulikov.buildlogic.GitCommitCountSource
import com.mkulikov.buildlogic.GitVersionSource
import com.mkulikov.buildlogic.PrintVersion
import com.mkulikov.buildlogic.TaskBVersion

require(project == rootProject) { "Apply taskb.versioning to the root project only" }

val applicationVersion = providers.of(GitVersionSource::class) {
    parameters.repositoryDirectory.set(layout.projectDirectory)
}.get()
val androidVersionCode = providers.of(GitCommitCountSource::class) {
    parameters.repositoryDirectory.set(layout.projectDirectory)
}.get()

extensions.add("taskbVersion", TaskBVersion(applicationVersion, androidVersionCode))

allprojects {
    version = applicationVersion
}

tasks.register<PrintVersion>("printVersion") {
    group = "versioning"
    description = "Prints the application version calculated from version-major.txt and Git history."
    versionName.set(applicationVersion)
}
