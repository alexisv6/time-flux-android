package com.timeflux.domain.model

/**
 * Wraps every repository result so that callers handle errors at compile-time via
 * exhaustive `when`. [Failure] subtypes are sealed — adding a new case (e.g. for v2 sync)
 * immediately surfaces all unhandled sites.
 */
sealed class Outcome<out T> {

    data class Success<out T>(val data: T) : Outcome<T>()

    sealed class Failure : Outcome<Nothing>() {

        /** An unrecoverable SQLite or driver exception. */
        data class DatabaseError(val cause: Throwable) : Failure()

        /** The requested entity does not exist (never created or soft-deleted). */
        data class NotFound(val id: String) : Failure()

        /** Caller supplied data that violates a business rule or constraint. */
        data class ValidationError(val message: String) : Failure()

        // — v2 sync placeholders — uncomment to force exhaustive `when` at every call site —
        // data class ConflictError(val entityId: String, val serverVersion: Long) : Failure()
        // data class NetworkError(val cause: Throwable) : Failure()
        // data class QuotaExceeded(val limitBytes: Long) : Failure()
    }
}

// ---- Extensions ----

/** Transform the success value; pass failures through unchanged. */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(data))
        is Outcome.Failure -> this
    }

/** Run [action] if this is a success; returns `this` for chaining. */
inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(data)
    return this
}

/** Run [action] if this is any failure; returns `this` for chaining. */
inline fun <T> Outcome<T>.onFailure(action: (Outcome.Failure) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(this)
    return this
}

/** Returns the success value or `null`. Prefer exhaustive `when` in most cases. */
val <T> Outcome<T>.dataOrNull: T?
    get() = (this as? Outcome.Success)?.data

/** Lift a `Result<T>` (typically from `runCatching`) into an `Outcome<T>`. */
fun <T> Result<T>.toOutcome(): Outcome<T> =
    fold(
        onSuccess = { Outcome.Success(it) },
        onFailure = { Outcome.Failure.DatabaseError(it) },
    )
