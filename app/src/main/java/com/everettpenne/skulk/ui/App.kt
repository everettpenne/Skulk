package com.everettpenne.skulk.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.everettpenne.skulk.data.model.Post
import com.everettpenne.skulk.data.model.PostFeed
import com.everettpenne.skulk.data.model.PostNavigation
import com.everettpenne.skulk.data.model.Subreddit
import com.everettpenne.skulk.graph
import com.everettpenne.media.ImageViewer
import com.everettpenne.media.LocalImageViewer
import com.everettpenne.media.VideoPip
import com.everettpenne.skulk.ui.components.Navigator
import com.everettpenne.skulk.ui.components.LocalToast
import com.everettpenne.skulk.ui.components.ToastOverlay
import com.everettpenne.glass.*
import com.everettpenne.skulk.ui.screens.*
import com.everettpenne.skulk.ui.theme.SkulkTheme
import com.everettpenne.skulk.util.LinkRouter
import com.everettpenne.skulk.util.LinkTarget

/** How long a pushed screen takes to slide in or out, in ms. */
private const val PUSH_MS = 320

/** Where the navigation is told to go. Pure strings, so deep links and taps share them. */
object Routes {
    const val FEED = "feed"; const val SAVED = "saved"; const val SEARCH = "search"; const val SETTINGS = "settings"
    fun subreddit(name: String) = "list/subreddit/${Uri.encode(name)}"
    fun user(name: String) = "list/user/${Uri.encode(name)}"
    fun post(id: String, subreddit: String? = null, comment: String? = null) =
        "post/${Uri.encode(id)}?sub=${Uri.encode(subreddit ?: "")}&comment=${Uri.encode(comment ?: "")}"
}

@Composable
fun SkulkApp(deepLink: Uri?, onDeepLinkHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val graph = context.graph
    val colorScheme by graph.settings.colorScheme.collectAsState()
    SkulkTheme(dark = colorScheme.dark) {
        val updater = com.everettpenne.updater.Updater.get(LocalContext.current, com.everettpenne.updater.UpdateConfig("everettpenne/Skulk", com.everettpenne.skulk.BuildConfig.VERSION_NAME))
        // Debug builds skip the launch check so the emulator tests never meet a dialog; Settings > Check for Updates still works there.
        LaunchedEffect(Unit) { if (!com.everettpenne.skulk.BuildConfig.DEBUG) updater.checkOnLaunch() }
        com.everettpenne.updater.UpdateDialog(updater, "Skulk")
        val navController = rememberNavController()
        var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
        var frontFeed by remember { mutableStateOf<PostFeed>(graph.settings.initialFeed()) }
        var toast by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2600); toast = null } }

        val navigator = remember(navController) {
            object : Navigator {
                override fun openPost(post: Post) { graph.postCache[post.id] = post; graph.readPosts.mark(post.id); navController.navigate(Routes.post(post.id, post.subreddit.displayName)) }
                override fun openSubreddit(subreddit: Subreddit) = navController.navigate(Routes.subreddit(subreddit.displayName))
                override fun openUser(username: String) = navController.navigate(Routes.user(username))
            }
        }
        // Links outside Reddit go to the suite's browser (BrowserHost below), which this handler reaches through openWeb once it is drawn.
        val openWeb = remember { mutableStateOf<(String) -> Unit>({ url -> com.everettpenne.browser.Browsers.openOutside(context, url) }) }
        val uriHandler = remember(navController) { AppUriHandler(navController, showImages = { urls -> viewer = urls to 0 }, openWeb = { openWeb.value(it) }) }
        // A link or a shortcut's own scheme. Asking the caller to clear it afterwards means tapping the same shortcut twice works.
        LaunchedEffect(deepLink) {
            val link = deepLink ?: return@LaunchedEffect
            when {
                link.scheme == "skulk" && link.host == "saved" -> navController.navigate(Routes.SAVED) { launchSingleTop = true }
                link.scheme == "skulk" && link.host == "search" -> navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                link.scheme == "skulk" && link.host == "feed" ->
                    navController.navigate(Routes.FEED) { popUpTo(Routes.FEED) { inclusive = false }; launchSingleTop = true }
                else -> uriHandler.openUri(link.toString())
            }
            onDeepLinkHandled()
        }

        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val tabs = Tab.entries.associateBy { it.route }
        var lastTab by remember { mutableStateOf(Tab.Feed) }
        LaunchedEffect(route) { tabs[route]?.let { lastTab = it } }

        val tabBar: @Composable () -> Unit = {
            GlassTabBar(Tab.bar, selected = lastTab, search = Tab.Search, onSelect = { tab ->
                // Tapping the tab you are already on goes back to the top of its list (and, from the top, refreshes it), as iOS's tab bar does.
                if (tab.route == route) TabReselect.events.tryEmit(tab)
                else navController.navigate(tab.route) { popUpTo(Routes.FEED) { inclusive = false }; launchSingleTop = true }
            })
        }

        val linksInApp by graph.settings.linksInApp.collectAsState()
        com.everettpenne.browser.BrowserHost(inApp = linksInApp) {
        val browser = com.everettpenne.browser.LocalBrowser.current
        SideEffect { openWeb.value = browser }
        CompositionLocalProvider(LocalImageViewer provides { urls, i -> viewer = urls to i }, LocalUriHandler provides uriHandler, LocalTabBar provides tabBar,
            com.everettpenne.updater.LocalUpdater provides updater, LocalToast provides { toast = it }) {
            // The plain page colour is also a glass backdrop, so controls drawn inside a scrolling page still have a surface to bend. It records
            // ONLY the colour, in a sibling of the screens: recording the screens themselves would make any glass control inside them sample a
            // layer that contains it, which sends the renderer into endless recursion and crashes the app.
            val pageBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).background(MaterialTheme.colorScheme.background))
                CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
                    Box(Modifier.fillMaxSize()) {
                    // Tabs cross-fade; a screen pushed from one (a post, a subreddit, Settings) slides in from the right over a page that
                    // drifts left a little and dims, and slides back out the same way, as iOS's navigation does.
                    val tabRoutes = remember { Tab.entries.map { it.route }.toSet() }
                    fun pushed(route: String?) = route != null && route !in tabRoutes
                    NavHost(navController, startDestination = Routes.FEED, modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            if (pushed(targetState.destination.route)) slideInHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(PUSH_MS / 2))
                            else fadeIn()
                        },
                        exitTransition = {
                            if (pushed(targetState.destination.route)) slideOutHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(PUSH_MS), targetAlpha = .55f)
                            else fadeOut()
                        },
                        popEnterTransition = {
                            if (pushed(initialState.destination.route)) slideInHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(PUSH_MS), initialAlpha = .55f)
                            else fadeIn()
                        },
                        popExitTransition = {
                            if (pushed(initialState.destination.route)) slideOutHorizontally(tween(PUSH_MS, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(PUSH_MS / 2, delayMillis = PUSH_MS / 2))
                            else fadeOut()
                        }) {
                            composable(Routes.FEED) {
                                PostsScreen(frontFeed, navigator, onSwitchFeed = { frontFeed = it; graph.settings.rememberFeed(it) },
                                    onSettings = { navController.navigate(Routes.SETTINGS) })
                            }
                            composable(Routes.SAVED) { PostsScreen(PostFeed.Saved, navigator) }
                            composable(Routes.SEARCH) { SearchScreen(navigator) }
                            composable("list/subreddit/{name}", listOf(navArgument("name") { type = NavType.StringType })) { e ->
                                PostsScreen(PostFeed.OfSubreddit(Subreddit.named(e.arguments?.getString("name").orEmpty())), navigator, onBack = { navController.popBackStack() })
                            }
                            composable("list/user/{name}", listOf(navArgument("name") { type = NavType.StringType })) { e ->
                                PostsScreen(PostFeed.OfUser(e.arguments?.getString("name").orEmpty()), navigator, onBack = { navController.popBackStack() })
                            }
                            composable("post/{id}?sub={sub}&comment={comment}", listOf(
                                navArgument("id") { type = NavType.StringType },
                                navArgument("sub") { type = NavType.StringType; defaultValue = "" },
                                navArgument("comment") { type = NavType.StringType; defaultValue = "" })) { e ->
                                val a = e.arguments
                                PostDetailScreen(PostNavigation(a?.getString("id").orEmpty(), a?.getString("sub")?.takeIf { it.isNotEmpty() }, a?.getString("comment")?.takeIf { it.isNotEmpty() }),
                                    navigator, onBack = { navController.popBackStack() })
                            }
                            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
                    }
                    ToastOverlay(toast, Modifier.align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp))
                    }
                    // In picture-in-picture the small window owns the video through the shared Glass player.
                    if (VideoPip.active) VideoPip.player?.let { com.everettpenne.media.VideoSurface(it, Modifier.fillMaxSize()) }
                }
            }
            viewer?.let { (urls, i) -> ImageViewer(urls, i, saveAlbum = "Skulk") { viewer = null } }
        }
        }
    }
}

/** Sends a tapped link where it belongs: Reddit content inside the app, pictures to the viewer, everything else to the browser. */
class AppUriHandler(
    private val nav: NavHostController,
    private val showImages: (List<String>) -> Unit,
    private val openWeb: (String) -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        when (val target = LinkRouter.parse(uri)) {
            is LinkTarget.Post -> nav.navigate(Routes.post(target.id, target.subreddit, target.commentId))
            is LinkTarget.Subreddit -> nav.navigate(Routes.subreddit(target.name))
            is LinkTarget.User -> nav.navigate(Routes.user(target.name))
            is LinkTarget.Image -> showImages(listOf(target.url))
            is LinkTarget.External -> openWeb(target.url)
        }
    }
}
