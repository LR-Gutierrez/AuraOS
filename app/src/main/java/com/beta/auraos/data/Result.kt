package com.beta.auraos.data

/**
 * Wrapper for async operation outcomes throughout the data layer.
 *
 * Eliminates try-catch boilerplate by modeling success and failure as
 * explicit types. The ViewModel pattern-matches on these to derive
 * the corresponding UI state.
 *
 * @param T The type of the successful value.
 */
sealed class Result<out T : Any> {

    data class Success<out T : Any>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[exception=$exception]"
        }
    }
}