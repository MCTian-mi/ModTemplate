plugins {
    java
    alias(libs.plugins.shadow)
}

val shadowImplementation = configurations.create("shadowImplementation")

configurations.implementation {
    extendsFrom(shadowImplementation)
}

tasks.shadowJar {
    archiveClassifier = "shadowed"

    dependencies {
        // TODO)) move to a version catalog?
        exclude(dependency("org.jspecify:jspecify:.*"))
        exclude(dependency("org.jetbrains:annotations:.*"))
    }

    configurations = listOf(
        // embedOnly,
        shadowImplementation,
    )
    if (enableJvmdg) {
        configurations.add(project.configurations.named("shadowDowngrade"))
    }

    if (minimizeShadowedDependencies) minimize()
    if (relocateShadowedDependencies) {
        enableAutoRelocation.set(true)
        relocationPrefix.set(defaultShadowPath)
    }
}