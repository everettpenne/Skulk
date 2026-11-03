package com.everettpenne.skulk.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The JSON Reddit sends, as plain data. Every field is optional with a default: Reddit omits, nulls and retypes fields freely
 * (`replies` is "" when empty and a listing otherwise, `edited` is false or a time), and a feed must never fail to load because
 * one post lacked a field. Turning these into the app's own models happens in [Models.kt] and [com.everettpenne.skulk.data.media].
 */
internal val RedditJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable data class Listing<T>(val kind: String? = null, val data: ListingData<T> = ListingData())
@Serializable data class ListingData<T>(val after: String? = null, val children: List<Thing<T>> = emptyList())
@Serializable data class Thing<T>(val kind: String = "", val data: T)

@Serializable data class PostDto(
    val id: String = "",
    val name: String = "",
    val title: String = "",
    val author: String = "[deleted]",
    val subreddit: String = "",
    @SerialName("subreddit_name_prefixed") val subredditNamePrefixed: String? = null,
    @SerialName("sr_detail") val srDetail: SubredditDto? = null,
    val ups: Int = 0,
    val downs: Int = 0,
    val score: Int? = null,
    @SerialName("num_comments") val numComments: Int = 0,
    @SerialName("created_utc") val createdUtc: Double = 0.0,
    val permalink: String = "",
    val url: String = "",
    val domain: String = "",
    @SerialName("is_self") val isSelf: Boolean = false,
    val selftext: String = "",
    val saved: Boolean = false,
    val likes: Boolean? = null,
    @SerialName("over_18") val over18: Boolean? = null,
    val stickied: Boolean? = null,
    val thumbnail: String? = null,
    @SerialName("link_flair_text") val linkFlairText: String? = null,
    @SerialName("link_flair_text_color") val linkFlairTextColor: String? = null,
    @SerialName("link_flair_background_color") val linkFlairBackgroundColor: String? = null,
    val preview: PreviewDto? = null,
    val media: MediaDto? = null,
    @SerialName("is_gallery") val isGallery: Boolean? = null,
    @SerialName("gallery_data") val galleryData: GalleryDataDto? = null,
    @SerialName("media_metadata") val mediaMetadata: Map<String, MediaMetadataDto?>? = null,
    @SerialName("crosspost_parent_list") val crosspostParentList: List<PostDto>? = null,
)

@Serializable data class PreviewDto(
    val images: List<PreviewImageDto>? = null,
    @SerialName("reddit_video_preview") val redditVideoPreview: VideoDto? = null,
)
@Serializable data class PreviewImageDto(val source: ImageSourceDto? = null, val variants: PreviewVariantsDto? = null)
/** Reddit's re-encodings of an animated picture; [mp4] is the same animation as a small video, which plays far better than the GIF. */
@Serializable data class PreviewVariantsDto(val mp4: PreviewVariantDto? = null)
@Serializable data class PreviewVariantDto(val source: ImageSourceDto? = null)
@Serializable data class ImageSourceDto(val url: String? = null, val width: Int? = null, val height: Int? = null)
@Serializable data class VideoDto(
    @SerialName("hls_url") val hlsUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
@Serializable data class MediaDto(
    @SerialName("reddit_video") val redditVideo: VideoDto? = null,
    val oembed: OembedDto? = null,
)
@Serializable data class OembedDto(
    val html: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
)
@Serializable data class GalleryDataDto(val items: List<GalleryItemDto> = emptyList())
@Serializable data class GalleryItemDto(@SerialName("media_id") val mediaId: String = "")
@Serializable data class MediaMetadataDto(val m: String? = null, val s: MediaSizeDto? = null, val status: String? = null)
@Serializable data class MediaSizeDto(val x: Int? = null, val y: Int? = null, val u: String? = null, val gif: String? = null)

@Serializable data class SubredditDto(
    val id: String = "",
    val name: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("display_name_prefixed") val displayNamePrefixed: String? = null,
    @SerialName("community_icon") val communityIcon: String? = null,
    @SerialName("icon_img") val iconImg: String? = null,
    @SerialName("key_color") val keyColor: String? = null,
    val subscribers: Int? = null,
    @SerialName("user_is_subscriber") val userIsSubscriber: Boolean? = null,
    @SerialName("public_description") val publicDescription: String? = null,
)

@Serializable data class CommentDto(
    val id: String = "",
    val author: String? = null,
    val body: String? = null,
    @SerialName("created_utc") val createdUtc: Double? = null,
    val score: Int? = null,
    val ups: Int? = null,
    val depth: Int? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("is_submitter") val isSubmitter: Boolean? = null,
    @SerialName("author_flair_text") val authorFlairText: String? = null,
    @SerialName("author_flair_background_color") val authorFlairBackgroundColor: String? = null,
    val distinguished: String? = null,
    val stickied: Boolean? = null,
    val likes: Boolean? = null,
    @SerialName("media_metadata") val mediaMetadata: Map<String, MediaMetadataDto?>? = null,
    /** "" when there are none, otherwise a listing of replies. */
    val replies: JsonElement? = null,
)

/** A comment listing's children are comments ("t1") and "more" stubs, so they are read as raw objects and picked apart by kind. */
typealias RawListing = Listing<JsonObject>
