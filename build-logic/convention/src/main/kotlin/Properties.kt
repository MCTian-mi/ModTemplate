import org.gradle.api.GradleException
import org.gradle.api.Project
import kotlin.reflect.KProperty
import kotlin.reflect.typeOf

val Project.modName: String by getter
val Project.modId: String by getter
val Project.modGroup: String by getter
val Project.modVersion: String by getter
val Project.archiveName: String by getter
val Project.versionDisplayFormat: String by getter

val Project.minecraftVersion: String by getter
val Project.devUserName: String by getter

val Project.extJavaArgs: String by getter
val Project.enableJvmdg: Boolean by getter
val Project.useLwjgl3ify: Boolean by getter

val Project.generateTags: Boolean by getter

val Project.accessTransformers: String by getter
val Project.useMixin: Boolean by getter
val Project.mixinPackage: String by getter
val Project.mixinRefmap: String by getter
val Project.coreModClass: String by getter
val Project.enableCoreModDebug: Boolean by getter
val Project.forceLoadAsMod: Boolean by getter

val Project.minimizeShadowedDependencies: Boolean by getter
val Project.relocateShadowedDependencies: Boolean by getter

val Project.separateRunDirectories: Boolean by getter

val Project.modrinthProjectId: String by getter
val Project.modrinthRelations: String by getter
val Project.curseForgeProjectId: String by getter
val Project.curseForgeRelations: String by getter
val Project.releaseType: String by getter

val Project.generateChangelog: Boolean by getter

val Project.customMavenPublishUrl: String by getter
val Project.mavenArtifactGroup: String by getter

val Project.enableSpotless: Boolean by getter
val Project.enableJUnit: Boolean by getter

val Project.jvmdgShadowPath: String by getter

val Project.deploymentDebug: Boolean by getter

val Project.modPath: String
    get() = modGroup.replace('.', '/')

val Project.defaultShadowPath: String
    get() = "${modPath}/shadow"

val Project.useCoreMod: Boolean
    get() = coreModClass.isNotEmpty()

private typealias getter = PropertyGetter

private object PropertyGetter {

    inline operator fun <reified T> getValue(thisRef: Project, property: KProperty<*>): T {
        val name = property.name
        val rawString = thisRef.providers.gradleProperty(name).orNull
            ?: throw GradleException("Could not resolve property named \"$name\" in gradle.properties!")
        return when (val type = typeOf<T>()) {
            typeOf<String>() -> rawString
            typeOf<Boolean>() -> rawString.toBoolean()
            else -> error("Unexpected property type $type")
        } as T
    }
}