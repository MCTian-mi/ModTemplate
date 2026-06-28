import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    alias(libs.plugins.retrofuturaGradle)
}

sourceSets {
    test {
        java {
            compileClasspath += patchedMc.get().output + mcLauncher.get().output
            runtimeClasspath += patchedMc.get().output + mcLauncher.get().output
        } // TODO)) is there a better way...?
    }
}

tasks.test {
    // ensure tests are run with java8
//    javaLauncher = javaToolchains.launcherFor {
//        languageVersion.set(JavaLanguageVersion.of(8))
//    } TODO)) do we really need it on java 8?
    testLogging {
        events(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
        showStandardStreams = true
    }

    if (enableJUnit) useJUnitPlatform()
}

dependencies {
    if (enableJUnit) {
        testImplementation(platform(libs.junit.bom))
        testImplementation(libs.junit.jupiter)
        testRuntimeOnly(libs.junit.platform.launcher)
    }
}