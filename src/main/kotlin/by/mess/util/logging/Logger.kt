package by.mess.util.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun <R : Any> R.logger(): Lazy<Logger> {
    return lazy { LoggerFactory.getLogger(getClassName(this::class.java)) }
}

fun <T : Any> getClassName(clazz: Class<T>): String {
    return clazz.name.removeSuffix("\$Companion")
}
