package com.everettpenne.skulk.data.api

import com.everettpenne.skulk.data.media.Comments
import com.everettpenne.skulk.data.media.Posts
import com.everettpenne.skulk.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Talks to Reddit's public website JSON (`/r/<sub>/hot.json`, ...) with no account and no official API, as Lurkur does:
 * the same pages a signed-out browser reads, fetched over plain HTTPS. Requests say what they are (Skulk's own user-agent,
 * with its version and that it only reads) and carry no cookies. Skulk does not pose as a browser or as another app.
 *
 * Reddit can refuse anonymous traffic (HTTP 403) or slow it (429). Skulk's answer to that is to be a good guest, not to
 * disguise itself: one request at a time with a gap between them ([Pacer]), short-term reuse of answers it just got
 * ([ResponseCache]), honouring Retry-After, and an honest error when Reddit still says no.
 */
class RedditApi(
    private val client: OkHttpClient,
    private val userAgent: () -> String,
    private val baseUrl: String = "https://www.reddit.com",
    private val pacer: Pacer = Pacer(),
    private val cache: ResponseCache = ResponseCache(),
) {
    // ---- plumbing ---------------------------------------------------------------------------------------------------------

    private fun requestBuilder(url: okhttp3.HttpUrl): Request.Builder = Request.Builder().url(url)
        .header("User-Agent", userAgent()).header("Accept", "application/json")

    private fun url(path: String, vararg query: Pair<String, String?>): okhttp3.HttpUrl {
        val builder = (baseUrl + path).toHttpUrl().newBuilder()
        for ((k, v) in query) if (v != null) builder.addQueryParameter(k, v)
        return builder.build()
    }

    /** What a request came back with, read and released entirely on OkHttp's own thread. [retryAfterMs] is Reddit's Retry-After, if any. */
    private class Raw(val code: Int, val body: String, val retryAfterMs: Long?) { val ok: Boolean get() = code in 200..299 }

    /** Cancelling a call closes its socket, and closing a TLS socket writes to the network, which Android forbids on the main thread. */
    private val cancelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Sends [request] and reads the answer. Everything that touches the connection (reading the body, closing the response, cancelling the call)
     * happens off the calling thread: closing a response writes to the socket, and doing that on the main thread, where these suspend functions
     * are called from, killed the app with NetworkOnMainThreadException the first time an error response came back.
     */
    private suspend fun send(request: Request): Result<Raw> = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { cancelScope.launch { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resume(Result.failure(e)) }
            override fun onResponse(call: Call, response: Response) {
                val raw = runCatching {
                    response.use {
                        val retry = it.header("Retry-After")?.toDoubleOrNull()?.let { s -> (s * 1000).toLong() }
                        Raw(it.code, if (it.isSuccessful) it.body?.string().orEmpty() else "", retry)
                    }
                }
                if (cont.isActive) cont.resume(raw)
            }
        })
    }

    private fun failureFor(code: Int): Failure = when (code) {
        401, 403 -> Failure.BLOCKED
        404 -> Failure.NOT_FOUND
        429 -> Failure.RATE_LIMITED
        else -> Failure.BAD_RESPONSE
    }

    /**
     * GETs [url] as text. Answers are remembered for [ttlMs] so flipping between tabs or reopening a thread costs no new request;
     * [fresh] skips the memory (pull to refresh). A 429 with Retry-After quiets every later request for that long.
     */
    private suspend fun getText(url: okhttp3.HttpUrl, ttlMs: Long = 0, fresh: Boolean = false): ApiResult<String> {
        val key = url.toString()
        if (!fresh && ttlMs > 0) cache.get(key)?.let { return ApiResult.Ok(it) }
        pacer.turn()
        val raw = send(requestBuilder(url).get().build()).getOrElse { return ApiResult.Failed(Failure.OFFLINE, it.message) }
        if (raw.code == 429 && raw.retryAfterMs != null) pacer.coolDown(raw.retryAfterMs)
        if (!raw.ok) return ApiResult.Failed(failureFor(raw.code), "HTTP ${raw.code}")
        if (ttlMs > 0) cache.put(key, raw.body, ttlMs)
        return ApiResult.Ok(raw.body)
    }

    private suspend inline fun <reified T> getJson(url: okhttp3.HttpUrl, ttlMs: Long = 0, fresh: Boolean = false): ApiResult<T> {
        val text = when (val r = getText(url, ttlMs, fresh)) { is ApiResult.Ok -> r.value; is ApiResult.Failed -> return r }
        return try { ApiResult.Ok(RedditJson.decodeFromString<T>(text)) }
        catch (e: Exception) { ApiResult.Failed(Failure.BAD_RESPONSE, e.message) }
    }

    // ---- reading ----------------------------------------------------------------------------------------------------------

    /** Where a page of posts comes from. Saved is the phone's own list, so it has no URL. */
    fun postsUrl(feed: PostFeed, sort: ListingSort, after: String?, limit: Int): okhttp3.HttpUrl? {
        val path = when (feed) {
            PostFeed.Home -> "/${sort.apiPath}"
            PostFeed.Popular -> "/r/popular/${sort.apiPath}"
            PostFeed.All -> "/r/all/${sort.apiPath}"
            is PostFeed.OfSubreddit -> "/r/${feed.subreddit.displayName}/${sort.apiPath}"
            is PostFeed.OfUser -> "/user/${feed.username}/submitted"
            PostFeed.Saved -> return null
        }
        return url("$path.json", "limit" to limit.toString(), "raw_json" to "1", "sr_detail" to "1", "after" to after,
            "t" to (if (feed.canSort) sort.timeParameter else null))
    }

    suspend fun fetchPosts(feed: PostFeed, sort: ListingSort, after: String? = null, limit: Int = 25, fresh: Boolean = false): ApiResult<PostPage> {
        val url = postsUrl(feed, sort, after, limit) ?: return ApiResult.Failed(Failure.BAD_RESPONSE, "feed has no URL")
        return getJson<Listing<PostDto>>(url, FEED_TTL_MS, fresh).map { listing -> pageOf(listing) }
    }

    private fun pageOf(listing: Listing<PostDto>) =
        PostPage(listing.data.children.filter { it.kind == "t3" }.map { Posts.from(it.data) }, listing.data.after)

    suspend fun fetchPostWithComments(subreddit: String?, postId: String, commentId: String? = null, sort: CommentSort = CommentSort.CONFIDENCE,
                                      limit: Int = 50, depth: Int = 8, fresh: Boolean = false): ApiResult<CommentsResult> {
        val cleanPost = postId.removePrefix("t3_")
        var path = if (subreddit != null) "/r/$subreddit/comments/$cleanPost" else "/comments/$cleanPost"
        if (commentId != null) path += "/comment/${commentId.removePrefix("t1_")}"
        val url = url("$path.json", "sort" to sort.id, "limit" to limit.toString(), "depth" to depth.toString(), "raw_json" to "1")
        val text = when (val r = getText(url, COMMENTS_TTL_MS, fresh)) { is ApiResult.Ok -> r.value; is ApiResult.Failed -> return r }
        return try {
            val root = RedditJson.parseToJsonElement(text) as? JsonArray ?: return ApiResult.Failed(Failure.BAD_RESPONSE)
            if (root.size < 2) return ApiResult.Failed(Failure.BAD_RESPONSE)
            val postListing = RedditJson.decodeFromJsonElement<Listing<PostDto>>(root[0])
            val commentListing = RedditJson.decodeFromJsonElement<RawListing>(root[1])
            ApiResult.Ok(CommentsResult(postListing.data.children.firstOrNull()?.data?.let(Posts::from), Comments.flatten(commentListing)))
        } catch (e: Exception) { ApiResult.Failed(Failure.BAD_RESPONSE, e.message) }
    }

    /**
     * Reddit answers 403 for /subreddits/search.json specifically while leaving the rest alone, so the question is asked through
     * the endpoints Reddit's own site uses for it: the generic search with type=sr, then the search-bar autocomplete; all three
     * return the same listing of t5 things. Any non-block failure (offline, rate limit) is reported as is, and only if every
     * endpoint is refused does the person see "refused".
     */
    suspend fun searchSubreddits(query: String, limit: Int = 30): ApiResult<List<Subreddit>> {
        if (query.isBlank()) return ApiResult.Ok(emptyList())
        val attempts = listOf(
            url("/subreddits/search.json", "q" to query, "limit" to limit.toString(), "sort" to "relevance", "raw_json" to "1"),
            url("/search.json", "q" to query, "type" to "sr", "limit" to limit.toString(), "sort" to "relevance", "raw_json" to "1"),
            url("/api/subreddit_autocomplete_v2.json", "query" to query, "limit" to limit.toString(), "include_over_18" to "true",
                "include_profiles" to "false", "raw_json" to "1"),
        )
        var last: ApiResult.Failed = ApiResult.Failed(Failure.BAD_RESPONSE)
        for (attempt in attempts) {
            when (val r = getJson<Listing<SubredditDto>>(attempt, SEARCH_TTL_MS)) {
                is ApiResult.Ok -> return ApiResult.Ok(r.value.data.children.filter { it.kind == "t5" }.map { Subreddit.from(it.data) })
                is ApiResult.Failed -> { last = r; if (r.reason != Failure.BLOCKED) return r }
            }
        }
        return last
    }

    suspend fun searchPosts(query: String, subreddit: String? = null, limit: Int = 30): ApiResult<List<Post>> {
        if (query.isBlank()) return ApiResult.Ok(emptyList())
        val path = if (subreddit != null) "/r/$subreddit/search.json" else "/search.json"
        return getJson<Listing<PostDto>>(url(path, "q" to query, "limit" to limit.toString(), "sort" to "relevance", "type" to "link",
            "raw_json" to "1", "sr_detail" to "1", "restrict_sr" to (if (subreddit != null) "true" else null)), SEARCH_TTL_MS).map { pageOf(it).posts }
    }

    suspend fun fetchSubredditAbout(name: String): ApiResult<Subreddit> =
        getJson<Thing<SubredditDto>>(url("/r/$name/about.json", "raw_json" to "1"), ABOUT_TTL_MS).map { Subreddit.from(it.data) }

    private companion object {
        const val FEED_TTL_MS = 45_000L
        const val COMMENTS_TTL_MS = 20_000L
        const val SEARCH_TTL_MS = 120_000L
        const val ABOUT_TTL_MS = 600_000L
    }
}
