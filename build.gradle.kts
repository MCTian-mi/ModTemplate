plugins {
    alias(conventions.plugins.minecraft)
    alias(conventions.plugins.shadow)
    alias(conventions.plugins.jvmdg)
    alias(conventions.plugins.idea)
    alias(conventions.plugins.test)
    alias(conventions.plugins.jvm)
}

dependencies {
    shadowDowngrade(deps.guava)
    shadowDowngrade(deps.pcollections)
}