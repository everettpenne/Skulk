package com.everettpenne.skulk

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.everettpenne.skulk.data.api.Failure
import com.everettpenne.skulk.data.model.*
import com.everettpenne.skulk.ui.components.Navigator
import com.everettpenne.glass.GlassScreen
import com.everettpenne.skulk.ui.screens.PostDetailList
import com.everettpenne.skulk.ui.theme.SkulkTheme
import org.junit.Rule
import org.junit.Test

/** The post page with real comment layouts on screen: nested replies, a collapsed thread, media, a failure, no comments. */
class PostDetailLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val sub = Subreddit("1", "pics", "r/pics")
    private val post = Post("p1", "t3_p1", "A post", "op", sub, 10, 3, 0.0, "/r/pics/comments/p1/", false, true, "Body **text**", null, null, false, false, null, MediaType.None)
    private fun comment(id: String, depth: Int, children: List<String> = emptyList(), body: String = "Comment $id with *marks* and [a link](https://example.com)") =
        Comment(id, "user$id", body, System.currentTimeMillis() / 1000.0, 5, 5, depth, null, id == "a", null, null, null, false, null,
            if (id == "c") listOf(CommentMedia(GalleryImage("https://example.invalid/x.png", 100, 50), false)) else emptyList(), children.size, children)
    private val comments = listOf(comment("a", 0, listOf("b", "c")), comment("b", 1, listOf("c")), comment("c", 2), comment("d", 0), comment("e", 0), comment("f", 3))
    private val nav = object : Navigator { override fun openPost(post: Post) {}; override fun openSubreddit(subreddit: Subreddit) {}; override fun openUser(username: String) {} }

    private fun show(list: List<Comment>, collapsed: Set<String> = emptySet(), loading: Boolean = false, failure: Failure? = null) {
        compose.setContent {
            SkulkTheme { GlassScreen(bars = {}) { PostDetailList(post, list, collapsed, loading, failure, nav, rememberLazyListState(), {}, {}) } }
        }
        compose.waitForIdle()
    }

    @Test fun nestedCommentsWithTopLevelSeparatorsLayOut() { show(comments); compose.onNodeWithText("Comments").assertExists() }
    @Test fun aCollapsedThreadLaysOut() { show(comments, collapsed = setOf("a")); compose.onNodeWithText("[2 replies]").assertExists() }
    @Test fun noCommentsAndAFailureLayOut() {
        show(emptyList()); compose.onNodeWithText("No comments yet").assertExists()
    }
    @Test fun aLoadFailureLaysOut() { show(emptyList(), failure = Failure.OFFLINE); compose.onNodeWithText("Try again").assertExists() }
}
