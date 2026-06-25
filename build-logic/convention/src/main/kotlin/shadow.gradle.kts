plugins {
    java
    alias(libs.plugins.shadow)
}

val Project.shadowImplementation: Configuration by configurations.creating

configurations.implementation {
    extendsFrom(shadowImplementation)
}

tasks.shadowJar {
    archiveClassifier = "shadowed"

    configurations = listOf(
//        embedOnly,
        shadowImplementation,
//        shadowDowngrade,
    )

    if (minimizeShadowedDependencies) minimize()
    if (relocateShadowedDependencies) {
        enableAutoRelocation.set(true)
        relocationPrefix.set(defaultShadowPath)
    }
}