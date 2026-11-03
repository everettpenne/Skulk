package com.everettpenne.skulk.data.media

import com.everettpenne.skulk.data.model.*
import com.everettpenne.skulk.util.parseHexColor
import com.everettpenne.skulk.util.unescapeHtml
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Reads a comment listing and flattens the reply tree into one list in display order, each comment knowing its depth and descendants. */
object Comments {
    private val embedPattern = Regex("""!\[(?:gif|img)\]\(([^)]+)\)""")
    private val urlPattern = Regex("""(?<!\]\()https?://[^\s<>()\[\]]+""")
    private val imageHosts = listOf("preview.redd.it", "i.redd.it", "i.imgur.com")
    private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")
    private val gifExtensions = listOf(".gif", ".gifv")

    /** The comments (kind "t1") of a listing's children, flattened; "more" stubs are skipped. */
    fun flatten(listing: Listing<JsonObject>): List<Comment> {
        val result = ArrayList<Comment>()
        for (thing in listing.data.children) {
            if (thing.kind != "t1") continue
            visit(RedditJson.decodeFromJsonElement<CommentDto>(thing.data), result)
        }
        // A keyed list crashes on a repeated id, so a comment Reddit lists twice is kept once.
        return result.distinctBy { it.id }
    }

    private fun repliesOf(dto: CommentDto): List<CommentDto> {
        val element: JsonElement = dto.replies ?: return emptyList()
        if (element !is JsonObject) return emptyList()   // "" when there are none
        val listing = runCatching { RedditJson.decodeFromJsonElement<Listing<JsonObject>>(element) }.getOrNull() ?: return emptyList()
        return listing.data.children.filter { it.kind == "t1" }.map { RedditJson.decodeFromJsonElement<CommentDto>(it.data) }
    }

    private fun descendantIds(dto: CommentDto): List<String> =
        repliesOf(dto).flatMap { listOf(it.id) + descendantIds(it) }

    private fun visit(dto: CommentDto, out: MutableList<Comment>) {
        val ids = descendantIds(dto)
        out += toComment(dto, ids)
        repliesOf(dto).forEach { visit(it, out) }
    }

    fun toComment(dto: CommentDto, childIds: List<String> = emptyList()): Comment {
        val raw = (dto.body ?: "[deleted]").unescapeHtml()
        val bg = parseHexColor(dto.authorFlairBackgroundColor)
        return Comment(
            id = dto.id, author = dto.author ?: "[deleted]", body = stripMediaTokens(raw), created = dto.createdUtc ?: 0.0,
            score = dto.score ?: dto.ups ?: 0, ups = dto.ups ?: 0, depth = dto.depth ?: 0, parentId = dto.parentId,
            isSubmitter = dto.isSubmitter ?: false, flairText = dto.authorFlairText?.takeIf { it.isNotBlank() }, flairBackground = bg,
            distinguished = dto.distinguished, stickied = dto.stickied ?: false, likes = dto.likes,
            embeddedMedia = embeddedMedia(raw, dto.mediaMetadata.orEmpty()), childCount = childIds.size, childIds = childIds)
    }

    /** The comments to show, leaving out the descendants of every collapsed comment. */
    fun applyCollapse(comments: List<Comment>, collapsed: Set<String>): List<Comment> {
        if (collapsed.isEmpty()) return comments
        val hidden = HashSet<String>()
        for (c in comments) if (c.id in collapsed) hidden += c.childIds
        return comments.filter { it.id !in hidden }
    }

    fun embeddedMedia(body: String, metadata: Map<String, MediaMetadataDto?>): List<CommentMedia> {
        if (body.isEmpty()) return emptyList()
        val results = ArrayList<CommentMedia>(); val seen = HashSet<String>()
        fun add(m: CommentMedia?) { if (m != null && seen.add(m.image.url)) results += m }
        embedPattern.findAll(body).forEach { add(resolveToken(it.groupValues[1], metadata)) }
        urlPattern.findAll(body).forEach { add(resolveImageUrl(it.value)) }
        return results
    }

    fun stripMediaTokens(body: String): String {
        var result = embedPattern.replace(body) { m -> if (m.groupValues[1].startsWith("emote|")) m.value else "" }
        result = urlPattern.replace(result) { m -> if (isImageUrl(m.value.replace("&amp;", "&"))) "" else m.value }
        return result.trim()
    }

    private fun resolveToken(token: String, metadata: Map<String, MediaMetadataDto?>): CommentMedia? {
        if (token.startsWith("emote|")) return null
        val item = metadata[token]
        val size = item?.s
        if (item != null && size?.u != null) {
            val isGif = item.m?.contains("gif") == true
            return CommentMedia(GalleryImage(size.u.replace("&amp;", "&"), size.x ?: 0, size.y ?: 0), isGif)
        }
        if (token.startsWith("giphy|")) {
            val id = token.split('|').getOrNull(1) ?: return null
            return CommentMedia(GalleryImage("https://i.giphy.com/media/$id/giphy.gif"), true)
        }
        if (token.startsWith("http")) return resolveImageUrl(token)
        return null
    }

    private fun resolveImageUrl(raw: String): CommentMedia? {
        val clean = raw.replace("&amp;", "&")
        if (!isImageUrl(clean)) return null
        return CommentMedia(GalleryImage(clean), isGifUrl(clean))
    }

    private fun hostAndPath(url: String): Pair<String, String>? = runCatching {
        val u = java.net.URI(url); (u.host ?: "").lowercase() to (u.path ?: "").lowercase()
    }.getOrNull()

    fun isImageUrl(url: String): Boolean {
        val (host, path) = hostAndPath(url) ?: return false
        return imageHosts.any { host.contains(it) } || imageExtensions.any(path::endsWith) || gifExtensions.any(path::endsWith)
    }

    fun isGifUrl(url: String): Boolean = hostAndPath(url)?.second?.let { p -> gifExtensions.any(p::endsWith) } ?: false
}
