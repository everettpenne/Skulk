package com.everettpenne.skulk

import com.everettpenne.skulk.util.LinkRouter
import com.everettpenne.skulk.util.LinkTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkRouterTest {
    private fun parse(s: String) = LinkRouter.parse(s)

    @Test fun postsAndComments() {
        assertEquals(LinkTarget.Post("abc123", "pics", null), parse("https://www.reddit.com/r/pics/comments/abc123/some_title/"))
        assertEquals(LinkTarget.Post("abc123", "pics", "def456"), parse("https://www.reddit.com/r/pics/comments/abc123/some_title/def456/"))
        assertEquals(LinkTarget.Post("abc123", "pics", "def456"), parse("https://old.reddit.com/r/pics/comments/abc123/comment/def456"))
        assertEquals(LinkTarget.Post("abc123", null, null), parse("https://redd.it/abc123"))
        assertEquals(LinkTarget.Post("abc123", null, null), parse("https://reddit.com/comments/abc123"))
    }

    @Test fun subredditsAndUsers() {
        assertEquals(LinkTarget.Subreddit("kotlin"), parse("https://reddit.com/r/kotlin"))
        assertEquals(LinkTarget.Subreddit("kotlin"), parse("https://m.reddit.com/R/kotlin/hot"))
        assertEquals(LinkTarget.User("spez"), parse("https://www.reddit.com/u/spez"))
        assertEquals(LinkTarget.User("spez"), parse("https://www.reddit.com/user/spez/"))
    }

    @Test fun picturesOpenTheViewerButGifsAndOtherSitesDoNot() {
        assertEquals(LinkTarget.Image("https://i.redd.it/x.jpg"), parse("https://i.redd.it/x.jpg"))
        assertEquals(LinkTarget.Image("https://preview.redd.it/x.png?w=1"), parse("https://preview.redd.it/x.png?w=1"))
        assertEquals(LinkTarget.Image("https://example.com/p.webp"), parse("https://example.com/p.webp"))
        assertEquals(LinkTarget.External("https://i.imgur.com/a.gif"), parse("https://i.imgur.com/a.gif"))
        assertEquals(LinkTarget.External("https://example.com/story"), parse("https://example.com/story"))
    }

    @Test fun nonsenseIsExternal() {
        assertEquals(LinkTarget.External("not a url"), parse("not a url"))
        assertEquals(LinkTarget.External("https://www.reddit.com/wiki/faq"), parse("https://www.reddit.com/wiki/faq"))
        assertEquals(LinkTarget.External("https://notreddit.com/r/pics"), parse("https://notreddit.com/r/pics"))
    }
}
