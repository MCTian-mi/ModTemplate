import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles

plugins {
    id("java")
    id("java-library")
    id("maven-publish")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.4.1"
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.2"
    id("net.darkhax.curseforgegradle") version "1.3.32"
    id("com.modrinth.minotaur") version "2.8.10"
    id("com.gradleup.shadow") version "9.4.2"
    id("xyz.wagyourtail.jvmdowngrader") version "1.3.6"
    id("com.dorongold.task-tree") version "4.0.1"
}

repositories {
    mavenCentral()
}

val embedOnly: Configuration by configurations.creating
val shadowImplementation: Configuration by configurations.creating

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    shadowImplementation("org.pcollections:pcollections:5.0.0") // TODO))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}


// Read properties from gradle.properties
val modName: String by project
val modId: String by project
val modGroup: String by project
val modVersion: String by project
val archiveName: String by project
val versionDisplayFormat: String by project

val minecraftVersion: String by project
val devUserName: String by project

val extJavaArgs: String by project
val enableModernJavaSyntax: String by project
val enableJava17RunTasks: String by project

val generateTags: String by project

val atFile: String by project
val useMixin: String by project
val mixinProviderSpec: String by project
val mixinPackage: String by project
val mixinConfigRefmap: String by project
val generateMixinConfig: String by project
val forceEnableMixins: String by project
val coreModClass: String by project
val enableCoreModDebug: String by project

val stripForgeRequirements: String by project

val useShadowDeps: String by project
val minimizeShadowedDependencies: String by project
val relocateShadowedDependencies: String by project

val separateRunDirectories: String by project

val modrinthProjectId: String by project
val modrinthRelations: String by project
val curseForgeProjectId: String by project
val curseForgeRelations: String by project
val releaseType: String by project

val generateChangelog: String by project

val customMavenPublishUrl: String by project
val mavenArtifactGroup: String by project

val enableSpotless: String by project
val enableJUnit: String by project

val enableJvmdg: String by project
val jvmdgShadowPath: String by project

val useLwjgl3ify: String by project

val modPath: String = modGroup.replace('.', '/')
val defaultShadowPath = "${modPath}/shadow"

val shadowDeps: Boolean = useShadowDeps.toBoolean()
val downgrade: Boolean = enableJvmdg.toBoolean()

// Project properties
group = modGroup
version = modVersion

// Set the toolchain version to decouple the Java we run Gradle with from the Java used to compile and run the mod
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        // Azul covers the most platforms for Java 8 toolchains, crucially including MacOS arm64
        vendor.set(JvmVendorSpec.AZUL)
    }
    // Generate sources and Javadocs jars when building and publishing
    withSourcesJar()
    withJavadocJar()
}

// Most RFG configuration lives here, see the JavaDoc for com.gtnewhorizons.retrofuturagradle.MinecraftExtension
minecraft {
    mcVersion.set(minecraftVersion)

    // Username for client run configurations
    username.set(devUserName)

    // Automatic token injection with RetroFuturaGradle
    if (generateTags.toBoolean()) {
        tasks.injectTags.configure { outputClassName = "${modGroup}.Tags" }
        tasks.processIdeaSettings.configure { dependsOn(tasks.injectTags) }
        injectedTags.putAll(
            mapOf(
                "MODID" to modId,
                "MODNAME" to modName,
                "VERSION" to modVersion
            )
        )
    }

    // If you need the old replaceIn mechanism, prefer the injectTags task because it doesn't inject a javac plugin.
    // tagReplacementFiles.add("RfgExampleMod.java")

    // Enable assertions in the mod's package when running the client or server
    extraRunJvmArguments.add("-ea:${project.group}")

    // If needed, add extra tweaker classes like for mixins.
    // extraTweakClasses.add("org.spongepowered.asm.launch.MixinTweaker")

    // Exclude some Maven dependency groups from being automatically included in the reobfuscated runs
    groupsToExcludeFromAutoReobfMapping.addAll("com.diffplug", "com.diffplug.durian", "net.industrial-craft")
}


val toDowngrade: Provider<RegularFile> = when {
    shadowDeps -> tasks.shadowJar
    else -> tasks.jar
}.flatMap { it.archiveFile }

val toReobf: Provider<RegularFile> = when {
    downgrade -> tasks.shadeDowngradedApi
    shadowDeps -> tasks.shadowJar
    else -> tasks.jar
}.flatMap { it.archiveFile }

val runJarTask: TaskProvider<out Jar> = when {
    downgrade -> tasks.shadeDowngradedApi
    shadowDeps -> tasks.shadowJar
    else -> tasks.jar
}


if (downgrade) {

    tasks.withType<DowngradeJar>().configureEach { logLevel.set("FATAL") }
    tasks.withType<DowngradeFiles>().configureEach { logLevel.set("FATAL") }

    val resolvedShadePath = jvmdgShadowPath.ifEmpty { defaultShadowPath }

    jvmdg.apply {
        downgradeTo = JavaVersion.VERSION_1_8
        shadePath = { resolvedShadePath }
        defaultTask {
            inputFile = toDowngrade
        }
    }

//    val downgradeShadowJar = tasks.register<DowngradeJar>("downgradeShadowJar") {
//        description = "Downgrades the shadow jar to Java 8"
//        archiveClassifier.set("dev-downgraded-shadow")
//        inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
//        dependsOn(tasks.named<ShadowJar>("shadowJar"))
//        classpath = sourceSets["main"].runtimeClasspath
//    }
//
//    val shadeDowngradedShadowJar = tasks.register<ShadeJar>("shadeDowngradedShadowJar") {
//        description = "Shades JVMDowngrader runtime stubs into the downgraded shadow jar"
//        archiveClassifier.set("dev")
//        inputFile.set(downgradeShadowJar.flatMap { it.archiveFile })
//        dependsOn(downgradeShadowJar)
//    }

    val downgradeTestClasses = tasks.register<DowngradeFiles>(name = "downgradeTestClasses") {
        description = "Downgrade classes in src/tests"
        inputCollection = files(sourceSets["test"].output.classesDirs, sourceSets["api"].output.classesDirs)
        dependsOn(tasks.testClasses, tasks.apiClasses)
    }

    tasks.test {
        dependsOn(runJarTask, downgradeTestClasses)
    }
}

if (shadowDeps) {

    configurations {
        embedOnly
        shadowImplementation
    }

    configurations.implementation {
        extendsFrom(shadowImplementation)
    }

    tasks.shadowJar {
        archiveClassifier = "shadowed"

        configurations = listOf(
            embedOnly,
            shadowImplementation,
        )

        if (minimizeShadowedDependencies.toBoolean()) minimize()
        if (relocateShadowedDependencies.toBoolean()) {
            enableAutoRelocation.set(true)
            relocationPrefix.set(defaultShadowPath)
        }
    }
}

tasks.reobfJar { inputJar.set(toReobf) }

tasks.withType<RunMinecraftTask>().configureEach {
    dependsOn(runJarTask)
    classpath = classpath - layout.files(tasks.jar) + layout.files(runJarTask)
}
