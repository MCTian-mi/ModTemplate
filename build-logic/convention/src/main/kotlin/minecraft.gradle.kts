plugins {
    alias(libs.plugins.retrofuturaGradle)
    alias(libs.plugins.ideaExt)
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

    // Enable assertions in the mod's package when running the client or server
    extraRunJvmArguments.add("-ea:${project.group}")

    // If needed, add extra tweaker classes like for mixins.
    // extraTweakClasses.add("org.spongepowered.asm.launch.MixinTweaker")

    // Exclude some Maven dependency groups from being automatically included in the reobfuscated runs
    groupsToExcludeFromAutoReobfMapping.addAll("com.diffplug", "com.diffplug.durian", "net.industrial-craft")
}