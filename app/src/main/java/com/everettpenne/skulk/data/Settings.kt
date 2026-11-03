package com.everettpenne.skulk.data

import android.content.Context
import com.everettpenne.skulk.data.model.ListingSort
import com.everettpenne.skulk.data.model.PostFeed
import com.everettpenne.skulk.data.model.Subreddit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's own dark/light choice, as Lurkur's Color Scheme setting: follow the phone, or pin one. */
enum class ColorSchemeSetting(val id: String, val label: String) {
    DEVICE("device", "Device default"), DARK("dark", "Dark"), LIGHT("light", "Light");

    /** null means "whatever the phone is doing". */
    val dark: Boolean? get() = when (this) { DEVICE -> null; DARK -> true; LIGHT -> false }

    companion object { fun from(id: String?): ColorSchemeSetting = entries.firstOrNull { it.id == id } ?: DEVICE }
}

/** The app's preferences, as flows the screens watch. The reader switches Lurkur has, plus a few for the feeds. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("skulk_settings", Context.MODE_PRIVATE)

    private fun flag(key: String, default: Boolean): MutableStateFlow<Boolean> = MutableStateFlow(prefs.getBoolean(key, default))
    private fun set(key: String, flow: MutableStateFlow<Boolean>, value: Boolean) { flow.value = value; prefs.edit().putBoolean(key, value).apply() }

    private val _persistFeed = flag("persistFeed", false)
    private val _persistSort = flag("persistSort", false)
    private val _hideFeedSwitcher = flag("hideFeedSwitcher", false)
    private val _autoplay = flag("autoplay", true)
    private val _muteOnPlay = flag("muteOnPlay", false)
    private val _blurNsfw = flag("blurNsfw", true)
    private val _linksInApp = flag("linksInApp", true)
    private val _colorCodedComments = flag("colorCodedComments", true)
    private val _commentLimit = MutableStateFlow(prefs.getInt("commentLimit", DEFAULT_COMMENT_LIMIT).coerceIn(MIN_COMMENT_LIMIT, MAX_COMMENT_LIMIT))
    private val _colorScheme = MutableStateFlow(ColorSchemeSetting.from(prefs.getString("colorScheme", null)))

    val persistFeed: StateFlow<Boolean> = _persistFeed.asStateFlow()
    val persistSort: StateFlow<Boolean> = _persistSort.asStateFlow()
    val hideFeedSwitcher: StateFlow<Boolean> = _hideFeedSwitcher.asStateFlow()
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()
    val muteOnPlay: StateFlow<Boolean> = _muteOnPlay.asStateFlow()
    val blurNsfw: StateFlow<Boolean> = _blurNsfw.asStateFlow()
    /** Replies get a colour per depth, so a thread can be followed by its edge; off draws them all the same. */
    val colorCodedComments: StateFlow<Boolean> = _colorCodedComments.asStateFlow()
    /** How many comments a thread opens with. */
    val commentLimit: StateFlow<Int> = _commentLimit.asStateFlow()
    val colorScheme: StateFlow<ColorSchemeSetting> = _colorScheme.asStateFlow()

    fun setPersistFeed(v: Boolean) = set("persistFeed", _persistFeed, v)
    fun setPersistSort(v: Boolean) = set("persistSort", _persistSort, v)
    fun setHideFeedSwitcher(v: Boolean) = set("hideFeedSwitcher", _hideFeedSwitcher, v)
    fun setAutoplay(v: Boolean) = set("autoplay", _autoplay, v)
    fun setMuteOnPlay(v: Boolean) = set("muteOnPlay", _muteOnPlay, v)
    fun setBlurNsfw(v: Boolean) = set("blurNsfw", _blurNsfw, v)
    fun setColorCodedComments(v: Boolean) = set("colorCodedComments", _colorCodedComments, v)
    fun setCommentLimit(v: Int) { val c = v.coerceIn(MIN_COMMENT_LIMIT, MAX_COMMENT_LIMIT); _commentLimit.value = c; prefs.edit().putInt("commentLimit", c).apply() }
    fun setColorScheme(v: ColorSchemeSetting) { _colorScheme.value = v; prefs.edit().putString("colorScheme", v.id).apply() }

    /** Links outside Reddit open in the suite's built-in browser over the app (on by default), or in Vanadium or the phone's browser. */
    val linksInApp: StateFlow<Boolean> = _linksInApp.asStateFlow()
    fun setLinksInApp(v: Boolean) = set("linksInApp", _linksInApp, v)

    /** The front-page feed to open with: the last one when "remember" is on, otherwise Home. */
    fun initialFeed(): PostFeed =
        if (_persistFeed.value) PostFeed.frontPage(prefs.getString("lastFeed", null)) ?: PostFeed.Home else PostFeed.Home
    fun rememberFeed(feed: PostFeed) { if (_persistFeed.value && feed.isFrontPage) prefs.edit().putString("lastFeed", feed.id).apply() }

    fun initialSort(feed: PostFeed): ListingSort {
        if (!feed.isFrontPage || !_persistSort.value) return feed.defaultSort
        val saved = ListingSort.from(prefs.getString("lastSort", null)) ?: return feed.defaultSort
        return if (saved == ListingSort.Best && !feed.supportsBestSort) feed.defaultSort else saved
    }
    fun rememberSort(feed: PostFeed, sort: ListingSort) { if (_persistSort.value && feed.isFrontPage) prefs.edit().putString("lastSort", sort.id).apply() }

    companion object {
        const val DEFAULT_COMMENT_LIMIT = 50
        const val MIN_COMMENT_LIMIT = 25
        const val MAX_COMMENT_LIMIT = 200
        /** The choices the Comment Settings row offers. */
        val commentLimitOptions = listOf(25, 50, 100, 200)
    }
}

/** Starred subreddits, kept by lower-cased name and offered in the feed switcher. */
class Favorites(context: Context) {
    private val prefs = context.getSharedPreferences("skulk_favorites", Context.MODE_PRIVATE)
    private val _names = MutableStateFlow(prefs.getStringSet("names", emptySet()).orEmpty().toSet())
    val names: StateFlow<Set<String>> = _names.asStateFlow()

    fun isFavorite(subreddit: Subreddit) = subreddit.displayName.lowercase() in _names.value
    fun toggle(subreddit: Subreddit) {
        val key = subreddit.displayName.lowercase()
        _names.value = if (key in _names.value) _names.value - key else _names.value + key
        prefs.edit().putStringSet("names", _names.value).apply()
    }
}
