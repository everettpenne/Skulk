package com.everettpenne.skulk

import com.everettpenne.skulk.data.api.*
import com.everettpenne.skulk.data.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RedditApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: RedditApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = RedditApi(OkHttpClient(), userAgent = { "TestAgent/1" }, baseUrl = server.url("/").toString().trimEnd('/'),
            pacer = Pacer(minGapMs = 0), cache = ResponseCache())
    }
    @After fun tearDown() { server.shutdown() }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    @Test fun postsAreRequestedWithTheAgentAndParameters() = runBlocking {
        server.enqueue(json(Fixtures.listing(Fixtures.imagePost, Fixtures.selfPost, after = "t3_next")))
        val result = api.fetchPosts(PostFeed.Popular, ListingSort.Top(TopPeriod.WEEK), after = "t3_prev", limit = 10)
        val request = server.takeRequest()
        assertEquals("/r/popular/top.json", request.requestUrl!!.encodedPath)
        val q = request.requestUrl!!
        assertEquals("10", q.queryParameter("limit")); assertEquals("1", q.queryParameter("raw_json")); assertEquals("1", q.queryParameter("sr_detail"))
        assertEquals("t3_prev", q.queryParameter("after")); assertEquals("week", q.queryParameter("t"))
        assertEquals("TestAgent/1", request.getHeader("User-Agent"))
        assertNull("anonymous reading sends no cookie", request.getHeader("Cookie"))
        val page = (result as ApiResult.Ok).value
        assertEquals(listOf("abc123", "s1"), page.posts.map { it.id }); assertEquals("t3_next", page.after)
    }

    @Test fun everyFeedHasItsOwnPath() {
        fun path(feed: PostFeed, sort: ListingSort = ListingSort.Best) = api.postsUrl(feed, sort, null, 25)?.encodedPath
        assertEquals("/best.json", path(PostFeed.Home)); assertEquals("/r/all/hot.json", path(PostFeed.All, ListingSort.Hot))
        assertEquals("/r/kotlin/new.json", path(PostFeed.OfSubreddit(Subreddit.named("kotlin")), ListingSort.New))
        assertEquals("/user/zed/submitted.json", path(PostFeed.OfUser("zed")))
        assertNull("Saved is the phone's own list; there is nothing to ask Reddit for", path(PostFeed.Saved))
        assertNull(api.postsUrl(PostFeed.Home, ListingSort.Top(TopPeriod.DAY), null, 25)!!.queryParameter("t").takeIf { false })
    }

    @Test fun httpStatusesBecomeReasons() = runBlocking {
        for ((code, reason) in listOf(401 to Failure.BLOCKED, 403 to Failure.BLOCKED, 404 to Failure.NOT_FOUND, 429 to Failure.RATE_LIMITED, 500 to Failure.BAD_RESPONSE)) {
            server.enqueue(json("{}", code))
            assertEquals(reason, (api.fetchPosts(PostFeed.All, ListingSort.Hot) as ApiResult.Failed).reason)
        }
    }

    @Test fun garbageIsABadResponseNotACrash() = runBlocking {
        server.enqueue(json("<html>blocked</html>"))
        assertEquals(Failure.BAD_RESPONSE, (api.fetchPosts(PostFeed.All, ListingSort.Hot) as ApiResult.Failed).reason)
    }

    @Test fun anUnreachableServerIsOffline() = runBlocking {
        server.shutdown()
        assertEquals(Failure.OFFLINE, (api.fetchPosts(PostFeed.All, ListingSort.Hot) as ApiResult.Failed).reason)
    }

    @Test fun commentsComeBackWithThePost() = runBlocking {
        server.enqueue(json(Fixtures.commentsResponse))
        val result = (api.fetchPostWithComments("pics", "t3_abc123", commentId = "t1_c_b", sort = CommentSort.TOP) as ApiResult.Ok).value
        val url = server.takeRequest().requestUrl!!
        assertEquals("/r/pics/comments/abc123/comment/c_b.json", url.encodedPath); assertEquals("top", url.queryParameter("sort"))
        assertEquals("50", url.queryParameter("limit")); assertEquals("8", url.queryParameter("depth"))
        assertEquals("abc123", result.post!!.id); assertEquals(4, result.comments.size)
    }

    @Test fun aSecondLookCostsNoRequestAndARefreshDoes() = runBlocking {
        server.enqueue(json(Fixtures.listing(Fixtures.imagePost)))
        api.fetchPosts(PostFeed.All, ListingSort.Hot)
        api.fetchPosts(PostFeed.All, ListingSort.Hot)
        assertEquals(1, server.requestCount)
        server.enqueue(json(Fixtures.listing(Fixtures.imagePost)))
        api.fetchPosts(PostFeed.All, ListingSort.Hot, fresh = true)
        assertEquals(2, server.requestCount)
    }

    @Test fun subredditSearchFallsBackWhenOneEndpointIsRefused() = runBlocking {
        val sub = """{"kind":"Listing","data":{"children":[{"kind":"t5","data":{"id":"2qh","name":"t5_2qh","display_name":"dune","subscribers":5}}]}}"""
        server.enqueue(json("{}", 403)); server.enqueue(json("{}", 403)); server.enqueue(json(sub))
        val found = (api.searchSubreddits("dune") as ApiResult.Ok).value
        assertEquals(listOf("/subreddits/search.json", "/search.json", "/api/subreddit_autocomplete_v2.json"), List(3) { server.takeRequest().requestUrl!!.encodedPath })
        assertEquals(1, found.size)
        server.enqueue(json("{}", 429))                                   // a non-block failure is reported at once, not retried elsewhere
        assertEquals(Failure.RATE_LIMITED, (api.searchSubreddits("dune") as ApiResult.Failed).reason); assertEquals(4, server.requestCount)
    }

    @Test fun searchUsesTheRightEndpoints() = runBlocking {
        server.enqueue(json(Fixtures.listing(Fixtures.imagePost)))
        api.searchPosts("kotlin", subreddit = "androiddev")
        val search = server.takeRequest().requestUrl!!
        assertEquals("/r/androiddev/search.json", search.encodedPath); assertEquals("true", search.queryParameter("restrict_sr")); assertEquals("link", search.queryParameter("type"))
        assertEquals(emptyList<Post>(), (api.searchPosts("   ") as ApiResult.Ok).value); assertEquals(1, server.requestCount)
    }
}

/** Closing a response or cancelling a call writes to the socket; Android kills an app that does that on its main thread. */
class RedditApiThreadingTest {
    private val callThread: Thread get() = Thread.currentThread()

    @Test fun connectionsAreReleasedOffTheCallingThreadEvenForErrorResponses() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            val ended = java.util.concurrent.CopyOnWriteArrayList<Thread>()
            val listener = object : okhttp3.EventListener() {
                override fun callEnd(call: okhttp3.Call) { ended += Thread.currentThread() }
                override fun callFailed(call: okhttp3.Call, ioe: java.io.IOException) { ended += Thread.currentThread() }
                override fun responseBodyEnd(call: okhttp3.Call, byteCount: Long) { ended += Thread.currentThread() }
            }
            val api = RedditApi(OkHttpClient.Builder().eventListener(listener).build(), userAgent = { "t" },
                baseUrl = server.url("/").toString().trimEnd('/'), pacer = Pacer(minGapMs = 0), cache = ResponseCache())
            val caller = callThread
            server.enqueue(MockResponse().setResponseCode(403).setBody("blocked page that is never read".repeat(500)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(Fixtures.listing(Fixtures.imagePost)))
            server.enqueue(MockResponse().setResponseCode(500).setBody("x".repeat(5000)))
            api.fetchPosts(PostFeed.All, ListingSort.Hot); api.fetchPosts(PostFeed.All, ListingSort.Hot)
            api.fetchPosts(PostFeed.Popular, ListingSort.Hot)
            assertTrue("every call finished somewhere", ended.isNotEmpty())
            assertTrue("calls finished on the calling thread: ${ended.map { it.name }}", ended.none { it === caller })
        } finally { server.shutdown() }
    }
}

/** The pacer keeps one request going out at a time and honours Reddit's Retry-After quiet periods. */
class PacerTest {
    @Test fun turnsAreSpacedAndInOrder() = runBlocking {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val pacer = Pacer(minGapMs = 700, now = { now }, sleep = { sleeps += it; now += it })
        pacer.turn(); pacer.turn(); pacer.turn()
        assertEquals(listOf(700L, 700L), sleeps)    // the first is let through at once, the rest wait out the gap
        assertEquals(700L, pacer.waitMs())          // one full gap remains before a fourth
    }

    @Test fun aCoolDownQuietsLaterRequests() = runBlocking {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val pacer = Pacer(minGapMs = 700, now = { now }, sleep = { sleeps += it; now += it })
        pacer.turn()
        pacer.coolDown(10_000)
        pacer.turn()
        assertEquals(listOf(10_000L), sleeps)
    }
}

/** The short-term answer memory: hits until the TTL runs out, then forgotten. */
class ResponseCacheTest {
    @Test fun entriesLiveUntilTheirTtl() {
        var now = 1_000L
        val cache = ResponseCache(now = { now })
        cache.put("k", "body", ttlMs = 100)
        assertEquals("body", cache.get("k"))
        now += 101
        assertNull(cache.get("k"))
        cache.put("a", "1", 1000); cache.clear()
        assertNull(cache.get("a"))
    }

    @Test fun theOldestFallsOffWhenFull() {
        val cache = ResponseCache(maxEntries = 2)
        cache.put("a", "1", 1000); cache.put("b", "2", 1000); cache.put("c", "3", 1000)
        assertNull(cache.get("a")); assertEquals("3", cache.get("c"))
    }
}
