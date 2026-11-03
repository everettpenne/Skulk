package com.everettpenne.skulk.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.everettpenne.skulk.data.model.*
import com.everettpenne.skulk.graph
import com.everettpenne.glass.GlassDropdownMenu
import com.everettpenne.glass.GlassMenuItem
import com.everettpenne.skulk.ui.theme.Skulk
import com.everettpenne.skulk.util.compact
import com.everettpenne.skulk.util.timeAgo

/** Where a tap on a post's parts goes; the root supplies it. */
interface Navigator {
    fun openPost(post: Post)
    fun openSubreddit(subreddit: Subreddit)
    fun openUser(username: String)
}

/** A post: title, flair, text, media, and the footer with subreddit, counts and save. [compact] is the feed's card; otherwise the post's own page. */
@Composable
fun PostCard(post: Post, nav: Navigator, compact: Boolean, modifier: Modifier = Modifier, nested: Boolean = false) {
    val context = LocalContext.current
    val blurNsfw by context.graph.settings.blurNsfw.collectAsState()
    val localSaved by context.graph.localSaves.posts.collectAsState()
    val toast = LocalToast.current
    // Skulk has no account: the bookmark is the phone's own list.
    val saved = localSaved.any { it.id == post.id }
    var menu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val readIds by context.graph.readPosts.ids.collectAsState()
    // A post already opened has a quieter title in the feed (not on its own page, and not inside a repost).
    val read = compact && !nested && post.id in readIds
    var revealed by remember(post.id) { mutableStateOf(false) }

    fun toggleSave() {
        val now = context.graph.localSaves.toggle(post)
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        toast?.invoke(if (now) "Saved on this device" else "Removed from saved")
    }

    Box(modifier) {
        Column(Modifier.fillMaxWidth()
            .then(if (compact && !nested) Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
            .then(if (compact) Modifier.combinedClickable(onClick = { nav.openPost(post) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }) else Modifier)
            .padding(if (compact && !nested) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(post.title, style = MaterialTheme.typography.titleMedium, color = if (read) MaterialTheme.colorScheme.onSurface.copy(alpha = .55f) else Color.Unspecified, maxLines = if (compact) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
            post.linkFlairText?.let { flair ->
                Text(flair, style = MaterialTheme.typography.labelSmall,
                    color = if (post.flairBackground != null && post.flairTextLight) Color.White else Color.Black,
                    modifier = Modifier.background(post.flairBackground?.let { Color(it) } ?: Color(0xFFD5D7D9), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (!compact) Text("u/${post.author}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { nav.openUser(post.author) })
            if (post.selftext.isNotEmpty()) MarkdownText(post.selftext, compact = compact, selectable = !compact)
            if (post.media != MediaType.None) {
                val hide = blurNsfw && post.isNsfw && !revealed
                val blurAmount by androidx.compose.animation.core.animateDpAsState(if (hide) 28.dp else 0.dp, androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow), label = "nsfw-blur")
                Box {
                    PostMedia(post.media, nsfwBlur = hide, modifier = Modifier.blur(blurAmount),
                        repost = { original -> PostCard(original, nav, compact = true, nested = true) })
                    if (hide) Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null) { revealed = true }, contentAlignment = Alignment.Center) {
                        Text("NSFW · tap to reveal", color = Color.White, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.background(Color.Black.copy(alpha = .6f), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubredditIcon(post.subreddit, 44) { nav.openSubreddit(post.subreddit) }
                Column(Modifier.weight(1f)) {
                    Text(post.subreddit.displayNamePrefixed, style = MaterialTheme.typography.labelMedium, color = post.subreddit.keyColor?.let { Color(it) } ?: Skulk.accent,
                        maxLines = 1, modifier = Modifier.clickable { nav.openSubreddit(post.subreddit) })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Stat(Icons.Rounded.ArrowUpward, post.ups.compact())
                        Stat(Icons.Rounded.ChatBubbleOutline, post.numComments.compact())
                        Stat(Icons.Rounded.Schedule, timeAgo(post.created))
                    }
                }
                CircleButton(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (saved) "Unsave" else "Save", { toggleSave() },
                    tint = if (saved) Skulk.green else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GlassDropdownMenu(menu, { menu = false }) {
            GlassMenuItem("u/${post.author}", { menu = false; nav.openUser(post.author) }, icon = Icons.Rounded.Person)
            GlassMenuItem(if (saved) "Unsave" else "Save on this device", { menu = false; toggleSave() }, icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder)
            GlassMenuItem("Share", {
                menu = false
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, post.redditUrl), null))
            }, icon = Icons.Rounded.Share)
        }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), CircleShape).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
fun SubredditIcon(subreddit: Subreddit, size: Int, onClick: (() -> Unit)? = null) {
    val tint = subreddit.keyColor?.let { Color(it) } ?: Skulk.accent
    Box(Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = .15f)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center) {
        if (subreddit.iconUrl != null) coil3.compose.AsyncImage(subreddit.iconUrl, null, Modifier.fillMaxSize())
        else Text("r/", color = tint, fontWeight = FontWeight.Bold, fontSize = (size * .4f).sp)
    }
}
