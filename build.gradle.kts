plugins {
    alias(conventions.plugins.repositories)
    alias(conventions.plugins.minecraft)
    alias(conventions.plugins.publish)
    alias(conventions.plugins.shadow)
    alias(conventions.plugins.idea)
    alias(conventions.plugins.test)
    alias(conventions.plugins.jvm)
}

dependencies {
    compileOnlyApi(deps.jspecify)
    compileOnlyApi(deps.annotations)
    testImplementation(deps.assertj.core)

    // Mixin annotation processor for compile-time @Shadow/@Inject support
    if (useMixin) {
        annotationProcessor(variantOf(libs.mixin) { classifier("processor") })
    }

    // JEI for dev testing
    modRuntimeOnly(deps.bundles.jei)
}

configurations {
    compileOnly {
        // exclude GNU trove, FastUtil is superior and still updated
        exclude(group = "net.sf.trove4j", module = "trove4j")
        // exclude javax.annotation from findbugs, JetBrains annotations are superior
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        // exclude scala as we don't use it for anything and causes import confusion
        exclude(group = "org.scala-lang")
        exclude(group = "org.scala-lang.modules")
        exclude(group = "org.scala-lang.plugins")
    }
}
