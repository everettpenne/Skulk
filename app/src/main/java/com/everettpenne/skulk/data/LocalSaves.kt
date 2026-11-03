package com.everettpenne.skulk.data

import android.content.Context
import com.everettpenne.skulk.data.model.Post
import com.everettpenne.skulk.data.model.RedditJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer

/** Reads and writes a list of saved posts as JSON; kept apart from storage so it is unit-tested. */
object SavedCodec {
    private val serializer = ListSerializer(Post.serializer())
    fun encode(posts: List<Post>): String = RedditJson.encodeToString(serializer, posts)
    fun decode(raw: String?): List<Post> = runCatching { RedditJson.decodeFromString(serializer, raw ?: return emptyList()) }.getOrDefault(emptyList())
}

/**
 * Posts saved on this phone. Skulk has no account, so the bookmark is this list: whole posts are kept, so they can be shown with
 * no network. Newest first, capped so the preferences file stays small.
 */
class LocalSaves(context: Context) {
    private val prefs = context.getSharedPreferences("skulk_saved", Context.MODE_PRIVATE)
    private val _posts = MutableStateFlow(SavedCodec.decode(prefs.getString("posts", null)))
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    fun isSaved(id: String) = _posts.value.any { it.id == id }

    /** Saves the post, or removes it if it was saved. Returns whether it is saved now. */
    fun toggle(post: Post): Boolean {
        val now = if (isSaved(post.id)) _posts.value.filter { it.id != post.id } else (listOf(post.copy(saved = true)) + _posts.value).take(MAX)
        _posts.value = now
        prefs.edit().putString("posts", SavedCodec.encode(now)).apply()
        return isSaved(post.id)
    }

    private companion object { const val MAX = 500 }
}
