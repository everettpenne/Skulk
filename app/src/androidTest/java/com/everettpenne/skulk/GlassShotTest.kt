package com.everettpenne.skulk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.everettpenne.skulk.data.model.*
import com.everettpenne.skulk.ui.Tab
import com.everettpenne.skulk.ui.components.Navigator
import com.everettpenne.skulk.ui.components.PostCard
import com.everettpenne.glass.*
import com.everettpenne.skulk.ui.theme.SkulkTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Dev: draws fixture posts under the glass chrome and saves screenshots so the look can be checked without a Reddit login. */
class GlassShotTest {
    @get:Rule val compose = createComposeRule()

    private val sub = Subreddit("1", "pics", "r/pics", null, 30_000_000, true, "Pictures", 0xFF0079D3.toInt())
    private fun post(i: Int, media: MediaType, self: String = "") = Post("p$i", "t3_p$i", listOf("A very long and interesting title about glass and light number $i", "Short title $i", "What would you do if the sky turned liquid? ($i)")[i % 3],
        "someone$i", sub, 1200 * i + 37, 40 + i, System.currentTimeMillis() / 1000.0 - 3600 * i, "/r/pics/comments/p$i/x/", i % 4 == 0, self.isNotEmpty(), self,
        if (i % 2 == 0) "Discussion" else null, 0xFFFF4500.toInt(), true, false, null, media)
    private val posts = listOf(
        post(1, MediaType.Image(GalleryImage("https://example.invalid/a.jpg", 1600, 900))),
        post(2, MediaType.None, "This is **bold**, this is *italic*, and here is a [link](https://example.com).\n\n- first\n- second"),
        post(3, MediaType.Link(LinkMetadata("https://example.com/long/story/path", "aurelien2022.substack.com", null))),
        post(4, MediaType.None, "Another text post with a few lines of text so the card is tall enough to scroll beneath the bars. ".repeat(3)),
        post(5, MediaType.None, "More"), post(6, MediaType.None, "Even more"),
    )
    private val nav = object : Navigator { override fun openPost(post: Post) {}; override fun openSubreddit(subreddit: Subreddit) {}; override fun openUser(username: String) {} }

    @Test fun shoot() {
        compose.setContent {
            SkulkTheme {
                val page = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassBackdrop provides page, LocalTabBar provides { GlassTabBar(Tab.bar, Tab.Feed, {}, search = Tab.Search) }) {
                    GlassScreen(bars = {
                        GlassTopBar("Home", modifier = Modifier.align(Alignment.TopCenter)) {
                            GlassActionPill {
                                com.everettpenne.skulk.ui.screens.MenuPillIcon(Icons.Rounded.EmojiEvents, "Sort") { close -> listOf("Best", "Hot", "New").forEach { GlassMenuItem(it, close, selected = it == "Best") } }
                                PillIcon(Icons.Rounded.Home, "Feed", {})
                            }
                        }
                    }) { contentPadding ->
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding(), start = 8.dp, end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) { items(posts) { PostCard(it, nav, compact = true) } }
                    }
                }
            }
        }
        compose.waitForIdle()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
        Thread.sleep(2000); device.takeScreenshot(File(dir, "feed.png"))
    }

    @Test fun realScreens() {
        compose.setContent {
            SkulkTheme {
                val page = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                var screen by remember { mutableStateOf(0) }
                val tabs: @Composable () -> Unit = { GlassTabBar(Tab.bar, if (screen == 2) Tab.Search else Tab.Saved, {}, search = Tab.Search) }
                CompositionLocalProvider(LocalGlassBackdrop provides page, LocalTabBar provides tabs) {
                    androidx.compose.foundation.layout.Box(Modifier.testTag("host")) {
                        when (screen) {
                            0 -> com.everettpenne.skulk.ui.screens.SettingsScreen({})
                            1 -> com.everettpenne.skulk.ui.screens.PostsScreen(com.everettpenne.skulk.data.model.PostFeed.Saved, nav)
                            else -> com.everettpenne.skulk.ui.screens.SearchScreen(nav)
                        }
                    }
                }
                LaunchedEffect(Unit) { snapshotFlow { shotStep }.collect { screen = it } }
            }
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
        for ((i, name) in listOf("settings", "saved", "search").withIndex()) {
            shotStep = i; compose.waitForIdle(); Thread.sleep(2500); device.takeScreenshot(File(dir, "$name.png"))
        }
    }
}

private var shotStep by mutableStateOf(0)
