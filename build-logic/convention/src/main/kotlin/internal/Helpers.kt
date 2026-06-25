package internal

import groovy.lang.MissingPropertyException
import org.gradle.api.Project
import kotlin.reflect.KProperty
import kotlin.reflect.typeOf

typealias getter = PropertyGetter

object PropertyGetter {

    inline operator fun <reified T> getValue(thisRef: Project, property: KProperty<*>): T {
        val name = property.name
        val rawString = thisRef.providers.gradleProperty(name).orNull ?: throw MissingPropertyException(name)
        return when (val type = typeOf<T>()) {
            typeOf<String>() -> rawString
            typeOf<Boolean>() -> rawString.toBoolean()
            else -> error("Unexpected property type $type")
        } as T
    }
}