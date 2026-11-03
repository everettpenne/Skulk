package com.everettpenne.skulk.data.media

import com.everettpenne.skulk.data.model.*
import com.everettpenne.skulk.util.parseHexColor
import com.everettpenne.skulk.util.unescapeHtml

/**
 * Works out what a post shows below its title (an image, a gallery, a video, a YouTube clip, a link card ...) from the fields Reddit
 * sends. A port of Lurker's `Post+MediaExtraction`, in the same order of preference, and pure so it is unit-tested.
 */
object MediaExtractor {
    private val videoFormats = listOf(".mov", ".mp4", ".avi", ".mkv", ".flv", ".wmv", ".mpg", ".mpeg", ".webm")
    private val imageFormats = listOf(".gif", ".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tiff", ".svg", ".ico", ".heic", ".heif")
    private val ytEmbed = Regex("""(?<=www\.youtube\.com/embed/)[^?"]*""")
    private val ytPatterns = listOf(Regex("""(?<=watch\?v=)[^&]*"""), Regex("""(?<=youtu\.be/)[^?]*"""), Regex("""(?<=embed/)[^?]*"""))

    fun extract(data: PostDto): MediaType {
        if (data.isSelf) return MediaType.None

        data.crosspostParentList?.firstOrNull()?.let { return MediaType.Repost(Posts.from(it)) }

        val url = data.url
        val domain = data.domain

        if (data.isGallery == true) {
            val items = data.galleryData?.items.orEmpty(); val meta = data.mediaMetadata
            if (meta != null) {
                val images = items.mapNotNull { galleryImage(it.mediaId, meta[it.mediaId]) }
                if (images.isNotEmpty()) return MediaType.Gallery(images)
            }
        }

        data.preview?.redditVideoPreview?.takeIf { it.hlsUrl != null }?.let { v ->
            return MediaType.Video(v.hlsUrl!!, v.width ?: 0, v.height ?: 0, bestImage(data).first)
        }
        data.media?.redditVideo?.takeIf { it.hlsUrl != null }?.let { v ->
            return MediaType.Video(v.hlsUrl!!, v.width ?: 0, v.height ?: 0, bestImage(data).first)
        }

        if (domain.contains("youtube.com") || domain.contains("youtu.be")) {
            val oembed = data.media?.oembed
            val fromHtml = oembed?.html?.let { ytEmbed.find(it)?.value }
            if (fromHtml != null && oembed.width != null && oembed.height != null) {
                val thumb = oembed.thumbnailUrl ?: bestImage(data).first
                if (thumb != null) return MediaType.YouTube(fromHtml, GalleryImage(thumb, oembed.width, oembed.height))
            }
            youTubeId(url)?.let { id ->
                return MediaType.YouTube(id, GalleryImage("https://img.youtube.com/vi/$id/maxresdefault.jpg"))
            }
        }

        if (videoFormats.any { url.endsWith(it) }) return MediaType.Video(url)

        if (url.endsWith(".gif") || url.endsWith(".gifv") || domain.contains("gfycat") || domain.contains("redgifs") ||
            (domain.contains("imgur") && (url.contains("gif") || url.contains("gifv")))) {
            val (imageUrl, size) = bestImage(data)
            // An animation plays as a looping video when there is one: Reddit's mp4 re-encoding, or Imgur's .mp4 twin of a .gifv.
            data.preview?.images?.firstOrNull()?.variants?.mp4?.source?.takeIf { it.url != null }?.let { mp4 ->
                return MediaType.Video(mp4.url!!.unescapeHtml().replace("&amp;", "&"), mp4.width ?: 0, mp4.height ?: 0, imageUrl, loops = true)
            }
            if (domain.contains("imgur") && url.endsWith(".gifv"))
                return MediaType.Video(url.removeSuffix(".gifv") + ".mp4", size?.first ?: 0, size?.second ?: 0, imageUrl, loops = true)
            if (imageUrl != null) return MediaType.Gif(GalleryImage(imageUrl, size?.first ?: 0, size?.second ?: 0))
        }

        val directImage = imageFormats.any { url.endsWith(it) } || domain.contains("i.redd.it") || domain.contains("i.imgur.com")
        if (directImage) {
            val (imageUrl, size) = bestImage(data)
            if (imageUrl != null) return MediaType.Image(GalleryImage(imageUrl, size?.first ?: 0, size?.second ?: 0))
        }

        if (!data.preview?.images.isNullOrEmpty()) {
            val (imageUrl, size) = bestImage(data)
            if (imageUrl != null && directImage) return MediaType.Image(GalleryImage(imageUrl, size?.first ?: 0, size?.second ?: 0))
        }

        if (url.isNotEmpty() && url != data.permalink) {
            return MediaType.Link(LinkMetadata(url, domain, bestThumbnail(data)))
        }
        return MediaType.None
    }

    /** The largest preview image (with the "preview." host swapped for "i." for the original), else the post's own image URL, else a thumbnail. */
    internal fun bestImage(data: PostDto): Pair<String?, Pair<Int, Int>?> {
        var imageUrl: String? = null
        var size: Pair<Int, Int>? = null
        val source = data.preview?.images?.firstOrNull()?.source
        if (source?.url != null && source.width != null && source.height != null) {
            val clean = source.url.replace("/preview.", "/i.").replace("&amp;", "&")
            if (!clean.contains("external-preview")) { imageUrl = clean; size = source.width to source.height }
        }
        if (imageUrl == null) {
            val url = data.url
            if (url.contains("i.redd.it") || url.contains("i.imgur.com") || listOf(".jpg", ".jpeg", ".png", ".webp", ".gif").any { url.endsWith(it) }) {
                imageUrl = url
                if (source?.width != null && source.height != null) size = source.width to source.height
            }
        }
        if (imageUrl == null) {
            val thumb = data.thumbnail
            if (thumb != null && thumb !in setOf("self", "default", "nsfw", "") && thumb.startsWith("http") && !thumb.contains("b.thumbs.redditmedia.com")) {
                imageUrl = thumb
            }
        }
        return imageUrl to size
    }

    private fun bestThumbnail(data: PostDto): String? {
        data.preview?.images?.firstOrNull()?.source?.url?.let { raw ->
            val clean = raw.replace("/preview.", "/i.").replace("&amp;", "&")
            if (!clean.contains("external-preview") || clean.contains("format=")) return clean
        }
        val thumb = data.thumbnail
        if (thumb != null && thumb !in setOf("self", "default", "nsfw", "") && thumb.startsWith("http")) return thumb
        return null
    }

    private fun galleryImage(mediaId: String, meta: MediaMetadataDto?): GalleryImage? {
        val ext = meta?.m?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: return null
        val size = meta.s ?: return null
        return GalleryImage("https://i.redd.it/$mediaId.$ext", size.x ?: 0, size.y ?: 0)
    }

    internal fun youTubeId(url: String): String? =
        ytPatterns.firstNotNullOfOrNull { it.find(url)?.value?.takeIf { id -> id.isNotEmpty() } }
}

/** Turns Reddit's JSON into the app's own models. */
object Posts {
    fun from(dto: PostDto): Post {
        val sub = dto.srDetail?.let(Subreddit::from) ?: Subreddit.named(dto.subreddit)
        val flairBackground = parseHexColor(dto.linkFlairBackgroundColor)
        return Post(
            id = dto.id, fullname = dto.name.ifEmpty { "t3_${dto.id}" }, title = dto.title.unescapeHtml(), author = dto.author,
            subreddit = sub, ups = dto.ups, numComments = dto.numComments, created = dto.createdUtc, permalink = dto.permalink,
            isNsfw = dto.over18 ?: false, isSelf = dto.isSelf, selftext = dto.selftext.unescapeHtml(),
            linkFlairText = dto.linkFlairText?.takeIf { it.isNotBlank() }, flairBackground = flairBackground,
            flairTextLight = flairBackground != null && dto.linkFlairTextColor == "light",
            saved = dto.saved, likes = dto.likes, media = MediaExtractor.extract(dto))
    }
}
