import com.modrinth.minotaur.dependencies.Dependency
import com.modrinth.minotaur.dependencies.ModDependency
import com.modrinth.minotaur.dependencies.VersionDependency
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import org.shipkit.changelog.GenerateChangelogTask

plugins {
    `maven-publish`
    alias(libs.plugins.shipkitChangelog)
    alias(libs.plugins.curseforgeGradle)
    alias(libs.plugins.minotaur)
}

val gitVersion: Provider<String> = providers.exec {
    commandLine(
        "git",
        "describe",
        "--tags",
        "--always",
        "--first-parent",
        "--abbrev=7",
        "--dirty=.dirty",
        "--match=*",
        "HEAD"
    )
    workingDir(rootDir)
    isIgnoreExitValue = true
}.standardOutput.asText.map { out ->
    out.trim().ifBlank { "NO_GIT_VERSION" }
}.orElse("NO_GIT_VERSION")

val resolvedModVersion: String = modVersion.ifBlank { gitVersion.get() }

group = modGroup
version = resolvedModVersion

// --- Shared deployment values -------------------------------------------------
val displayName: String =
    versionDisplayFormat.replace($$"$MOD_NAME", modName).replace($$"$VERSION", resolvedModVersion)

val resolvedReleaseType: String =
    providers.environmentVariable("RELEASE_TYPE").orElse(releaseType).get()
require(resolvedReleaseType in setOf("release", "beta", "alpha")) {
    "Release type invalid! Found \"$resolvedReleaseType\", allowed: \"release\", \"beta\", \"alpha\""
}

// --- Changelog ----------------------------------------------------------------
// Resolve which file holds the changelog Markdown:
//   1. CHANGELOG_LOCATION env var, if set (highest precedence).
//   2. The generated build/changelog.md, when 'generateChangelog' is enabled.
//   3. A CHANGELOG.md at the project root, otherwise.
fun resolveChangelogFile(): File {
    providers.environmentVariable("CHANGELOG_LOCATION").orNull?.let { return file(it) }
    if (generateChangelog) {
        return layout.buildDirectory
            .file("CHANGELOG.md")
            .get()
            .asFile
    }
    return file("CHANGELOG.md")
}

fun readChangelog(): String {
    val f = resolveChangelogFile()
    return if (f.exists()) f.readText(Charsets.UTF_8) else ""
}

// Generate a default changelog of all commits since the last tagged git commit using Shipkit.
val generateChangelogTask =
    tasks.named("generateChangelog", GenerateChangelogTask::class.java) {
        group = "publishing"
        description = "Generate a default changelog of all commits since the last tagged git commit"
        onlyIf { generateChangelog }

        val repo = providers.environmentVariable("GITHUB_REPOSITORY").orElse("MCTian-mi/ModTemplate").get()
        val token = providers.environmentVariable("GITHUB_TOKEN").orElse("").get()
        val sha = providers.environmentVariable("GITHUB_SHA").orElse("HEAD").get()

        repository = repo
        githubToken = token
        revision = sha
        version = resolvedModVersion
        releaseTag = "v$resolvedModVersion"
        previousRevision =
            runCatching {
                val proc =
                    ProcessBuilder("git", "describe", "--abbrev=0", "--tags")
                        .directory(rootDir)
                        .redirectErrorStream(false)
                        .start()
                proc.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
                    .also { proc.waitFor() }
            }.getOrDefault("")

        outputFile =
            layout.buildDirectory
                .file("changelog.md")
                .get()
                .asFile
    }
// --- Maven publishing ---------------------------------------------------------
// Default artifact group: 'mavenArtifactGroup' if set, otherwise 'modGroup' trimmed to its
// parent package (com.myname.mymodid -> com.myname); a group without dots is kept as-is.
val defaultArtifactGroup: String =
    mavenArtifactGroup.ifBlank {
        modGroup.substringBeforeLast('.', modGroup)
    }

// Tracks the release tasks that are actually configured, so 'publishModRelease' can depend
// only on the targets available in this environment.
val releaseTasks = mutableListOf<Any>()

// Only wire up a remote Maven repository when a target URL is provided. 'components["java"]'
// already carries the sources jar because jvm.gradle.kts calls withSourcesJar().
if (customMavenPublishUrl.isNotBlank()) {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                groupId = providers.environmentVariable("ARTIFACT_GROUP_ID").orElse(defaultArtifactGroup).get()
                artifactId = providers.environmentVariable("ARTIFACT_ID").orElse(archiveName).get()
                version = providers.environmentVariable("RELEASE_VERSION").orElse(resolvedModVersion).get()
            }
        }
        repositories {
            maven {
                url = uri(customMavenPublishUrl)
                isAllowInsecureProtocol = !customMavenPublishUrl.startsWith("https")
                credentials {
                    username = providers.environmentVariable("MAVEN_USER").orElse("NONE").get()
                    password = providers.environmentVariable("MAVEN_PASSWORD").orElse("NONE").get()
                }
            }
        }
    }
    releaseTasks.add(tasks.named("publish"))
}

// --- Modrinth -----------------------------------------------------------------
val modrinthApiKey = providers.environmentVariable("MODRINTH_API_KEY")

// Build a Modrinth Dependency from the 'scope-type:name' relation syntax used in gradle.properties.
fun parseModrinthRelation(entry: String): Dependency {
    val parts = entry.split(":")
    require(parts.size == 2) { "Invalid Modrinth relation \"$entry\", expected 'scope-type:name'" }
    val qualifier = parts[0].split("-")
    var scope = qualifier[0]
    var type = if (qualifier.size > 1) qualifier[1] else "project"
    val name = parts[1]

    scope =
        when (scope) {
            "req" -> "required"
            "opt" -> "optional"
            "embed" -> "embedded"
            "incomp", "fail" -> "incompatible"
            else -> scope
        }
    require(scope in setOf("required", "optional", "incompatible", "embedded")) {
        "Invalid Modrinth dependency scope: $scope"
    }
    type =
        when (type) {
            "proj", "", "p" -> "project"
            "ver", "v" -> "version"
            else -> type
        }
    return when (type) {
        "project" -> ModDependency(name, scope)
        "version" -> VersionDependency(name, scope)
        else -> throw GradleException("Invalid Modrinth dependency type: $type")
    }
}

if (modrinthApiKey.isPresent || deploymentDebug) {
    modrinth {
        token.set(modrinthApiKey.orElse("debug_token"))
        projectId.set(modrinthProjectId)
        versionName.set(displayName)
        versionNumber.set(resolvedModVersion)
        versionType.set(resolvedReleaseType)
        gameVersions.set(listOf(minecraftVersion))
        loaders.set(listOf("forge"))
        detectLoaders.set(false)
        debugMode.set(deploymentDebug)
        uploadFile.set(tasks.named("reobfJar"))
        additionalFiles.add(tasks.named("sourcesJar"))
        changelog.set(provider { readChangelog() })

        if (modrinthRelations.isNotBlank()) {
            dependencies.set(
                modrinthRelations
                    .split(";")
                    .filter { it.isNotBlank() }
                    .map { parseModrinthRelation(it) },
            )
        }
    }
    tasks.named("modrinth") {
        dependsOn(tasks.named("build"), generateChangelogTask)
    }
    releaseTasks.add(tasks.named("modrinth"))
}

// --- CurseForge ---------------------------------------------------------------
val curseForgeApiKey = providers.environmentVariable("CURSEFORGE_API_KEY")

// Normalize a CurseForge relation type from the 'type:name' syntax used in gradle.properties.
fun normalizeCurseForgeType(raw: String): String {
    val type =
        when (raw) {
            "req", "required" -> "requiredDependency"
            "opt", "optional" -> "optionalDependency"
            "embed", "embedded" -> "embeddedLibrary"
            "incomp", "fail" -> "incompatible"
            else -> raw
        }
    require(
        type in setOf("requiredDependency", "embeddedLibrary", "optionalDependency", "tool", "incompatible"),
    ) { "Invalid CurseForge dependency type: $type" }
    return type
}

if (curseForgeApiKey.isPresent || deploymentDebug) {
    val curseforge =
        tasks.register<TaskPublishCurseForge>("curseforge") {
            group = "publishing"
            disableVersionDetection()
            debugMode = deploymentDebug
            apiToken = curseForgeApiKey.orElse("debug_token").get()

            doFirst {
                val changelogRaw = readChangelog()
                val mainFile = upload(curseForgeProjectId, tasks.named("reobfJar").get())
                mainFile.displayName = displayName
                mainFile.releaseType = resolvedReleaseType
                mainFile.changelog = changelogRaw
                mainFile.changelogType = "markdown"
                mainFile.addModLoader("Forge")
                mainFile.addJavaVersion("Java 8")
                mainFile.addGameVersion(minecraftVersion)

                if (curseForgeRelations.isNotBlank()) {
                    curseForgeRelations
                        .split(";")
                        .filter { it.isNotBlank() }
                        .forEach { dep ->
                            val parts = dep.split(":")
                            require(parts.size == 2) {
                                "Invalid CurseForge relation \"$dep\", expected 'type:name'"
                            }
                            mainFile.addRelation(parts[1], normalizeCurseForgeType(parts[0]))
                        }
                }

                val sources = tasks.named("sourcesJar").get()
                mainFile.withAdditionalFile(sources).apply {
                    changelog = changelogRaw
                }
            }
        }
    curseforge.configure {
        dependsOn(tasks.named("build"), generateChangelogTask)
    }
    releaseTasks.add(curseforge)
}

// --- Aggregate lifecycle ------------------------------------------------------
// Convenience task that publishes to every target configured in this environment.
tasks.register("publishModRelease") {
    group = "publishing"
    description = "Publishes the mod release to all configured targets (Maven, Modrinth, CurseForge)"
    dependsOn(releaseTasks)
}

// Prints just the resolved mod version, for scripting/CI (e.g. computing a release tag).
tasks.register("printModVersion") {
    group = "publishing"
    description = "Prints the resolved mod version to standard out"
    val v = resolvedModVersion
    doLast { println(v) }
}
