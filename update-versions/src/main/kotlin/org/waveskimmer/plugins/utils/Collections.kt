package org.waveskimmer.plugins.utils

/**
 * tl;dr is to hide the imperative coding that kotlin forces on the user
 */
object Collections {

    /**
     * traverse list until a result is found
     */
    fun <T, R> List<T>.until(compute: (T) -> XOptional<R>): XOptional<R> {
        for (t in this) {
            compute(t).let { if (it.present) return it }
        }
        return Empty
    }
}
