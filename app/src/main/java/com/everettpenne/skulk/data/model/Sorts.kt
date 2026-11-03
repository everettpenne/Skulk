package com.everettpenne.skulk.data.model

enum class TopPeriod(val id: String, val label: String) {
    HOUR("hour", "Hour"), DAY("day", "Day"), WEEK("week", "Week"), MONTH("month", "Month"), YEAR("year", "Year"), ALL("all", "All Time"),
}

/** How a feed is ordered; "top" carries its time period, as in Lurker. */
sealed class ListingSort(val id: String, val label: String, val apiPath: String, val timeParameter: String? = null) {
    data object Best : ListingSort("best", "Best", "best")
    data object Hot : ListingSort("hot", "Hot", "hot")
    data object New : ListingSort("new", "New", "new")
    data object Controversial : ListingSort("controversial", "Controversial", "controversial")
    data class Top(val period: TopPeriod) : ListingSort("top_${period.id}", period.label, "top", period.id)

    companion object {
        // Computed, not stored: these objects are nested in this class, and a list built while the class is still initialising can capture a
        // half-made (null) entry, depending on which of them the app touches first. That crashed the menu on a real phone.
        val basic: List<ListingSort> get() = listOf(Best, Hot, New, Controversial)
        val topOptions: List<ListingSort> get() = TopPeriod.entries.map { Top(it) }

        fun from(id: String?): ListingSort? = when (id) {
            "best" -> Best; "hot" -> Hot; "new" -> New; "controversial" -> Controversial
            else -> if (id != null && id.startsWith("top_")) TopPeriod.entries.firstOrNull { it.id == id.removePrefix("top_") }?.let(::Top) else null
        }
    }
}

enum class CommentSort(val id: String, val label: String) {
    CONFIDENCE("confidence", "Best"), TOP("top", "Top"), NEW("new", "New"), CONTROVERSIAL("controversial", "Controversial"),
    OLD("old", "Old"), RANDOM("random", "Random"), QA("qa", "Q&A"), LIVE("live", "Live"),
}

/** What a posts list shows. */
sealed class PostFeed(val id: String, val displayName: String) {
    data object Home : PostFeed("home", "Home")
    data object Popular : PostFeed("popular", "Popular")
    data object All : PostFeed("all", "All")
    data class OfSubreddit(val subreddit: Subreddit) : PostFeed("subreddit_${subreddit.id}", subreddit.displayNamePrefixed)
    data object Saved : PostFeed("saved", "Saved")
    data class OfUser(val username: String) : PostFeed("user_$username", "u/$username")

    val canSort: Boolean get() = this is Home || this is Popular || this is All || this is OfSubreddit
    val isFrontPage: Boolean get() = this is Home || this is Popular || this is All
    val supportsBestSort: Boolean get() = !(this is Popular || this is All)
    val supportsSearch: Boolean get() = this is OfSubreddit
    val defaultSort: ListingSort get() = if (this is Popular || this is All) ListingSort.Hot else ListingSort.Best

    companion object {
        /** Computed, not stored: the objects are nested in this class, and a stored list would be built while they are still half-made. */
        val frontPage: List<PostFeed> get() = listOf(Home, Popular, All)
        fun frontPage(id: String?): PostFeed? = frontPage.firstOrNull { it.id == id }
    }
}
