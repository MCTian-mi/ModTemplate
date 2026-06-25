plugins {
    java
}

if (enableJUnit) {
    tasks.test {
        useJUnitPlatform()
    }

    dependencies {
        testImplementation(platform(libs.junit.bom))
        testImplementation(libs.junit.jupiter)
        testRuntimeOnly(libs.junit.platform.launcher)
    }
}