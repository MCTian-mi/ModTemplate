import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar
import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles
import java.io.Serializable

plugins {
    alias(libs.plugins.jvmDowngrader)
    alias(libs.plugins.retrofuturaGradle)
}

val shadowDowngrade: Configuration by configurations.creating

configurations.compileOnly {
    extendsFrom(shadowDowngrade)
}

// JvmDowngrader
tasks.withType<DowngradeJar>().configureEach { logLevel.set("FATAL") }
tasks.withType<DowngradeFiles>().configureEach { logLevel.set("FATAL") }


jvmdg.apply {
    shadePath.set(ConstantShadePath(jvmdgShadowPath.ifEmpty { defaultShadowPath }))
//    defaultTask {
//        enabled = enableJvmdg
//    }
    dg(shadowDowngrade)
}

val downgradeRunJar by tasks.registering(DowngradeJar::class) {
    description = "Downgrade the slim project jar for Minecraft run tasks"
    dependsOn(tasks.jar)
    inputFile = tasks.jar.flatMap { it.archiveFile }
    archiveClassifier = "run-downgraded"
}

val shadeRunDowngradedApi by tasks.registering(ShadeJar::class) {
    description = "Shade JvmDowngrader API stubs into the downgraded run jar"
    dependsOn(downgradeRunJar)
    inputFile = downgradeRunJar.flatMap { it.archiveFile }
    archiveClassifier = "run-downgraded-shaded"
}

val downgradeTestClasses by tasks.registering(DowngradeFiles::class) {
    description = "Downgrade classes in src/tests"
    inputCollection = files(sourceSets["test"].output.classesDirs, sourceSets["api"].output.classesDirs)
    dependsOn(tasks.testClasses, tasks.apiClasses)
}

tasks.test {
    dependsOn(tasks.shadeDowngradedApi, downgradeTestClasses)
} // TODO)) fix tests

tasks.reobfJar { inputJar.set(tasks.shadeDowngradedApi.flatMap { it.archiveFile }) }

tasks.withType<RunMinecraftTask>().configureEach {
    dependsOn(shadeRunDowngradedApi)
    classpath = classpath - files(tasks.jar) + files(shadeRunDowngradedApi) + files(shadowDowngrade)
}

class ConstantShadePath(private val path: String) : (String) -> String, Serializable {
    override fun invoke(fileName: String): String = path
}