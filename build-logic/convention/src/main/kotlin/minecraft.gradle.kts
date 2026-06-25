import gradle.kotlin.dsl.accessors._8fef7488b4e13413a248f4aae2811a96.sourceSets

plugins {
    alias(libs.plugins.retrofuturaGradle)
    alias(libs.plugins.ideaExt)
    alias(libs.plugins.blossom)
}

// Most RFG configuration lives here, see the Javadoc for com.gtnewhorizons.retrofuturagradle.MinecraftExtension
minecraft {
    mcVersion = minecraftVersion

    // Username for client run configurations
    username = devUserName

    // Automatic token injection with RetroFuturaGradle
    if (generateTags) {
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

    if (useLwjgl3ify) {
        mainLwjglVersion = 3
        lwjgl3Version = libs.versions.lwjgl3
    }

    extraRunJvmArguments.apply {
        // Enable assertions in the mod's package when running the client or server
        add("-ea:${project.group}")
        add("-Dterminal.jline=true")

        if (useMixin) {
            addAll(
                "-Dmixin.hotSwap=true",
                "-Dmixin.checks.interfaces=true",
                "-Dmixin.debug.export=true",
            )
        }

        if (useCoreMod) {
            add("-Dfml.coreMods.load=${modGroup}.${coreModClass}")
        }

        if (enableCoreModDebug) {
            addAll(
                "-Dlegacy.debugClassLoading=true",
                "-Dlegacy.debugClassLoadingFiner=true",
                "-Dlegacy.debugClassLoadingSave=true",
            )
        }

        if (extJavaArgs.isNotEmpty()) addAll(extJavaArgs.split(";"))
    }

    // If needed, add extra tweaker classes like for mixins.
    // extraTweakClasses.add("org.spongepowered.asm.launch.MixinTweaker")
}

// AccessTransformers
if (accessTransformers.isNotEmpty()) {
    val atFiles = accessTransformers.split(";")
        .map { file("src/main/resources/$it") }
        .onEach { if (!it.exists()) throw GradleException("Could not find accessTransformer file \"$it\"!") }

    tasks.deobfuscateMergedJarToSrg { accessTransformerFiles.from(atFiles) }
    tasks.srgifyBinpatchedJar { accessTransformerFiles.from(atFiles) }
}

sourceSets.main {
    blossom {
        resources {
            properties = mapOf(
                "mod_id" to modId,
                "mod_name" to modName,
                "mod_version" to modVersion,
                "mc_version" to minecraftVersion,
                "mod_group" to modGroup,
                "mixin_package" to mixinPackage,
                "mixin_refmap" to mixinRefmap,
                "mixin_min_version" to libs.versions.mixin,
            )
        }
    }
}

// Copy AT files to where it should be
tasks.processResources {
    rename("(.+_at.cfg)", "META-INF/$1")
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(buildMap {
            if (useCoreMod) {
                put("FMLCorePlugin", "${modGroup}.${coreModClass}")
            }
            if (useMixin || useCoreMod) {
                put("FMLCorePluginContainsFMLMod", true)
                put("ForceLoadAsMod", forceLoadedAsMod)
            }
            if (accessTransformers.isNotEmpty()) {
                put("FMLAT", accessTransformers.replace(";", " "))
            }
        })
    }
}

dependencies {
    runtimeOnly(libs.osxNarratorBlocker)
    runtimeOnly(libs.stripLatestForgeRequirements)

    if (useMixin) {
        annotationProcessor(libs.asmDebug)
        annotationProcessor(libs.guava)
        annotationProcessor(libs.gson)
        annotationProcessor(libs.mixinbooter) { isTransitive = false }
        api(libs.mixinbooter) { isTransitive = false }

        modUtils.enableMixins(libs.mixinbooter, mixinRefmap)
    } else if (forceEnableMixins) {
        runtimeOnly(libs.mixinbooter)
    }
}
