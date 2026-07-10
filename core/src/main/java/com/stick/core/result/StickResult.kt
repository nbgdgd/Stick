package com.stick.core.result

/**
 * A lightweight, allocation-friendly result type used across module boundaries
 * so that callers never have to catch provider-specific exceptions.
 */
sealed interface StickResult<out T> {
    data class Success<T>(val value: T) : StickResult<T>
    data class Failure(val error: StickError) : StickResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    companion object {
        inline fun <T> runCatching(block: () -> T): StickResult<T> = try {
            Success(block())
        } catch (t: Throwable) {
            Failure(StickError.from(t))
        }
    }
}

// Transform helpers are top-level extensions so they can stay `inline` — inline is
// not permitted on interface members.

inline fun <T, R> StickResult<T>.map(transform: (T) -> R): StickResult<R> = when (this) {
    is StickResult.Success -> StickResult.Success(transform(value))
    is StickResult.Failure -> this
}

inline fun <T> StickResult<T>.onSuccess(block: (T) -> Unit): StickResult<T> {
    if (this is StickResult.Success) block(value)
    return this
}

inline fun <T> StickResult<T>.onFailure(block: (StickError) -> Unit): StickResult<T> {
    if (this is StickResult.Failure) block(error)
    return this
}

/** Normalised error taxonomy shared by every layer. */
sealed class StickError(open val message: String, open val cause: Throwable? = null) {
    data class Network(override val message: String, override val cause: Throwable? = null) : StickError(message, cause)
    data class NotFound(override val message: String) : StickError(message)
    data class Parsing(override val message: String, override val cause: Throwable? = null) : StickError(message, cause)
    data class Unsupported(override val message: String) : StickError(message)
    data class Conversion(override val message: String, override val cause: Throwable? = null) : StickError(message, cause)
    data class Unknown(override val message: String, override val cause: Throwable? = null) : StickError(message, cause)

    companion object {
        fun from(t: Throwable): StickError = when (t) {
            is java.io.IOException -> Network(t.message ?: "Network error", t)
            else -> Unknown(t.message ?: t.javaClass.simpleName, t)
        }
    }
}
