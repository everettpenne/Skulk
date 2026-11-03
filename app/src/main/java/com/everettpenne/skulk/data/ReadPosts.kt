package com.everettpenne.skulk.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The posts the person has opened, kept on this phone only (never sent anywhere), so a feed can show which ones it has already read: their titles go
 * a little quieter, as in other readers. The most recent [MAX] are remembered; older ones fall off, which only means an old post looks unread again.
 */
class ReadPosts(context: Context) {
    private val prefs = context.getSharedPreferences("skulk_read", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(ReadPostsRules.decode(prefs.getString("ids", null)))
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun mark(id: String) {
        if (id in _ids.value) return
        val next = ReadPostsRules.add(_ids.value, id, MAX)
        _ids.value = next
        prefs.edit().putString("ids", ReadPostsRules.encode(next)).apply()
    }

    fun clear() { _ids.value = emptySet(); prefs.edit().remove("ids").apply() }

    private companion object { const val MAX = 2000 }
}

/** The rules behind [ReadPosts], pure so they are unit-tested: an insertion-ordered set capped at a size, stored as space-separated ids. */
object ReadPostsRules {
    fun add(current: Set<String>, id: String, max: Int): Set<String> {
        val next = LinkedHashSet(current); next.remove(id); next.add(id)
        while (next.size > max) next.remove(next.first())
        return next
    }
    fun encode(ids: Set<String>) = ids.joinToString(" ")
    fun decode(raw: String?): Set<String> = raw.orEmpty().split(' ').filter { it.isNotBlank() }.toCollection(LinkedHashSet())
}
