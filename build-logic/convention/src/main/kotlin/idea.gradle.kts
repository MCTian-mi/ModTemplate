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