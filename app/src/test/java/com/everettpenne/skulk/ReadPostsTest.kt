package com.everettpenne.skulk

import com.everettpenne.skulk.data.ReadPostsRules
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadPostsTest {
    @Test fun anIdIsAddedOnceAndMovedToTheNewestEnd() {
        val one = ReadPostsRules.add(emptySet(), "a", 10)
        val two = ReadPostsRules.add(ReadPostsRules.add(one, "b", 10), "a", 10)
        assertEquals(listOf("b", "a"), two.toList())
    }

    @Test fun theOldestFallOffAtTheCap() {
        var s: Set<String> = emptySet()
        listOf("a", "b", "c", "d").forEach { s = ReadPostsRules.add(s, it, 3) }
        assertEquals(listOf("b", "c", "d"), s.toList())
    }

    @Test fun encodingRoundTripsAndToleratesJunk() {
        val s = linkedSetOf("t3_x", "t3_y")
        assertEquals(s.toList(), ReadPostsRules.decode(ReadPostsRules.encode(s)).toList())
        assertEquals(emptySet<String>(), ReadPostsRules.decode(null))
        assertEquals(listOf("a", "b"), ReadPostsRules.decode("  a   b ").toList())
    }
}
