package com.everettpenne.skulk.data.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spaces requests to Reddit out. Anonymous reading has a small allowance, so Skulk asks for one thing at a time with a short gap
 * between requests, and when Reddit says to wait (a 429 with a Retry-After) every later request waits as long as it asked.
 */
class Pacer(
    private val minGapMs: Long = 700,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Mutex()
    private var nextAllowed = 0L

    /** Waits for this request's turn. Requests are served one at a time, in the order they ask. */
    suspend fun turn() = lock.withLock {
        val wait = nextAllowed - now()
        if (wait > 0) sleep(wait)
        nextAllowed = now() + minGapMs
    }

    /** Reddit asked for [ms] of quiet: no request goes out before then. */
    fun coolDown(ms: Long) { synchronized(this) { nextAllowed = maxOf(nextAllowed, now() + ms) } }

    /** How long until the next request would be let through, in ms. */
    fun waitMs(): Long = (nextAllowed - now()).coerceAtLeast(0)
}
