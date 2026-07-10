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

    modRuntimeOnly(deps.bundles.jei)
    modRuntimeOnly(deps.bundles.jade)
}
