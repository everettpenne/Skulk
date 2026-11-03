package com.everettpenne.skulk.data.api

/** Why a request failed, in terms a person can act on. */
enum class Failure(val message: String) {
    OFFLINE("Can't reach Reddit. Check your connection."),
    BLOCKED("Reddit refused the request. It limits anonymous reading sometimes; trying again in a moment usually helps."),
    RATE_LIMITED("Reddit says you're going too fast. Try again in a moment."),
    NOT_FOUND("That doesn't exist, or it's private."),
    BAD_RESPONSE("Reddit sent something unexpected."),
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failed(val reason: Failure, val detail: String? = null) : ApiResult<Nothing>

    val valueOrNull: T? get() = (this as? Ok)?.value
    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Failed -> this
    }
}
