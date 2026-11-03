package com.everettpenne.skulk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.everettpenne.skulk.data.api.ApiResult
import com.everettpenne.skulk.data.model.Subreddit
import com.everettpenne.skulk.graph
import com.everettpenne.skulk.ui.components.SubredditIcon
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassSheet
import com.everettpenne.glass.glassPalette

/** About a subreddit: its numbers and description, and a star to keep it in the feed switcher. Details are fetched when a link only gave a name. */
@Composable
fun SubredditInfoSheet(initial: Subreddit, onDismiss: () -> Unit) {
    val graph = LocalContext.current.graph
    val ink = glassPalette().ink
    var sub by remember { mutableStateOf(initial) }
    val favoriteNames by graph.favorites.names.collectAsState()
    val starred = sub.displayName.lowercase() in favoriteNames
    LaunchedEffect(initial.displayName) {
        if (initial.publicDescription.isEmpty() && initial.subscriberCount == 0 && initial.displayName.isNotEmpty()) {
            (graph.api.fetchSubredditAbout(initial.displayName) as? ApiResult.Ok)?.value?.let { sub = it }
        }
    }
    GlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubredditIcon(sub, 48)
                Column {
                    Text(sub.displayNamePrefixed, style = MaterialTheme.typography.titleLarge, color = ink)
                    if (sub.subscriberCount > 0) Text("${sub.formattedSubscribers} subscribers", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(sub.publicDescription.ifEmpty { "No description available" }, color = ink.copy(alpha = .85f), style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                GlassButton(if (starred) "Remove from favorites" else "Add to favorites", { graph.favorites.toggle(sub) }, prominent = !starred)
            }
        }
    }
}
