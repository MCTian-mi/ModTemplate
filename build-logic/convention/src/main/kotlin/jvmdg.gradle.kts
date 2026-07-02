import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar
import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles
import java.io.Serializable as JSerializable

plugins {
    alias(libs.plugins.jvmDowngrader)
    alias(libs.plugins.retrofuturaGradle)
}

val shadowDowngrade = configurations.create("shadowDowngrade")

configurations.compileOnly {
    extendsFrom(shadowDowngrade)
}

// JvmDowngrader
tasks.withType<DowngradeJar>().configureEach { logLevel.set("FATAL") }
tasks.withType<DowngradeFiles>().configureEach { logLevel.set("FATAL") }

jvmdg.apply {
    shadePath = ConstantShadePath(jvmdgShadowPath)
    dg(shadowDowngrade)
}

val downgradeRunJar = tasks.register<DowngradeJar>("downgradeRunJar") {
    description = "Downgrade the slim project jar for Minecraft run tasks"
    dependsOn(tasks.jar)
    inputFile = tasks.jar.flatMap { it.archiveFile }
    archiveClassifier = "run-downgraded"
}

val shadeRunDowngradedApi = tasks.register<ShadeJar>("shadeRunDowngradedApi") {
    description = "Shade JvmDowngrader API stubs into the downgraded run jar"
    dependsOn(downgradeRunJar)
    inputFile = downgradeRunJar.flatMap { it.archiveFile }
    archiveClassifier = "run-downgraded-shaded"
}

val downgradeTestClasses = tasks.register<DowngradeFiles>("downgradeTestClasses") {
    description = "Downgrade classes in src/tests"
    inputCollection = files(sourceSets["test"].output.classesDirs, sourceSets["api"].output.classesDirs)
    dependsOn(tasks.testClasses, tasks.apiClasses)
}

tasks.test {
    dependsOn(tasks.shadeDowngradedApi, downgradeTestClasses)
} // TODO)) fix tests

tasks.reobfJar { inputJar.set(tasks.shadeDowngradedApi.flatMap { it.archiveFile }) }

// RunObf* tasks are intentionally excluded, since they relay on reobfJar
tasks.withType<RunMinecraftTask>().configureEach {
    if (!systemProperties.contains("retrofuturagradle.reobfDev")) {
        dependsOn(shadeRunDowngradedApi)
        classpath = classpath - files(tasks.jar) + files(shadeRunDowngradedApi) + files(shadowDowngrade)
    }
}

tasks.compileInjectedInterfacesJava {
    javaCompiler = javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(8))
        vendor.set(JvmVendorSpec.AZUL)
    } // TODO)) should this be downgraded?
}

private class ConstantShadePath(private val path: String) : (String) -> String, JSerializable {
    override fun invoke(fileName: String): String = path
}
