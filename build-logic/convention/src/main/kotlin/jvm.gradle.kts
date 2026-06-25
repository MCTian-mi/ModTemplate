plugins {
    java
}

val utf8: String = Charsets.UTF_8.name()

tasks.compileJava {
    options.encoding = utf8
}

tasks.javadoc {
    isFailOnError = false
    with(options as CoreJavadocOptions) {
        quiet()
        encoding = utf8
        addStringOption("Xdoclint:none", "-quiet")
    }
}

// Set the toolchain version to decouple the Java we run Gradle with from the Java used to compile and run the mod
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        // Azul covers the most platforms for Java 8 toolchains, crucially including macOS arm64
        vendor.set(JvmVendorSpec.AZUL)
    }
    // Generate sources and Javadocs jars when building and publishing
    withSourcesJar()
    withJavadocJar()
}