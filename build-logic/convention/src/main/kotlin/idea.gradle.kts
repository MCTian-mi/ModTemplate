import org.gradle.kotlin.dsl.base
import org.gradle.kotlin.dsl.idea

plugins {
    java
    alias(libs.plugins.ideaExt)
    alias(libs.plugins.retrofuturaGradle)
}

// Project properties
group = modGroup
version = modVersion

base {
    archivesName = archiveName
}

// Allows injectTags to work when Gradle or idea syncs
tasks.processIdeaSettings {
    dependsOn(tasks.injectTags)
}

idea {
    module {
//        inheritOutputDirs = true
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
