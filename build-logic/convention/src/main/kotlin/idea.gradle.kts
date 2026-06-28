plugins {
    java
    alias(libs.plugins.ideaExt)
}

// Project properties
group = modGroup
version = modVersion

base {
    archivesName = archiveName
}

idea {
    module {
//        inheritOutputDirs = true
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
