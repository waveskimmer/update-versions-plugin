package org.waveskimmer.plugins.utils

/**
 * better optional
 */
sealed class XOptional<out R> {
    val present get() = this is Some

    fun getOrNull(): R? = when (this) {
        is Some -> value
        Empty -> null
    }

    fun get(): R = when (this) {
        is Some -> value
        Empty -> throw NoSuchElementException("No value present")
    }
}

object Empty : XOptional<Nothing>()
data class Some<out R>(val value: R) : XOptional<R>()

