package com.everettpenne.skulk.ui.screens

import com.everettpenne.glass.GlassRefreshIndicator
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.everettpenne.skulk.Graph
import com.everettpenne.skulk.data.api.ApiResult
import com.everettpenne.skulk.data.api.Failure
import com.everettpenne.skulk.data.media.Comments
import com.everettpenne.skulk.data.model.*
import com.everettpenne.skulk.graph
import com.everettpenne.skulk.ui.components.*
import com.everettpenne.glass.*
import com.everettpenne.skulk.ui.theme.Skulk
import com.everettpenne.skulk.util.compact
import com.everettpenne.skulk.util.timeAgo
import kotlinx.coroutines.launch

/** A post and its comments. Mirrors Lurker's `PostDetailDataSource`: comments are flattened and can be collapsed. */
class PostDetailViewModel(private val graph: Graph, private val target: PostNavigation) : ViewModel() {
    var post by mutableStateOf(graph.postCache[target.postId]); private set
    private val hadInitialPost = post != null
    var allComments by mutableStateOf<List<Comment>>(emptyList()); private set
    var collapsed by mutableStateOf<Set<String>>(emptySet()); private set
    var loading by mutableStateOf(true); private set
    var failure by mutableStateOf<Failure?>(null); private set
    var sort by mutableStateOf(CommentSort.CONFIDENCE); private set
    var scrollToComment by mutableStateOf(target.commentId); private set
    private var started = false

    val visible: List<Comment> get() = Comments.applyCollapse(allComments, collapsed)

    fun start() { if (!started) { started = true; load() } }

    fun changeSort(next: CommentSort) { if (next != sort) { sort = next; allComments = emptyList(); load() } }

    fun load(resetPost: Boolean = false, fresh: Boolean = false) {
        loading = true; failure = null
        // A link may name only the post; Reddit then finds its subreddit itself.
        val subreddit = post?.subreddit?.displayName ?: target.subreddit
        val limit = graph.settings.commentLimit.value
        viewModelScope.launch {
            when (val r = graph.api.fetchPostWithComments(subreddit, post?.id ?: target.postId, sort = sort, limit = limit, fresh = fresh)) {
                is ApiResult.Ok -> {
                    r.value.post?.let { if (resetPost || !hadInitialPost) post = it }
                    allComments = r.value.comments
                }
                is ApiResult.Failed -> failure = r.reason
            }
            loading = false
        }
    }

    fun consumedScroll() { scrollToComment = null }

    fun toggleCollapse(id: String) { collapsed = if (id in collapsed) collapsed - id else collapsed + id }
}

private val depthColors = listOf(Color(0xFF0A84FF), Color(0xFF34C759), Color(0xFFFF9F0A), Color(0xFFAF52DE), Color(0xFFFF453A), Color(0xFFFF2D55), Color(0xFF64D2FF), Color(0xFF5E5CE6))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(target: PostNavigation, nav: Navigator, onBack: () -> Unit) {
    val graph = LocalContext.current.graph
    val vm: PostDetailViewModel = viewModel(key = "post-${target.postId}") { PostDetailViewModel(graph, target) }
    LaunchedEffect(vm) { vm.start() }
    val listState = rememberLazyListState()
    var sortOpen by remember { mutableStateOf(false) }
    val comments = vm.visible

    // Open on the comment a link pointed at.
    LaunchedEffect(vm.scrollToComment, comments.size) {
        val id = vm.scrollToComment ?: return@LaunchedEffect
        val index = comments.indexOfFirst { it.id == id }
        if (index >= 0) { listState.animateScrollToItem(index + 2); vm.consumedScroll() }
    }

    GlassScreen(bars = {
        GlassCenteredTopBar(vm.post?.subreddit?.displayNamePrefixed ?: "Post", onBack = onBack, modifier = Modifier.align(Alignment.TopCenter),
            subtitle = vm.post?.let { "${it.numComments.compact()} comments" } ?: "Loading…") {
            Box {
                GlassIconButton(vm.sort.icon(), "Sort comments by ${vm.sort.label}", { sortOpen = true }, tint = Skulk.accent)
                GlassDropdownMenu(sortOpen, { sortOpen = false }) {
                    CommentSort.entries.forEach { s -> GlassMenuItem(s.label, { sortOpen = false; vm.changeSort(s) }, icon = s.icon(), selected = s == vm.sort) }
                }
            }
        }
    }) { contentPadding ->
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PullToRefreshBox(isRefreshing = false, onRefresh = { vm.load(resetPost = true, fresh = true) }, modifier = Modifier.fillMaxSize(), state = pullState,
            indicator = { GlassRefreshIndicator(pullState, false, contentPadding.calculateTopPadding()) }) {
            PostDetailList(vm.post, comments, vm.collapsed, vm.loading, vm.failure, nav, listState,
                onToggle = vm::toggleCollapse, onRetry = { vm.load(resetPost = true, fresh = true) })
        }
    }
}

/**
 * The post and its comments as a list. Stateless, so it is rendered with sample data in tests: a bug in this layout (a padding that went
 * negative) only appeared once real comments were on screen. Items carry their own side margins so that the thick bar between top-level
 * comments can run the full width; padding cannot be negative, so the list itself has none.
 */
@Composable
fun PostDetailList(post: Post?, comments: List<Comment>, collapsed: Set<String>, loading: Boolean, failure: Failure?, nav: Navigator,
                   listState: androidx.compose.foundation.lazy.LazyListState, onToggle: (String) -> Unit, onRetry: () -> Unit) {
    val side = Modifier.padding(horizontal = 16.dp)
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = contentTopPadding(), bottom = contentBottomPadding())) {
        item(key = "post") {
            post?.let { PostCard(it, nav, compact = false, modifier = side.widthIn(max = 800.dp).fillMaxWidth()) }
        }
        item(key = "header") {
            Box(side) {
                when {
                    loading && comments.isEmpty() -> LoadingRow()
                    failure != null && comments.isEmpty() -> ErrorState(failure, onRetry = onRetry)
                    comments.isNotEmpty() -> Text("Comments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    !loading -> EmptyState(Icons.Rounded.ChatBubbleOutline, "No comments yet", "Nothing here yet.")
                }
            }
        }
        items(comments, key = { it.id }) { c ->
            val first = c.id == comments.first().id
            if (c.depth == 0 && !first) Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            else if (!first) Box(side.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)))
            CommentRow(c, collapsed = c.id in collapsed, onToggle = { onToggle(c.id) }, nav = nav,
                modifier = side.widthIn(max = 800.dp).animateContentSize())
        }
    }
}

@Composable
private fun CommentRow(c: Comment, collapsed: Boolean, onToggle: () -> Unit, nav: Navigator, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val colorCoded by LocalContext.current.graph.settings.colorCodedComments.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.fillMaxWidth().alpha(if (collapsed && c.hasChildren) .55f else 1f)
            .combinedClickable(onClick = { if (c.hasChildren) onToggle() }, onLongClick = { menu = true })
            .padding(top = if (c.depth == 0) 14.dp else 8.dp, bottom = 6.dp)) {
            if (c.depth > 0) {
                if (c.depth > 1) Spacer(Modifier.width(((c.depth - 1) * 12).dp))
                val barColor = if (colorCoded) depthColors[(c.depth - 1).coerceAtLeast(0) % depthColors.size] else MaterialTheme.colorScheme.outlineVariant
                Box(Modifier.width(2.dp).fillMaxHeight().background(barColor, RoundedCornerShape(1.dp)))
                Spacer(Modifier.width(9.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.author, style = MaterialTheme.typography.labelMedium, color = if (c.isSubmitter) Skulk.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { nav.openUser(c.author) })
                    if (c.isSubmitter) Badge("OP", Skulk.accent)
                    if (c.distinguished == "moderator") Badge("MOD", Skulk.green)
                    if (c.stickied) Icon(Icons.Rounded.PushPin, null, tint = Skulk.green, modifier = Modifier.size(13.dp))
                    c.flairText?.let { Badge(it, c.flairBackground?.let { b -> Color(b) } ?: MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowUpward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    Text(c.ups.compact(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timeAgo(c.created), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!collapsed) {
                    if (c.body.isNotEmpty()) MarkdownText(c.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .9f))
                    c.embeddedMedia.forEach { m -> RemoteImage(m.image.url, Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(10.dp)), m.image.aspectRatio, androidx.compose.ui.layout.ContentScale.FillWidth) }
                } else if (c.hasChildren) {
                    Text("[${c.childCount} ${if (c.childCount == 1) "reply" else "replies"}]", style = MaterialTheme.typography.bodySmall, color = Skulk.accent, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
        }
        GlassDropdownMenu(menu, { menu = false }) {
            GlassMenuItem("u/${c.author}", { menu = false; nav.openUser(c.author) }, icon = Icons.Rounded.Person)
            GlassMenuItem("Copy text", { menu = false; clipboard.setText(AnnotatedString(c.body)) }, icon = Icons.Rounded.ContentCopy)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1,
        modifier = Modifier.background(color.copy(alpha = .15f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
}
