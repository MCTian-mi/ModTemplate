import com.modrinth.minotaur.dependencies.Dependency
import com.modrinth.minotaur.dependencies.ModDependency
import com.modrinth.minotaur.dependencies.VersionDependency
import net.darkhax.curseforgegradle.TaskPublishCurseForge

plugins {
    `maven-publish`
    alias(libs.plugins.curseforgeGradle)
    alias(libs.plugins.minotaur)
}

val gitVersion: Provider<String> = providers.exec {
    commandLine(
        "git", "describe", "--tags", "--always",
        "--first-parent", "--abbrev=7", "--dirty=.dirty", "--match=*", "HEAD"
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
    env("RELEASE_TYPE").getOrElse(releaseType)
require(resolvedReleaseType in setOf("release", "beta", "alpha")) {
    "Release type invalid! Found \"$resolvedReleaseType\", allowed: \"release\", \"beta\", \"alpha\""
}

data class PublishTarget(val name: String, val task: TaskProvider<out Task>)

val publishTargets = mutableListOf<PublishTarget>()

// --- Changelog ----------------------------------------------------------------
// Resolve which file holds the changelog Markdown:
//   1. CHANGELOG_LOCATION env var, if set (highest precedence).
//   2. The generated changelog, when 'generateChangelog' is enabled.
//   3. CHANGELOG.md at the project root, otherwise.
fun resolveChangelogFile(): File {
    env("CHANGELOG_LOCATION").orNull?.let { return file(it) }
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

val generateChangelogTask = tasks.register("generateChangelog") {
    enabled = generateChangelog
    group = "publishing"
    description = "Generates a changelog from git history when possible."

    val changelogFile = layout.buildDirectory.file("CHANGELOG.md")
    outputs.file(changelogFile)

    doLast {
        val previousTag = findPreviousGitTag()
        val range = previousTag?.let { "$it..HEAD" } ?: "HEAD"
        val commits = gitOrNull(
            "log",
            "--date=format:%d %b %Y",
            "--pretty=%s - **%an** (%ad)",
            range,
        )
        val changelog =
            when {
                commits?.isNotBlank() == true -> {
                    val prefix =
                        if (previousTag != null) "Changes since $previousTag" else "Changes in $displayName"
                    "$prefix:\n\n*${commits.replace("\n", "\n*")}"
                }

                previousTag != null ->
                    "There have been no changes since $previousTag."

                else ->
                    "Changes in $displayName.\n\nNo git history was available to generate a detailed changelog."
            }

        changelogFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(changelog, Charsets.UTF_8)
        }
    }
}
// --- Maven publishing ---------------------------------------------------------
// Default artifact group: 'mavenArtifactGroup' if set, otherwise 'modGroup' trimmed to its
// parent package (com.myname.mymodid -> com.myname); a group without dots is kept as-is.
val defaultArtifactGroup: String =
    mavenArtifactGroup.ifBlank {
        modGroup.substringBeforeLast('.', modGroup)
    }

// Only wire up a remote Maven repository when a target URL is provided. 'components["java"]'
// already carries the sources jar because jvm.gradle.kts calls withSourcesJar().
if (customMavenPublishUrl.isNotBlank()) {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                groupId = env("ARTIFACT_GROUP_ID").getOrElse(defaultArtifactGroup)
                artifactId = env("ARTIFACT_ID").getOrElse(archiveName)
                version = env("RELEASE_VERSION").getOrElse(resolvedModVersion)
            }
        }
        repositories {
            maven {
                url = uri(customMavenPublishUrl)
                isAllowInsecureProtocol = !customMavenPublishUrl.startsWith("https")
                credentials {
                    username = env("MAVEN_USER").getOrElse("NONE")
                    password = env("MAVEN_PASSWORD").getOrElse("NONE")
                }
            }
        }
    }
    publishTargets.add(PublishTarget("Maven", tasks.named("publish")))
}

// --- Modrinth -----------------------------------------------------------------
val modrinthApiKey = env("MODRINTH_API_KEY")
val resolvedModrinthProjectId = env("MODRINTH_PROJECT_ID").orElse(modrinthProjectId).get().trim()
val shouldPublishModrinth =
    resolvedModrinthProjectId.isNotBlank() && (modrinthApiKey.getOrElse("").isNotBlank() || deploymentDebug)

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

if (shouldPublishModrinth) {
    modrinth {
        token.set(modrinthApiKey.orElse("debug_token"))
        projectId.set(resolvedModrinthProjectId)
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
    publishTargets.add(PublishTarget("Modrinth", tasks.named("modrinth")))
}

// --- CurseForge ---------------------------------------------------------------
val curseForgeApiKey = env("CURSEFORGE_API_KEY")
val resolvedCurseForgeProjectId = env("CURSEFORGE_PROJECT_ID").orElse(curseForgeProjectId).get().trim()
val shouldPublishCurseForge =
    resolvedCurseForgeProjectId.isNotBlank() && (curseForgeApiKey.getOrElse("").isNotBlank() || deploymentDebug)

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

if (shouldPublishCurseForge) {
    val curseforge =
        tasks.register<TaskPublishCurseForge>("curseforge") {
            group = "publishing"
            disableVersionDetection()
            debugMode = deploymentDebug
            apiToken = curseForgeApiKey.orElse("debug_token").get()

            doFirst {
                val changelogRaw = readChangelog()
                val mainFile = upload(resolvedCurseForgeProjectId, tasks.named("reobfJar").get())
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
    publishTargets.add(PublishTarget("CurseForge", curseforge))
}

tasks.register("publishModRelease") {
    group = "publishing"
    description = "Publishes the mod to the configured release targets."
    dependsOn(publishTargets.map { it.task })

    doFirst {
        if (publishTargets.isEmpty()) {
            throw GradleException(
                "No publish targets are configured. Set customMavenPublishUrl, or provide a Modrinth/CurseForge project ID with its API key (or deploymentDebug=true).",
            )
        }
        logger.lifecycle("Publishing mod release to: ${publishTargets.joinToString { it.name }}")
    }
}

// Prints just the resolved mod version, for scripting/CI (e.g. computing a release tag).
tasks.register("printModVersion") {
    group = "publishing"
    description = "Prints the resolved mod version to standard out"
    val v = resolvedModVersion
    doLast { println(v) }
}

fun findPreviousGitTag(): String? {
    val githubTag = env("GITHUB_TAG").orNull?.trim()?.takeIf { it.isNotBlank() }
    if (githubTag != null) {
        return gitOrNull("describe", "--abbrev=0", "--tags", "$githubTag^")
            ?: gitOrNull("describe", "--abbrev=0", "--tags", "HEAD^")
    }
    return gitOrNull("describe", "--abbrev=0", "--tags", "--first-parent", "HEAD")
}

fun gitOrNull(vararg args: String): String? {
    val output = providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
        isIgnoreExitValue = true
    }
    if (output.result.get().exitValue != 0) {
        return null
    }
    return output.standardOutput.asText.get().trim().takeIf { it.isNotBlank() }
}

fun env(name: String): Provider<String> = providers.environmentVariable(name)
