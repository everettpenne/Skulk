package com.everettpenne.skulk

import com.everettpenne.skulk.data.media.Comments
import com.everettpenne.skulk.data.model.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.*
import org.junit.Test

class CommentsTest {
    private fun flat(): List<Comment> {
        val root = RedditJson.parseToJsonElement(Fixtures.commentsResponse) as JsonArray
        return Comments.flatten(RedditJson.decodeFromJsonElement<RawListing>(root[1]))
    }

    @Test fun theTreeIsFlattenedInDisplayOrderWithDepths() {
        val c = flat()
        assertEquals(listOf("c_a", "c_b", "c_c", "c_d"), c.map { it.id })
        assertEquals(listOf(0, 1, 2, 0), c.map { it.depth })
    }

    @Test fun eachCommentKnowsItsDescendants() {
        val c = flat().associateBy { it.id }
        assertEquals(listOf("c_b", "c_c"), c.getValue("c_a").childIds); assertEquals(2, c.getValue("c_a").childCount)
        assertEquals(listOf("c_c"), c.getValue("c_b").childIds)
        assertFalse(c.getValue("c_c").hasChildren); assertFalse(c.getValue("c_d").hasChildren)
    }

    @Test fun fieldsAreReadAndTextUnescaped() {
        val a = flat().first()
        assertEquals("Top level & bold", a.body); assertEquals(true, a.likes); assertTrue(a.isSubmitter); assertEquals("t1_c_a", a.fullname)
    }

    @Test fun collapsingHidesEveryDescendant() {
        val c = flat()
        assertEquals(listOf("c_a", "c_d"), Comments.applyCollapse(c, setOf("c_a")).map { it.id })
        assertEquals(listOf("c_a", "c_b", "c_d"), Comments.applyCollapse(c, setOf("c_b")).map { it.id })
        assertEquals(c, Comments.applyCollapse(c, emptySet()))
    }

    @Test fun anImageTokenBecomesMediaAndIsRemovedFromTheText() {
        val d = flat().last()
        assertEquals("look", d.body)
        val m = d.embeddedMedia.single()
        assertEquals("https://preview.redd.it/pic.png?width=216&s=x", m.image.url); assertFalse(m.isGif); assertEquals(216, m.image.width)
    }

    @Test fun giphyAndBareImageLinksAreFound() {
        val media = Comments.embeddedMedia("![gif](giphy|abc123) and https://i.imgur.com/a.gifv and https://example.com/page", emptyMap())
        assertEquals(listOf("https://i.giphy.com/media/abc123/giphy.gif", "https://i.imgur.com/a.gifv"), media.map { it.image.url })
        assertTrue(media.all { it.isGif })
        assertEquals("and  and https://example.com/page", Comments.stripMediaTokens("![gif](giphy|abc123) and https://i.imgur.com/a.gifv and https://example.com/page"))
    }

    @Test fun emotesAreLeftInTheText() {
        assertEquals("hi ![img](emote|t5_x|1)", Comments.stripMediaTokens("hi ![img](emote|t5_x|1)"))
        assertTrue(Comments.embeddedMedia("hi ![img](emote|t5_x|1)", emptyMap()).isEmpty())
    }
}
