package com.everettpenne.skulk

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.everettpenne.skulk.data.Favorites
import com.everettpenne.skulk.data.Settings
import com.everettpenne.skulk.data.api.Pacer
import com.everettpenne.skulk.data.api.RedditApi
import com.everettpenne.skulk.data.api.ResponseCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The app's shared parts, built once: the HTTP client, the Reddit reader, and the on-device preferences and lists. */
class Graph(app: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    val settings = Settings(app)
    val favorites = Favorites(app)
    val localSaves = com.everettpenne.skulk.data.LocalSaves(app)
    val readPosts = com.everettpenne.skulk.data.ReadPosts(app)

    /** Posts the person has just seen, so a post opens at once while its comments load. Small and in memory only. */
    val postCache: MutableMap<String, com.everettpenne.skulk.data.model.Post> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, com.everettpenne.skulk.data.model.Post>(64, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.everettpenne.skulk.data.model.Post>?) = size > 120
        })

    /** Skulk names itself to Reddit: what it is, which version, and that it only reads. It does not pose as a browser. */
    private val userAgent: String = "Android:${app.packageName}:v${BuildConfig.VERSION_NAME} (read-only reader)"
    val api = RedditApi(http, userAgent = { userAgent }, pacer = Pacer(), cache = ResponseCache())

}

class SkulkApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { graph.http }))
            add(AnimatedImageDecoder.Factory())
        }.build()
}

val Context.graph: Graph get() = (applicationContext as SkulkApp).graph
