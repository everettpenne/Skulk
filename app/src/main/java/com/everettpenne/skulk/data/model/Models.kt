package com.everettpenne.skulk.data.model

import com.everettpenne.skulk.util.compact
import com.everettpenne.skulk.util.parseHexColor
import com.everettpenne.skulk.util.timeAgo
import com.everettpenne.skulk.util.unescapeHtml
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable data class Subreddit(
    val id: String,
    val displayName: String,
    val displayNamePrefixed: String,
    val iconUrl: String? = null,
    val subscriberCount: Int = 0,
    val isSubscribed: Boolean = false,
    val publicDescription: String = "",
    val keyColor: Int? = null,
) {
    val formattedSubscribers: String get() = subscriberCount.compact()

    companion object {
        fun named(displayName: String) = Subreddit(
            id = displayName.ifEmpty { "home" }, displayName = displayName,
            displayNamePrefixed = if (displayName.isEmpty()) "Home" else "r/$displayName")

        fun from(dto: SubredditDto): Subreddit {
            val name = dto.displayName ?: dto.name
            val icon = (dto.communityIcon?.takeIf { it.isNotEmpty() } ?: dto.iconImg?.takeIf { it.isNotEmpty() })
                ?.substringBefore('?')?.unescapeHtml()
            return Subreddit(
                id = dto.id.ifEmpty { name }, displayName = name,
                displayNamePrefixed = dto.displayNamePrefixed ?: "r/$name", iconUrl = icon,
                subscriberCount = dto.subscribers ?: 0, isSubscribed = dto.userIsSubscriber ?: false,
                publicDescription = dto.publicDescription.orEmpty(), keyColor = parseHexColor(dto.keyColor))
        }
    }
}

@Serializable data class GalleryImage(val url: String, val width: Int = 0, val height: Int = 0) {
    val aspectRatio: Float? get() = if (width > 0 && height > 0) width.toFloat() / height else null
}
@Serializable data class LinkMetadata(val url: String, val domain: String, val thumbnailUrl: String? = null)

/** What a post shows below its title. */
@Serializable sealed interface MediaType {
    @Serializable data object None : MediaType
    @Serializable data class Image(val image: GalleryImage) : MediaType
    @Serializable data class Gallery(val images: List<GalleryImage>) : MediaType
    /** A video to play inline. [loops] marks an animation (a GIF shown as video): it has no sound worth a mute button. */
    @Serializable data class Video(val url: String, val width: Int = 0, val height: Int = 0, val thumbnailUrl: String? = null, val loops: Boolean = false) : MediaType
    @Serializable data class YouTube(val videoId: String, val image: GalleryImage) : MediaType
    @Serializable data class Link(val metadata: LinkMetadata) : MediaType
    @Serializable data class Gif(val image: GalleryImage) : MediaType
    @Serializable data class Repost(val original: Post) : MediaType
}

@Serializable data class Post(
    val id: String,
    val fullname: String,
    val title: String,
    val author: String,
    val subreddit: Subreddit,
    val ups: Int,
    val numComments: Int,
    val created: Double,
    val permalink: String,
    val isNsfw: Boolean,
    val isSelf: Boolean,
    val selftext: String,
    val linkFlairText: String?,
    val flairBackground: Int?,
    val flairTextLight: Boolean,
    val saved: Boolean,
    val likes: Boolean?,
    val media: MediaType,
) {
    val redditUrl: String get() = "https://reddit.com$permalink"
}

data class CommentMedia(val image: GalleryImage, val isGif: Boolean)

data class Comment(
    val id: String,
    val author: String,
    val body: String,
    val created: Double,
    val score: Int,
    val ups: Int,
    val depth: Int,
    val parentId: String?,
    val isSubmitter: Boolean,
    val flairText: String?,
    val flairBackground: Int?,
    val distinguished: String?,
    val stickied: Boolean,
    val likes: Boolean?,
    val embeddedMedia: List<CommentMedia> = emptyList(),
    val childCount: Int = 0,
    val childIds: List<String> = emptyList(),
) {
    val fullname: String get() = "t1_$id"
    val hasChildren: Boolean get() = childCount > 0
}

data class PostNavigation(val postId: String, val subreddit: String? = null, val commentId: String? = null)

data class PostPage(val posts: List<Post>, val after: String?)
data class CommentsResult(val post: Post?, val comments: List<Comment>)

internal fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
