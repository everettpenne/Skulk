package com.everettpenne.skulk.data.api

/**
 * Remembers what Reddit just answered, for a short while, in memory only. Going back to a feed or a post, flipping between tabs, or opening
 * the same thread twice then costs no request, which is what keeps anonymous reading inside Reddit's allowance. Pulling to refresh
 * skips it.
 */
class ResponseCache(private val maxEntries: Int = 80, private val now: () -> Long = System::currentTimeMillis) {
    private class Entry(val body: String, val expires: Long)
    private val entries = object : LinkedHashMap<String, Entry>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    @Synchronized fun get(key: String): String? {
        val e = entries[key] ?: return null
        if (e.expires <= now()) { entries.remove(key); return null }
        return e.body
    }

    @Synchronized fun put(key: String, body: String, ttlMs: Long) { entries[key] = Entry(body, now() + ttlMs) }
    @Synchronized fun clear() = entries.clear()
}
