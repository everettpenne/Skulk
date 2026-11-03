// Media3 marks parts of its API "unstable" (they may change between versions); these are used on purpose and pinned by the version in the build.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.everettpenne.skulk.ui.components

import androidx.compose.material.icons.automirrored.rounded.*
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.everettpenne.skulk.data.model.*
import com.everettpenne.skulk.graph
import com.everettpenne.skulk.ui.theme.Skulk
import com.everettpenne.media.FullscreenVideo
import com.everettpenne.media.LocalImageViewer
import com.everettpenne.media.VideoPip

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, aspectRatio: Float? = null, contentScale: ContentScale = ContentScale.Crop, description: String? = null) {
    val base = if (aspectRatio != null) modifier.aspectRatio(aspectRatio) else modifier.heightIn(min = 120.dp)
    AsyncImage(model = url, contentDescription = description, contentScale = contentScale,
        modifier = base.background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)))
}

/** What a post shows below its title. */
@Composable
fun PostMedia(media: MediaType, nsfwBlur: Boolean, modifier: Modifier = Modifier, repost: @Composable (Post) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val openImages = LocalImageViewer.current
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.fillMaxWidth().clip(shape)) {
        when (media) {
            MediaType.None -> Unit
            is MediaType.Image -> RemoteImage(media.image.url, Modifier.fillMaxWidth().clickable { openImages(listOf(media.image.url), 0) },
                media.image.aspectRatio?.coerceIn(.4f, 3f), if (media.image.aspectRatio != null) ContentScale.Crop else ContentScale.FillWidth)
            is MediaType.Gif -> RemoteImage(media.image.url, Modifier.fillMaxWidth().clickable { openImages(listOf(media.image.url), 0) },
                media.image.aspectRatio?.coerceIn(.4f, 3f), if (media.image.aspectRatio != null) ContentScale.Crop else ContentScale.FillWidth)
            is MediaType.Gallery -> Gallery(media.images)
            is MediaType.Video -> VideoPlayer(media.url, media.width, media.height, media.thumbnailUrl, loops = media.loops)
            is MediaType.YouTube -> Box(Modifier.fillMaxWidth().clickable { uriHandler.openUri("https://www.youtube.com/watch?v=${media.videoId}") }) {
                RemoteImage(media.image.url, Modifier.fillMaxWidth(), media.image.aspectRatio ?: (16f / 9f))
                PlayBadge(Modifier.align(Alignment.Center))
            }
            is MediaType.Link -> LinkCard(media.metadata) { uriHandler.openUri(media.metadata.url) }
            is MediaType.Repost -> Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)).padding(10.dp)) { repost(media.original) }
        }
    }
}

@Composable
private fun PlayBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(56.dp).background(Color.Black.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun Gallery(images: List<GalleryImage>) {
    val openImages = LocalImageViewer.current
    val state = rememberPagerState { images.size }
    val ratio = (images.firstOrNull()?.aspectRatio ?: (4f / 3f)).coerceIn(.6f, 2f)
    Box(Modifier.fillMaxWidth()) {
        HorizontalPager(state, Modifier.fillMaxWidth().aspectRatio(ratio)) { page ->
            RemoteImage(images[page].url, Modifier.fillMaxSize().clickable { openImages(images.map { it.url }, page) }, null, ContentScale.Fit)
        }
        Text("${state.currentPage + 1}/${images.size}", color = Color.White, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = .5f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun LinkCard(link: LinkMetadata, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Link, null, tint = Skulk.link, modifier = Modifier.size(30.dp))
        Text(link.domain, style = MaterialTheme.typography.titleMedium, color = Skulk.link, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (link.thumbnailUrl != null) RemoteImage(link.thumbnailUrl, Modifier.width(112.dp).clip(RoundedCornerShape(10.dp)), 1.5f)
    }
}

/**
 * A video in a post, played with Media3: Reddit's own (HLS), a linked .mp4, or an animation shown as a looping video. It starts muted or not as set
 * and loops. A tap anywhere opens it full screen with sound, the way Photos and the iOS Reddit apps do; the round buttons in the corner mute it in
 * place or open it full screen. The full-screen player is the same player, so it carries on from where it was with no re-buffering.
 */
@Composable
fun VideoPlayer(url: String, width: Int, height: Int, thumbnail: String?, modifier: Modifier = Modifier, loops: Boolean = false) {
    val context = LocalContext.current
    val settings = context.graph.settings
    val autoplay by settings.autoplay.collectAsState()
    val muteOnPlay by settings.muteOnPlay.collectAsState()
    val player = remember(url) { buildPlayer(context, url) }
    var playing by remember { mutableStateOf(false) }
    var started by remember(url) { mutableStateOf(false) }
    var fullscreen by remember(url) { mutableStateOf(false) }
    var muted by remember(url) { mutableStateOf(muteOnPlay || loops) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onRenderedFirstFrame() { started = true }
            override fun onVolumeChanged(volume: Float) { muted = volume == 0f }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player, autoplay, muteOnPlay) { player.volume = if (muteOnPlay || loops) 0f else 1f; player.playWhenReady = autoplay }
    fun openFullscreen() { player.volume = 1f; player.play(); fullscreen = true }
    val ratio = if (width > 0 && height > 0) (width.toFloat() / height).coerceIn(.5f, 2f) else 16f / 9f
    Box(modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black).clickable(onClickLabel = "Play full screen") { openFullscreen() }) {
        if (thumbnail != null && !started) RemoteImage(thumbnail, Modifier.fillMaxSize(), null, ContentScale.Fit)
        // the surface ignores touches; the Box above takes the tap
        AndroidView(factory = { c -> PlayerView(c).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; isClickable = false; isFocusable = false } },
            update = { it.player = if (fullscreen || VideoPip.active) null else player }, onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize())
        if (!playing) PlayBadge(Modifier.align(Alignment.Center))
        if (loops) Text("GIF", color = Color.White, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        Row(Modifier.align(Alignment.BottomEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!loops) VideoButton(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (muted) "Unmute" else "Mute") {
                player.volume = if (player.volume > 0f) 0f else 1f
            }
            VideoButton(Icons.Rounded.Fullscreen, "Full screen") { openFullscreen() }
        }
    }
    if (fullscreen) FullscreenVideo(player) {
        fullscreen = false
        // back in the feed it follows the feed's sound setting again
        player.volume = if (muteOnPlay || loops) 0f else 1f
    }
    // Entering PiP closes the full-screen dialog so the small window (driven by the app shell) can own the video surface.
    LaunchedEffect(VideoPip.active) { if (VideoPip.active) fullscreen = false }
}

/** A round dark button on video: 40dp to touch, readable on any frame. */
@Composable
private fun VideoButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)).clickable(onClickLabel = description, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

private fun buildPlayer(context: Context, url: String): ExoPlayer = ExoPlayer.Builder(context).build().apply {
    setMediaItem(MediaItem.fromUri(url)); repeatMode = Player.REPEAT_MODE_ONE; prepare()
}
