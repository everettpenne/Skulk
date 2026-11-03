package com.everettpenne.skulk.ui.screens

import com.everettpenne.glass.frostedGlass
import com.everettpenne.glass.GlassScreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Close
import com.everettpenne.glass.GlassIconButton
import com.everettpenne.glass.glassSurface
import com.everettpenne.skulk.ui.theme.Skulk
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.everettpenne.skulk.Graph
import com.everettpenne.skulk.data.api.ApiResult
import com.everettpenne.skulk.data.api.Failure
import com.everettpenne.skulk.data.model.Post
import com.everettpenne.skulk.data.model.Subreddit
import com.everettpenne.skulk.graph
import com.everettpenne.skulk.ui.components.Navigator
import com.everettpenne.skulk.ui.components.PostCard
import com.everettpenne.skulk.ui.components.SubredditIcon
import com.everettpenne.glass.GlassChip
import com.everettpenne.glass.GlassTopBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class SearchScope(val label: String) { SUBREDDITS("Subreddits"), POSTS("Posts") }

class SearchViewModel(private val graph: Graph) : ViewModel() {
    var query by mutableStateOf("")
    var scope by mutableStateOf(SearchScope.SUBREDDITS); private set
    var subreddits by mutableStateOf<List<Subreddit>>(emptyList()); private set
    var posts by mutableStateOf<List<Post>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var failure by mutableStateOf<Failure?>(null); private set
    private var job: Job? = null

    fun changeScope(next: SearchScope) { if (next != scope) { scope = next; subreddits = emptyList(); posts = emptyList(); submit() } }

    fun submit() {
        job?.cancel()
        if (query.isBlank()) { subreddits = emptyList(); posts = emptyList(); failure = null; loading = false; return }
        loading = true; failure = null
        job = viewModelScope.launch {
            when (scope) {
                SearchScope.SUBREDDITS -> when (val r = graph.api.searchSubreddits(query.trim(), 25)) { is ApiResult.Ok -> subreddits = r.value.distinctBy { it.id }; is ApiResult.Failed -> failure = r.reason }
                SearchScope.POSTS -> when (val r = graph.api.searchPosts(query.trim(), limit = 25)) { is ApiResult.Ok -> posts = r.value.distinctBy { it.id }; is ApiResult.Failed -> failure = r.reason }
            }
            loading = false
        }
    }

    fun clear() { query = ""; subreddits = emptyList(); posts = emptyList(); failure = null }
}

/** Search subreddits or posts across Reddit. The field sits at the bottom, above the keyboard or the tab bar, as in the original. */
@Composable
fun SearchScreen(nav: Navigator) {
    val graph = LocalContext.current.graph
    val vm: SearchViewModel = viewModel { SearchViewModel(graph) }
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    GlassScreen(bars = {
        GlassTopBar("Search", modifier = Modifier.align(Alignment.TopCenter)) {
            if (vm.query.isNotEmpty() || vm.subreddits.isNotEmpty() || vm.posts.isNotEmpty()) Box(Modifier.height(48.dp).frostedGlass(RoundedCornerShape(percent = 50), lensHeight = 12f, lensAmount = 24f)
                .clickable(interactionSource = null, indication = null) { vm.clear() }.padding(horizontal = 18.dp), Alignment.Center) { Text("Clear", color = Skulk.accent, style = MaterialTheme.typography.bodyLarge) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().padding(bottom = if (imeVisible) 8.dp else 92.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchScope.entries.forEach { s -> GlassChip(vm.scope == s, { vm.changeScope(s) }) { Text(s.label) } }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassField(vm.query, { vm.query = it; if (it.isEmpty()) vm.clear() }, "Search ${vm.scope.label}", Modifier.weight(1f), onSubmit = vm::submit)
                GlassIconButton(Icons.Rounded.Close, "Clear search", { vm.clear() }, size = 48)
            }
        }
    }) { contentPadding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 220.dp, start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.loading) item { LoadingRow() }
            vm.failure?.let { f -> item { ErrorState(f, onRetry = vm::submit) } }
            when (vm.scope) {
                SearchScope.SUBREDDITS -> items(vm.subreddits, key = { it.id }) { sub ->
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)).clickable { nav.openSubreddit(sub) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SubredditIcon(sub, 44)
                        Column { Text(sub.displayNamePrefixed, style = MaterialTheme.typography.bodyLarge)
                            if (sub.subscriberCount > 0) Text("${sub.formattedSubscribers} subscribers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                SearchScope.POSTS -> items(vm.posts, key = { it.id }) { PostCard(it, nav, compact = true, modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()) }
            }
            if (!vm.loading && vm.failure == null && vm.query.isNotBlank() && vm.subreddits.isEmpty() && vm.posts.isEmpty()) item { EmptyState(Icons.Rounded.Search, "No results", "Try a different search.") }
        }
    }
}
