plugins {
    java
    id("repositories")
}

group = "dev.tianmi"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly(variantOf(rfbPatchLibs.lwjgl3ify) {
        classifier("forgePatches")
    }) { isTransitive = false }
    compileOnly(rfbPatchLibs.annotations)
    compileOnly(rfbPatchLibs.asm.tree)
}

// The modernJavaExtraRuntimeClasspath consumer in minecraft.gradle.kts resolves this project's
// runtimeElements with variant-aware attribute matching. RFG expects these two attributes on
// the outgoing configuration; without them Gradle fails variant selection.
configurations.named("runtimeElements") {
    attributes {
        attribute(Attribute.of("com.gtnewhorizons.retrofuturagradle.obfuscation", String::class.java), "mcp")
        attribute(Attribute.of("rfgDeobfuscatorTransformed", Boolean::class.javaObjectType), true)
    }
}
