pluginManagement {
    includeBuild("../build-logic")
    repositories {
        maven {
            // RetroFuturaGradle
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
                includeGroup("com.gtnewhorizons")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "forgePatchesExtra"

dependencyResolutionManagement {
    versionCatalogs {
        create("rfbPatchLibs") {
            from(files("./gradle/libs.versions.toml"))
        }
    }
}
