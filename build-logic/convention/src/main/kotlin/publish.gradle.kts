import com.modrinth.minotaur.dependencies.container.NamedDependencyContainer
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import net.darkhax.curseforgegradle.Constants as CurseForge

plugins {
    `maven-publish`
    alias(libs.plugins.retrofuturaGradle)
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

// MOD_VERSION (set by CI) overrides everything; otherwise fall back to the 'modVersion'
// property, then to the git-derived version.
val resolvedModVersion: String =
    env("MOD_VERSION").orNull?.takeIf(String::isNotBlank)
        ?: modVersion.ifBlank { gitVersion.get() }

group = modGroup
version = resolvedModVersion

// --- Shared deployment values -------------------------------------------------
val releaseName: String =
    versionDisplayFormat.replace($$"$MOD_NAME", modName).replace($$"$VERSION", resolvedModVersion)

val resolvedReleaseType: String =
    env("RELEASE_TYPE").getOrElse(releaseType)
require(resolvedReleaseType in setOf("release", "beta", "alpha")) {
    "Release type invalid! Found \"$resolvedReleaseType\", allowed: \"release\", \"beta\", \"alpha\""
}

// DEPLOYMENT_DEBUG (set by CI) overrides the 'deploymentDebug' property when present.
val resolvedDeploymentDebug: Boolean = envBool("DEPLOYMENT_DEBUG", deploymentDebug)

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

// TODO))
val generateChangelogTask = tasks.register("generateChangelog") {
    enabled = generateChangelog
    group = "publishing"
    description = "Generates a changelog from git history when possible."

    val changelogFile = layout.buildDirectory.file("CHANGELOG.md")
    outputs.file(changelogFile)

    // Resolve the changelog text at configuration time. The git lookups run through providers.exec
    // (so they're declared configuration-cache inputs and invalidate the entry when HEAD moves), and
    // the result is captured as a plain String. The doLast action below must NOT reference any Gradle
    // script object — findPreviousGitTag()/gitOrNull()/displayName all do — because the configuration
    // cache cannot serialize "Gradle script object references".
    val changelogText: String =
        if (generateChangelog) {
            val previousTag = findPreviousGitTag()
            val range = previousTag?.let { "$it..HEAD" } ?: "HEAD"
            val commits = gitOrNull(
                "log",
                "--date=format:%d %b %Y",
                "--pretty=%s - **%an** (%ad)",
                range,
            )
            when {
                commits?.isNotBlank() == true -> {
                    val prefix =
                        if (previousTag != null) "Changes since $previousTag" else "Changes in $releaseName"
                    "$prefix:\n\n*${commits.replace("\n", "\n*")}"
                }

                previousTag != null ->
                    "There have been no changes since $previousTag."

                else ->
                    "Changes in $releaseName.\n\nNo git history was available to generate a detailed changelog."
            }
        } else {
            ""
        }

    doLast {
        changelogFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(changelogText, Charsets.UTF_8)
        }
    }
}
// --- Maven publishing ---------------------------------------------------------
// Default artifact group: 'mavenArtifactGroup' if set, otherwise 'modGroup' trimmed to its
// parent package (com.myname.mymodid -> com.myname); a group without dots is kept as-is.
// TODO))
val defaultArtifactGroup: String =
    mavenArtifactGroup.ifBlank {
        modGroup.substringBeforeLast(".")
    }

// Maven
if (customMavenPublishUrl.isNotBlank() && envBool("PUBLISH_MAVEN", true)) {
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
}

// TODO))
val modrinthApiKey = env("MODRINTH_API_KEY")
val resolvedModrinthProjectId = env("MODRINTH_PROJECT_ID").orElse(modrinthProjectId).get().trim()
val shouldPublishModrinth =
    envBool("PUBLISH_MODRINTH", true) &&
            resolvedModrinthProjectId.isNotBlank() &&
            (modrinthApiKey.getOrElse("").isNotBlank() || resolvedDeploymentDebug)


// Modrinth
if (shouldPublishModrinth) {
    modrinth {
        token = modrinthApiKey.orElse("debug_token")
        projectId = resolvedModrinthProjectId
        versionName = releaseName
        versionNumber = resolvedModVersion
        versionType = resolvedReleaseType
        gameVersions = listOf(minecraftVersion)
        loaders = listOf("Forge")
        detectLoaders = false
        debugMode = resolvedDeploymentDebug
        uploadFile.set(tasks.reobfJar)
        additionalFiles = listOf(tasks.jar, tasks.named("sourcesJar"))
        changelog = readChangelog()

        modrinthRelations.takeIf { it.isNotBlank() }?.let { str ->
            str.split(";")
                .filter { it.isNotBlank() }
                .forEach {
                    val args = it.split(":", limit = 3)
                    val (type, slur) = args
                    val version = args.getOrNull(2)
                    when (type) {
                        in ModRelations.REQ -> required.of(slur, version)
                        in ModRelations.INC -> incompatible.of(slur, version)
                        in ModRelations.OPT -> optional.of(slur, version)
                        in ModRelations.EMB -> embedded.of(slur, version)
                    }
                }
        }
    }
}

tasks.modrinth {
    enabled = shouldPublishModrinth
}

// TODO))
val curseForgeApiKey = env("CURSEFORGE_API_KEY")
val resolvedCurseForgeProjectId = env("CURSEFORGE_PROJECT_ID").orElse(curseForgeProjectId).get().trim()
val shouldPublishCurseForge =
    envBool("PUBLISH_CURSEFORGE", true) &&
            resolvedCurseForgeProjectId.isNotBlank() &&
            (curseForgeApiKey.getOrElse("").isNotBlank() || resolvedDeploymentDebug)

// CurseForge
if (shouldPublishCurseForge) {
    tasks.register<TaskPublishCurseForge>("curseforge") {
        description = "Publishes mod to CurseForge"
        group = "publishing"

        disableVersionDetection()
        debugMode = resolvedDeploymentDebug
        apiToken = env("CURSEFORGE_API_KEY").orElse("debug_token")

        with(upload(resolvedCurseForgeProjectId, tasks.reobfJar)) {
            displayName = releaseName
            releaseType = resolvedReleaseType
            changelogType = CurseForge.CHANGELOG_MARKDOWN
            changelog = readChangelog()
            addModLoader("Forge")
            addGameVersion(minecraftVersion)
            withAdditionalFile(tasks.jar)
            withAdditionalFile(tasks.named("sourcesJar"))

            curseForgeRelations.takeIf { it.isNotBlank() }?.let { str ->
                str.split(";")
                    .filter { it.isNotBlank() }
                    .forEach {
                        val (type, slur) = it.split(':', limit = 2)
                        when (type) {
                            in ModRelations.REQ -> addRequirement(slur)
                            in ModRelations.INC -> addIncompatibility(slur)
                            in ModRelations.OPT -> addOptional(slur)
                            in ModRelations.EMB -> addEmbedded(slur)
                        }
                    }
            }
        }
    }
}


tasks.register("publishModRelease") {
    group = "publishing"
    description = "Publishes the mod to the configured release targets."
    dependsOn(publishTargets.map { it.task })

    // Snapshot the target names into a plain local list at configuration time; the doFirst action
    // must not capture `publishTargets` (a script-level val), or the configuration cache fails with
    // "cannot serialize Gradle script object references".
    val targetNames = publishTargets.map { it.name }
    doFirst {
        if (targetNames.isEmpty()) {
            throw GradleException(
                "No publish targets are configured. Set customMavenPublishUrl, or provide a Modrinth/CurseForge project ID with its API key (or deploymentDebug=true).",
            )
        }
        logger.lifecycle("Publishing mod release to: ${targetNames.joinToString()}")
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

// Reads a boolean from an environment variable. A blank or unset value falls back to [default],
// so CI can pass empty strings for inputs that don't apply (e.g. on tag-push events) without
// flipping a target off.
fun envBool(name: String, default: Boolean): Boolean =
    env(name).orNull?.takeIf(String::isNotBlank)?.toBoolean() ?: default

fun NamedDependencyContainer.of(slur: String, version: String? = null) {
    return if (version.isNullOrBlank()) project(slur) else version(slur, version)
}

object ModRelations {
    val REQ = listOf("req", "required", "requiredDependency")
    val OPT = listOf("opt", "optional", "optionalDependency")
    val EMB = listOf("emb", "embedded", "embeddedLibrary")
    val INC = listOf("incomp", "fail", "incompatible")
}
