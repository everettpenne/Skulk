package com.everettpenne.skulk.ui.screens

import com.everettpenne.glass.frostedGlass
import com.everettpenne.glass.GlassRefreshIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.everettpenne.skulk.Graph
import com.everettpenne.skulk.data.api.ApiResult
import com.everettpenne.skulk.data.api.Failure
import com.everettpenne.skulk.data.model.*
import com.everettpenne.skulk.graph
import com.everettpenne.skulk.ui.components.*
import com.everettpenne.glass.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One list of posts: its feed, sort, pages and state. Mirrors Lurker's `PostListDataSource`. */
class PostsViewModel(private val graph: Graph, val feed: PostFeed) : ViewModel() {
    var sort by mutableStateOf(graph.settings.initialSort(feed)); private set
    var posts by mutableStateOf<List<Post>>(emptyList()); private set
    var after by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var failure by mutableStateOf<Failure?>(null); private set
    var query by mutableStateOf("")
    private var searching = false
    private var job: Job? = null
    private var started = false

    /** Saved is the phone's own list, kept whole on the device: no request is ever made for it. */
    val isLocalList get() = feed == PostFeed.Saved

    fun start() { if (!started) { started = true; if (!isLocalList) load(reset = true) } }

    fun changeSort(next: ListingSort) {
        if (next == sort) return
        sort = next; graph.settings.rememberSort(feed, next); load(reset = true)
    }

    /** Pull to refresh asks Reddit again, skipping the short-term answer cache. */
    fun refresh() { refreshing = true; load(reset = true, fresh = true) }
    fun loadMore() { if (!loading && after != null && !searching) load(reset = false) }

    /** Search within the subreddit; an empty query goes back to the feed. */
    fun submitSearch() {
        val q = query.trim()
        if (q.isEmpty()) { clearSearch(); return }
        job?.cancel(); searching = true; loading = true; failure = null
        job = viewModelScope.launch {
            when (val r = graph.api.searchPosts(q, subreddit = feed.subredditName())) {
                is ApiResult.Ok -> { posts = r.value.distinctBy { it.id }; after = null }
                is ApiResult.Failed -> failure = r.reason
            }
            loading = false
        }
    }

    fun clearSearch() { query = ""; if (searching) { searching = false; load(reset = true) } }

    private fun load(reset: Boolean, fresh: Boolean = false) {
        job?.cancel()
        loading = true; failure = null
        if (reset) after = null
        val cursor = if (reset) null else after
        job = viewModelScope.launch {
            when (val r = graph.api.fetchPosts(feed, sort, cursor, fresh = fresh)) {
                is ApiResult.Ok -> {
                    val seen = if (reset) emptySet() else posts.mapTo(HashSet()) { it.id }
                    posts = ((if (reset) emptyList() else posts) + r.value.posts.filter { it.id !in seen }).distinctBy { it.id }
                    after = r.value.after
                }
                is ApiResult.Failed -> failure = r.reason
            }
            loading = false; refreshing = false
        }
    }

    private fun PostFeed.subredditName() = (this as? PostFeed.OfSubreddit)?.subreddit?.displayName
}

@Composable
fun postsViewModel(feed: PostFeed): PostsViewModel {
    val graph = LocalContext.current.graph
    return viewModel(key = "posts-${feed.id}") { PostsViewModel(graph, feed) }
}

/**
 * A feed of posts under a glass top bar: pull to refresh, endless scrolling, a sort menu, and for the front pages a switcher between
 * Home, Popular, All and any starred subreddits; inside a subreddit a search field and an info button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsScreen(feed: PostFeed, nav: Navigator, onBack: (() -> Unit)? = null, onSwitchFeed: ((PostFeed) -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    val vm = postsViewModel(feed)
    LaunchedEffect(vm) { vm.start() }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; last >= vm.posts.size - 4 } }
    LaunchedEffect(nearEnd, vm.posts.size) { if (nearEnd && vm.posts.isNotEmpty()) vm.loadMore() }
    // The tab tapped again: back to the top of the list; at the top of the Feed tab, refresh.
    LaunchedEffect(vm) {
        com.everettpenne.skulk.ui.TabReselect.events.collect { tab ->
            val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40
            when (tab) {
                com.everettpenne.skulk.ui.Tab.Feed -> if (onSwitchFeed != null) { if (atTop) vm.refresh() else listState.animateScrollToItem(0) }
                com.everettpenne.skulk.ui.Tab.Saved -> if (feed == PostFeed.Saved && !atTop) listState.animateScrollToItem(0)
                else -> Unit
            }
        }
    }
    // A new sort starts at the top of its list, not part-way down the old one.
    LaunchedEffect(vm.sort) { listState.scrollToItem(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Subreddit?>(null) }
    val graph = LocalContext.current.graph
    val hideSwitcher by graph.settings.hideFeedSwitcher.collectAsState()
    val localPosts by graph.localSaves.posts.collectAsState()
    val favoriteNames by graph.favorites.names.collectAsState()
    val localList = vm.isLocalList
    val posts = if (localList) localPosts else vm.posts

    GlassScreen(bars = {
        Column(Modifier.align(Alignment.TopCenter)) {
            GlassTopBar(feed.displayName, onBack = onBack) {
                if (feed.canSort || (onSwitchFeed != null && !hideSwitcher) || feed.supportsSearch || feed is PostFeed.OfSubreddit || onSettings != null) GlassActionPill {
                    if (feed.supportsSearch) PillIcon(Icons.Rounded.Search, "Search this subreddit", { searchOpen = !searchOpen; if (!searchOpen) vm.clearSearch() })
                    if (feed.canSort) MenuPillIcon(vm.sort.icon(), "Sort by ${vm.sort.label}") { close ->
                        ListingSort.basic.filter { feed.supportsBestSort || it != ListingSort.Best }.forEach { s -> GlassMenuItem(s.label, { close(); vm.changeSort(s) }, icon = s.icon(), selected = s == vm.sort) }
                        MenuSection("Top")
                        ListingSort.topOptions.forEach { s -> GlassMenuItem("Top · ${s.label}", { close(); vm.changeSort(s) }, icon = s.icon(), selected = s == vm.sort) }
                    }
                    if (onSwitchFeed != null && !hideSwitcher) MenuPillIcon(feed.icon(), "Switch feed") { close ->
                        PostFeed.frontPage.forEach { f -> GlassMenuItem(f.displayName, { close(); onSwitchFeed(f) }, icon = f.icon(), selected = f.id == feed.id) }
                        if (favoriteNames.isNotEmpty()) {
                            MenuSection("Favorites")
                            favoriteNames.sorted().forEach { name ->
                                GlassMenuItem("r/$name", { close(); onSwitchFeed(PostFeed.OfSubreddit(Subreddit.named(name))) },
                                    icon = Icons.Rounded.Forum, selected = (feed as? PostFeed.OfSubreddit)?.subreddit?.displayName?.lowercase() == name)
                            }
                        }
                    }
                    if (feed is PostFeed.OfSubreddit) PillIcon(Icons.Rounded.Info, "About ${feed.displayName}", { sheet = feed.subreddit })
                    if (onSettings != null) PillIcon(Icons.Rounded.Settings, "Settings", onSettings)
                }
            }
            if (searchOpen) {
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                GlassField(vm.query, { vm.query = it }, "Search ${feed.displayName}", Modifier.padding(horizontal = 16.dp).focusRequester(focus), onSubmit = vm::submitSearch)
            }
        }
    }) { contentPadding ->
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { if (!localList) vm.refresh() }, modifier = Modifier.fillMaxSize(), state = pullState,
            indicator = { GlassRefreshIndicator(pullState, vm.refreshing, contentPadding.calculateTopPadding()) }) {
            LazyColumn(Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + if (searchOpen) 56.dp else 0.dp, bottom = contentPadding.calculateBottomPadding(), start = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (localList) item { Text("Saved on this device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
                itemsIndexed(posts, key = { _, p -> p.id }) { _, post -> PostCard(post, nav, compact = true, modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()) }
                item {
                    when {
                        localList && posts.isEmpty() -> EmptyState(Icons.Rounded.BookmarkBorder, "Nothing saved yet", "Posts you save with the bookmark stay on this phone.")
                        localList -> Unit
                        vm.loading && vm.posts.isNotEmpty() -> LoadingRow()
                        vm.failure != null -> ErrorState(vm.failure!!, onRetry = { if (vm.posts.isEmpty()) vm.refresh() else vm.loadMore() })
                        !vm.loading && vm.posts.isEmpty() -> EmptyState(Icons.Rounded.Inbox, "Nothing here", "No posts to show.")
                    }
                }
            }
            if (vm.loading && vm.posts.isEmpty() && vm.failure == null && !localList)
                Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding() + 8.dp, start = 12.dp, end = 12.dp)) { PostSkeleton(3) }
        }
    }
    sheet?.let { SubredditInfoSheet(it) { sheet = null } }
}

/** A [PillIcon] that opens a glass menu when tapped; [content] builds the rows and is handed a function that closes the menu. */
@Composable
fun MenuPillIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillIcon(icon, description, { open = true })
        GlassDropdownMenu(open, { open = false }) { content { open = false } }
    }
}

fun ListingSort.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    ListingSort.Best -> Icons.Rounded.EmojiEvents
    ListingSort.Hot -> Icons.Rounded.LocalFireDepartment
    ListingSort.New -> Icons.Rounded.Newspaper
    ListingSort.Controversial -> Icons.Rounded.SportsMartialArts
    is ListingSort.Top -> when (period) {
        TopPeriod.HOUR -> Icons.Rounded.Schedule; TopPeriod.DAY -> Icons.Rounded.WbSunny; TopPeriod.WEEK -> Icons.Rounded.DateRange
        TopPeriod.MONTH -> Icons.Rounded.CalendarMonth; TopPeriod.YEAR -> Icons.Rounded.Public; TopPeriod.ALL -> Icons.Rounded.AllInclusive
    }
}

fun PostFeed.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    PostFeed.Home -> Icons.Rounded.Home
    PostFeed.Popular -> Icons.Rounded.TrendingUp
    PostFeed.All -> Icons.Rounded.Public
    is PostFeed.OfSubreddit -> Icons.Rounded.Forum
    PostFeed.Saved -> Icons.Rounded.Bookmark
    is PostFeed.OfUser -> Icons.Rounded.Person
}

fun CommentSort.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    CommentSort.CONFIDENCE -> Icons.Rounded.LocalFireDepartment; CommentSort.TOP -> Icons.Rounded.EmojiEvents; CommentSort.NEW -> Icons.Rounded.Newspaper
    CommentSort.CONTROVERSIAL -> Icons.Rounded.SportsMartialArts; CommentSort.OLD -> Icons.Rounded.History; CommentSort.RANDOM -> Icons.Rounded.Casino
    CommentSort.QA -> Icons.Rounded.QuestionAnswer; CommentSort.LIVE -> Icons.Rounded.Sensors
}

@Composable
fun LoadingRow() = com.everettpenne.glass.GlassSkeleton(count = 3, rowHeight = 52.dp, modifier = Modifier.padding(horizontal = 4.dp))

@Composable
fun ErrorState(failure: Failure, onRetry: () -> Unit) =
    com.everettpenne.glass.GlassErrorState("Couldn't load", failure.message, onRetry, retryLabel = "Try again")

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) =
    com.everettpenne.glass.GlassEmptyState(icon, title, message)

/** A quiet section label inside a glass menu, so grouped rows read as a group rather than a flat list. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MenuSection(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 2.dp))
}

/** A glass search field. */
@Composable
fun GlassField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSubmit: () -> Unit = {}) {
    val ink = glassPalette().ink
    androidx.compose.foundation.text.BasicTextField(value, onValueChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink), cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier.fillMaxWidth().height(48.dp).frostedGlass(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50), lensHeight = 10f, lensAmount = 20f),
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Search, null, tint = ink.copy(alpha = .6f), modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(placeholder, color = ink.copy(alpha = .45f), style = MaterialTheme.typography.bodyLarge); inner() }
                if (value.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = ink.copy(alpha = .6f), modifier = Modifier.size(20.dp).clickable { onValueChange("") })
            }
        })
}
