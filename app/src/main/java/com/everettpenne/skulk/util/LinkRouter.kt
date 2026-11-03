package com.everettpenne.skulk.util

/** Where a tapped link should go: inside the app when it is Reddit content, an image viewer for pictures, otherwise the browser. */
sealed interface LinkTarget {
    data class Post(val id: String, val subreddit: String?, val commentId: String?) : LinkTarget
    data class Subreddit(val name: String) : LinkTarget
    data class User(val name: String) : LinkTarget
    data class Image(val url: String) : LinkTarget
    data class External(val url: String) : LinkTarget
}

object LinkRouter {
    private val redditHosts = setOf("reddit.com", "www.reddit.com", "old.reddit.com", "m.reddit.com", "np.reddit.com", "new.reddit.com")
    private val imageHosts = listOf("preview.redd.it", "i.redd.it", "i.imgur.com")
    private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")

    fun parse(url: String): LinkTarget {
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return LinkTarget.External(url)
        val host = uri.host?.lowercase() ?: return LinkTarget.External(url)
        val path = (uri.path ?: "").lowercase()
        if (imageHosts.any { host == it || host.endsWith(".$it") } || imageExtensions.any { path.endsWith(it) } && host !in redditHosts) {
            if (!path.endsWith(".gif")) return LinkTarget.Image(url)
        }
        val parts = (uri.path ?: "").split('/').filter { it.isNotEmpty() }
        if (host == "redd.it" && parts.size == 1) return LinkTarget.Post(parts[0], null, null)
        if (host !in redditHosts) return LinkTarget.External(url)

        if (parts.size >= 4 && parts[0].equals("r", true) && parts[2].equals("comments", true)) {
            val comment = when {
                parts.size >= 6 && parts[4].equals("comment", true) -> parts[5]
                parts.size >= 6 -> parts[5]                      // /r/sub/comments/id/slug/commentid/
                parts.size == 5 && parts[3].isNotEmpty() && parts[4].equals("comment", true) -> null
                else -> null
            }
            return LinkTarget.Post(parts[3], parts[1], comment)
        }
        if (parts.size >= 2 && parts[0].equals("comments", true)) return LinkTarget.Post(parts[1], null, null)
        if (parts.size >= 2 && parts[0].equals("r", true)) return LinkTarget.Subreddit(parts[1])
        if (parts.size >= 2 && (parts[0].equals("u", true) || parts[0].equals("user", true))) return LinkTarget.User(parts[1])
        return LinkTarget.External(url)
    }
}
