package com.everettpenne.skulk

import com.everettpenne.skulk.data.model.*
import com.everettpenne.skulk.util.*
import org.junit.Assert.*
import org.junit.Test

class FormatAndMarkdownTest {
    @Test fun countsAreCompact() {
        assertEquals("0", 0.compact()); assertEquals("999", 999.compact()); assertEquals("1k", 1000.compact())
        assertEquals("1.2k", 1234.compact()); assertEquals("12.3k", 12345.compact()); assertEquals("3M", 3_000_000.compact())
        assertEquals("1.5M", 1_500_000.compact()); assertEquals("-1.2k", (-1234).compact())
    }

    @Test fun timeAgoUsesTheLargestUnit() {
        val now = 1_000_000.0
        assertEquals("now", timeAgo(now - 30, now)); assertEquals("5m", timeAgo(now - 300, now)); assertEquals("3h", timeAgo(now - 3 * 3600, now))
        assertEquals("2d", timeAgo(now - 2 * 86400 - 5, now))
    }

    @Test fun hexColoursAreStrict() {
        assertEquals(0xFF112233.toInt(), parseHexColor("#112233")); assertEquals(0xFFAABBCC.toInt(), parseHexColor("aabbcc"))
        assertNull(parseHexColor("#123")); assertNull(parseHexColor("zzzzzz")); assertNull(parseHexColor(null)); assertNull(parseHexColor(""))
    }

    @Test fun inlineMarksNestAndKeepPlainText() {
        val s = Markdown.inline("a **bold *and italic*** ~~gone~~ `code` b")
        assertEquals("a bold and italic gone code b", s.joinToString("") { it.text })
        assertTrue(s.first { it.text == "bold " }.bold)
        val both = s.first { it.text == "and italic" }; assertTrue(both.bold && both.italic)
        assertTrue(s.first { it.text == "gone" }.strike); assertTrue(s.first { it.text == "code" }.code)
    }

    @Test fun linksAreRecognisedAndPunctuationStaysOutOfBareLinks() {
        val s = Markdown.inline("see [the site](https://example.com/a) or https://example.org/b.")
        assertEquals("https://example.com/a", s.first { it.text == "the site" }.link)
        assertEquals("https://example.org/b", s.first { it.link == "https://example.org/b" }.text)
        assertEquals(".", s.last().text)
    }

    @Test fun underscoresInsideWordsAreNotItalic() {
        val s = Markdown.inline("snake_case_name and _real_")
        assertEquals("snake_case_name and real", s.joinToString("") { it.text })
        assertTrue(s.first { it.text == "real" }.italic); assertFalse(s.first().italic)
    }

    @Test fun blocksAreSplitByKind() {
        val b = Markdown.blocks("# Title\n\nPara one\nstill one\n\n> quoted\n- a\n- b\n1. first\n---\n```\ncode here\n```\nend")
        assertEquals(listOf("Heading", "Paragraph", "Quote", "ListItem", "ListItem", "ListItem", "Rule", "CodeBlock", "Paragraph"),
            b.map { it::class.simpleName })
        assertEquals("Para one\nstill one", (b[1] as Markdown.Block.Paragraph).spans.joinToString("") { it.text })
        assertEquals("1.", (b[5] as Markdown.Block.ListItem).marker); assertEquals("code here", (b[7] as Markdown.Block.CodeBlock).code)
    }

    @Test fun sortsAndFeedsBehaveLikeLurkers() {
        assertEquals(ListingSort.Top(TopPeriod.WEEK), ListingSort.from("top_week")); assertNull(ListingSort.from("top_decade")); assertNull(ListingSort.from(null))
        assertEquals("top", ListingSort.Top(TopPeriod.ALL).apiPath); assertEquals("all", ListingSort.Top(TopPeriod.ALL).timeParameter)
        assertEquals(ListingSort.Hot, PostFeed.All.defaultSort); assertEquals(ListingSort.Best, PostFeed.Home.defaultSort)
        assertFalse(PostFeed.Popular.supportsBestSort); assertFalse(PostFeed.Saved.canSort)
        assertEquals(PostFeed.Popular, PostFeed.frontPage("popular")); assertNull(PostFeed.frontPage("saved"))
    }
}

class RobustnessTest {
    @Test fun hostileMarkdownIsParsedQuickly() {
        val inputs = listOf("*a ".repeat(30_000), "_x ".repeat(30_000), "**" + "y".repeat(50_000), "[".repeat(20_000) + "](", "`".repeat(40_000), "~~ ".repeat(30_000), "a*\n".repeat(30_000))
        for (input in inputs) {
            val start = System.nanoTime()
            Markdown.blocks(input)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("took ${ms}ms on ${input.take(12)}", ms < 3_000)
        }
    }

    @Test fun emphasisStaysOnItsLine() {
        val spans = Markdown.inline("*not\nclosed*")
        assertEquals("*not\nclosed*", spans.joinToString("") { it.text })
        assertTrue(spans.none { it.italic })
    }

    @Test fun repeatedCommentIdsAreKeptOnce() {
        val dup = """{"kind":"t1","data":{"id":"x","author":"a","body":"hi","depth":0,"replies":""}}"""
        val listing = RedditJson.decodeFromString<Listing<kotlinx.serialization.json.JsonObject>>(Fixtures.listing(dup, dup, dup))
        assertEquals(listOf("x"), com.everettpenne.skulk.data.media.Comments.flatten(listing).map { it.id })
    }
}
